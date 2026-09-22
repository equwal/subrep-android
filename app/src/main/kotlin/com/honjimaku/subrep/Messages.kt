package com.honjimaku.subrep

import org.json.JSONObject

/** A caption: `final` when the sentence is done, `partial` while it goes on, or `clear`. */
data class Caption(val type: String, val text: String)

/**
 * The messages of the relay protocol, the same as the desktop app sends them: a hello with
 * the room secret, then the captions, then a status when something about the stream changes.
 */
object Messages {

    fun relayHello(secret: String, lang: String, title: String): String =
        JSONObject().put("secret", secret).put("lang", lang).put("title", title).toString()

    /** A caption as the viewer page reads it. */
    fun caption(caption: Caption, nowSeconds: Double = System.currentTimeMillis() / 1000.0): String =
        JSONObject().put("type", caption.type).put("text", caption.text).put("tr", "").put("ts", nowSeconds).toString()

    /** The stream is [live] in [lang], named [title]: sent when the language is found. */
    fun status(live: Boolean, lang: String, title: String): String =
        JSONObject().put("type", "status").put("live", live).put("lang", lang).put("title", title).toString()

    /** The reason in an error message of the relay; null for another message. */
    fun error(json: String): String? {
        val message = parse(json) ?: return null
        if (message.optString("type") != "error") return null
        return message.optString("error").ifEmpty { "error" }
    }

    /** True for the ready message that the relay sends after the hello. */
    fun isReady(json: String): Boolean = parse(json)?.optString("type") == "ready"

    private fun parse(json: String): JSONObject? = runCatching { JSONObject(json) }.getOrNull()
}
