package com.honjimaku.subrep

/** Turns one piece of speech into text: Whisper on the phone, or [CloudRecognizer]. */
interface Recognizer : AutoCloseable {

    /** The language found in the last piece, "ja", "ru", ..., or "" when not known. */
    val detectedLanguage: String

    /** The text of [samples]: 16 kHz mono, from -1 to 1. [language] is a code, or "auto". */
    fun transcribe(samples: FloatArray, language: String): String
}

/** Recognition on subread.space. Each piece costs its length in seconds from the hours of the account. */
class CloudRecognizer(private val cloud: Cloud, private val onSecondsLeft: (Long) -> Unit) : Recognizer {

    override val detectedLanguage: String = ""

    override fun transcribe(samples: FloatArray, language: String): String {
        val answer = cloud.transcribe(Pcm.toBytes(samples), language)
        onSecondsLeft(answer.secondsLeft)
        return answer.text
    }

    override fun close() = Unit
}
