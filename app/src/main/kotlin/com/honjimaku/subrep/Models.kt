package com.honjimaku.subrep

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * The speech models. Each is a whisper.cpp file from the ggerganov/whisper.cpp repository on
 * Hugging Face, in 8-bit, which is what a phone runs fastest.
 */
enum class Model(val id: String, val label: String, val about: String) {
    TINY("tiny-q8_0", "tiny", "43 MB. The fastest. Rough words."),
    BASE("base-q8_0", "base", "82 MB. Keeps up on most phones. Fair words."),
    SMALL("small-q8_0", "small", "264 MB. Good words. Slow on a phone: the captions come late.");

    val fileName: String get() = "ggml-$id.bin"
    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    fun file(context: Context): File = File(File(context.filesDir, "models"), fileName)

    fun downloaded(context: Context): Boolean = file(context).isFile

    companion object {
        fun byId(id: String): Model = entries.firstOrNull { it.id == id } ?: BASE
    }
}

/** Downloads a model to its file. Runs on the thread of the caller. */
class Downloader(private val http: OkHttpClient) {

    /**
     * Downloads [model] into its file under [context]. [onProgress] gets the bytes so far and
     * the total (-1 when unknown). Returns null when done, else the reason it failed.
     */
    fun download(context: Context, model: Model, onProgress: (Long, Long) -> Unit): String? {
        val target = model.file(context)
        target.parentFile?.mkdirs()
        // The whole file goes to a temporary name first: a half file must never pass as a model.
        val temp = File(target.path + ".part")
        try {
            http.newCall(Request.Builder().url(model.url).build()).execute().use { response ->
                if (!response.isSuccessful) return "HTTP ${response.code}"
                val body = response.body ?: return "empty answer"
                val total = body.contentLength()
                var done = 0L
                var lastReport = 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (done - lastReport >= 1024 * 1024) {
                                lastReport = done
                                onProgress(done, total)
                            }
                        }
                    }
                }
                if (total > 0 && done != total) return "the download stopped at $done of $total bytes"
                if (!temp.renameTo(target)) return "cannot write ${target.name}"
                onProgress(done, total)
                return null
            }
        } catch (e: Exception) {
            return "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            temp.delete()
        }
    }
}
