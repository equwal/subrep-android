package com.honjimaku.subrep

import java.io.File

/** JNI surface. See src/main/cpp/whisper_jni.c. */
internal object WhisperLib {
    external fun initContext(modelPath: String): Long
    external fun freeContext(ptr: Long)
    external fun transcribe(ptr: Long, samples: FloatArray, threads: Int, language: String, audioCtx: Int): Int
    external fun segmentCount(ptr: Long): Int
    external fun segmentText(ptr: Long, index: Int): ByteArray
    external fun detectedLanguage(ptr: Long): String
    external fun systemInfo(): String
}

class WhisperUnavailable(message: String) : Exception(message)

/** A loaded speech model. Not thread-safe: one transcription at a time. */
class Whisper private constructor(private var ptr: Long) : Recognizer {

    /** The language that Whisper found in the last piece: "ja", "ru", ... */
    override val detectedLanguage: String get() = WhisperLib.detectedLanguage(ptr)

    /**
     * The text of one piece of sound: [samples] at 16 kHz mono, from -1 to 1. [language] is a
     * code such as "ja", or "auto".
     */
    override fun transcribe(samples: FloatArray, language: String): String {
        val rc = WhisperLib.transcribe(ptr, samples, threads, language, audioContext(samples.size))
        if (rc != 0) throw WhisperUnavailable("The speech model failed on this sound (code $rc).")
        return (0 until WhisperLib.segmentCount(ptr)).joinToString(" ") {
            // Lenient decode: a small model can end a segment inside a character.
            String(WhisperLib.segmentText(ptr, it), Charsets.UTF_8).replace("�", "").trim()
        }.trim()
    }

    override fun close() {
        if (ptr != 0L) WhisperLib.freeContext(ptr)
        ptr = 0
    }

    companion object {

        @Volatile private var loaded = false

        val threads: Int by lazy { fastCores() }

        fun open(model: File): Whisper {
            requireCpu()
            if (!loaded) {
                System.loadLibrary("subrep_whisper")
                loaded = true
            }
            val ptr = WhisperLib.initContext(model.absolutePath)
            if (ptr == 0L) throw WhisperUnavailable("The speech model could not be loaded.")
            return Whisper(ptr)
        }

        /**
         * The part of the encoder window to compute for a piece of [samples]: 50 units for each
         * second of sound, plus a margin. The whole window is 1500 units for 30 s. A piece of
         * 5 s then costs a fifth of the encoder, which is most of the time on a phone.
         */
        fun audioContext(samples: Int): Int = (samples / 16_000.0 * 50 + 100).toInt().coerceIn(384, 1500)

        /**
         * The native library is built for ARMv8.2 with half-precision and dot-product
         * instructions. Loading it without them is a crash with no message, so look first.
         */
        private fun requireCpu() {
            val features = runCatching {
                File("/proc/cpuinfo").readLines()
                    .firstOrNull { it.startsWith("Features") }.orEmpty()
                    .substringAfter(':').trim().split(' ').toSet()
            }.getOrDefault(emptySet())
            if (features.isEmpty()) return
            val missing = listOf("asimdhp", "asimddp").filter { it !in features }
            if (missing.isNotEmpty()) {
                throw WhisperUnavailable("The processor of this phone is too old for speech recognition on the phone (ARMv8.2 is needed).")
            }
        }

        /**
         * Threads to use: the fast cores only. Whisper waits for its slowest thread at each
         * layer, so a little core makes it slower, not faster. "Fast" is a core clocked above
         * the slowest cluster.
         */
        private fun fastCores(): Int {
            val max = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { cpu ->
                runCatching {
                    File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
                }.getOrNull()
            }
            if (max.isEmpty()) return 4
            val slowest = max.min()
            val fast = max.count { it > slowest }
            return if (fast == 0) (max.size / 2).coerceIn(2, 4) else fast.coerceIn(2, 6)
        }
    }
}
