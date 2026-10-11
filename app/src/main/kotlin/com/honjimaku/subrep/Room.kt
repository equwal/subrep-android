package com.honjimaku.subrep

import java.security.SecureRandom
import java.util.Base64

/**
 * The share room of this device on the relay, the same as the desktop app makes it.
 *
 * The room id is a capability: whoever has the link reads the feed. The secret lets only this
 * device publish into the room. Both are made once and kept, so the link stays the same across
 * restarts. The relay refuses a secret shorter than 16 characters.
 */
object Room {

    const val RELAY = "wss://honjimaku.com/subrep"

    /** [value] if it is a ws:// or wss:// address with a host, else the default [RELAY]. A self-hosted relay goes here. */
    fun relayUrl(value: String): String = withHost(value, "ws", "wss") ?: RELAY

    /** [value] without spaces and a final "/", if its scheme is one of [schemes] and it has a host; else null. */
    fun withHost(value: String, vararg schemes: String): String? {
        val url = value.trim().trimEnd('/')
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        return url.takeIf { uri.scheme in schemes && !uri.host.isNullOrEmpty() }
    }

    private val random = SecureRandom()

    /** A room id: 12 characters of the URL alphabet, as the desktop app makes them. */
    fun newId(): String = token(9)

    /** A publisher secret: 32 characters. */
    fun newSecret(): String = token(24)

    /** The link that the viewers open, for the relay [relay] and the room [room]. */
    fun watchUrl(relay: String, room: String): String {
        val base = relay.trimEnd('/')
        val http = when {
            base.startsWith("wss://") -> "https://" + base.removePrefix("wss://")
            base.startsWith("ws://") -> "http://" + base.removePrefix("ws://")
            else -> base
        }
        return "$http/w/$room"
    }

    private fun token(bytes: Int): String {
        val raw = ByteArray(bytes).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }
}
