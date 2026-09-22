package com.honjimaku.subrep

import org.json.JSONObject

/** A caption from the engine: `partial` while the sentence goes on, `final` when it is done, or `clear`. */
data class Caption(val type: String, val text: String)

/**
 * The messages of the two protocols.
 *
 * The engine (`subrep serve`) takes a hello, then frames of 16-bit PCM, and answers with
 * captions as JSON text. The relay takes a hello with the room secret, then the captions as
 * they came from the engine.
 */
object Messages {

    val CAPTIONS = setOf("partial", "final", "clear")

    fun engineHello(sampleRate: Int, lang: String, source: String): String =
        JSONObject().put("type", "hello").put("sampleRate", sampleRate).put("lang", lang).put("source", source).toString()

    fun relayHello(secret: String, lang: String, title: String): String =
        JSONObject().put("secret", secret).put("lang", lang).put("title", title).toString()

    /** The caption in a message of the engine; null for a message of another type or junk. */
    fun caption(json: String): Caption? {
        val message = parse(json) ?: return null
        val type = message.optString("type")
        return if (type in CAPTIONS) Caption(type, message.optString("text")) else null
    }

    /** The reason in an error message, from the engine or the relay; null for another message. */
    fun error(json: String): String? {
        val message = parse(json) ?: return null
        if (message.optString("type") != "error") return null
        return message.optString("error").ifEmpty { "error" }
    }

    /** True for the ready message that the engine and the relay send after the hello. */
    fun isReady(json: String): Boolean = parse(json)?.optString("type") == "ready"

    private fun parse(json: String): JSONObject? = runCatching { JSONObject(json) }.getOrNull()
}
