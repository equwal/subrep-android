// The narrowest bridge to whisper.cpp: load a model, transcribe one piece of 16 kHz mono float
// PCM, read the text back. One transcription runs at a time (LocalEngine has one worker).

#include <jni.h>
#include <string.h>
#include <android/log.h>

#include "whisper.h"

#define TAG "subrep-whisper"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

#define JNI_FN(name) Java_com_honjimaku_subrep_WhisperLib_##name

JNIEXPORT jlong JNICALL
JNI_FN(initContext)(JNIEnv *env, jobject thiz, jstring model_path) {
    (void) thiz;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    if (ctx == NULL) LOGW("could not load model %s", path);
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
JNI_FN(freeContext)(JNIEnv *env, jobject thiz, jlong ptr) {
    (void) env; (void) thiz;
    if (ptr != 0) whisper_free((struct whisper_context *) ptr);
}

// Returns 0 on success, else whisper's own error code.
//
// audio_ctx: how much of the 30 s encoder window to compute, in units of 20 ms. A piece of a
// few seconds does not need the whole window, and the encoder is most of the time on a phone.
JNIEXPORT jint JNICALL
JNI_FN(transcribe)(JNIEnv *env, jobject thiz, jlong ptr, jfloatArray samples,
                   jint n_threads, jstring language, jint audio_ctx) {
    (void) thiz;
    struct whisper_context *ctx = (struct whisper_context *) ptr;
    if (ctx == NULL) return -2;

    const jsize n = (*env)->GetArrayLength(env, samples);
    jfloat *pcm = (*env)->GetFloatArrayElements(env, samples, NULL);
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);

    struct whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_realtime   = false;
    p.print_progress   = false;
    p.print_timestamps = false;
    p.print_special    = false;
    p.translate        = false;
    p.language         = lang;          // "auto" detects the language of the piece
    p.n_threads        = n_threads;
    // Each piece stands alone. Text carried over as a prompt is how one hallucinated line
    // becomes a page of the same line.
    p.no_context       = true;
    p.single_segment   = true;
    p.suppress_blank   = true;
    p.suppress_nst     = true;
    p.audio_ctx        = audio_ctx;
    // Greedy, no second try at a higher temperature: a live caption must not wait for it.
    p.temperature_inc  = 0.0f;
    // Speech is at most about 8 tokens a second. A cap stops the loop that Whisper falls into
    // on singing or noise ("o-o-o-o-o...") after a moment instead of a page later.
    p.max_tokens       = (int) (n / 16000.0 * 8) + 24;

    int rc = whisper_full(ctx, p, pcm, n);

    (*env)->ReleaseStringUTFChars(env, language, lang);
    (*env)->ReleaseFloatArrayElements(env, samples, pcm, JNI_ABORT);

    if (rc != 0) LOGW("whisper_full failed: %d", rc);
    return rc;
}

JNIEXPORT jint JNICALL
JNI_FN(segmentCount)(JNIEnv *env, jobject thiz, jlong ptr) {
    (void) env; (void) thiz;
    return whisper_full_n_segments((struct whisper_context *) ptr);
}

// Text as raw UTF-8 bytes. NewStringUTF wants *modified* UTF-8 and mangles or aborts on
// anything outside the BMP, and a small model can end a segment inside a multi-byte
// character. Kotlin decodes leniently.
JNIEXPORT jbyteArray JNICALL
JNI_FN(segmentText)(JNIEnv *env, jobject thiz, jlong ptr, jint index) {
    (void) thiz;
    const char *text = whisper_full_get_segment_text((struct whisper_context *) ptr, index);
    if (text == NULL) text = "";
    const jsize len = (jsize) strlen(text);
    jbyteArray out = (*env)->NewByteArray(env, len);
    if (out != NULL) (*env)->SetByteArrayRegion(env, out, 0, len, (const jbyte *) text);
    return out;
}

JNIEXPORT jstring JNICALL
JNI_FN(detectedLanguage)(JNIEnv *env, jobject thiz, jlong ptr) {
    (void) thiz;
    const int id = whisper_full_lang_id((struct whisper_context *) ptr);
    const char *code = id >= 0 ? whisper_lang_str(id) : NULL;
    return (*env)->NewStringUTF(env, code != NULL ? code : "");
}

JNIEXPORT jstring JNICALL
JNI_FN(systemInfo)(JNIEnv *env, jobject thiz) {
    (void) thiz;
    return (*env)->NewStringUTF(env, whisper_print_system_info());
}
