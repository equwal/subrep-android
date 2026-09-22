package com.honjimaku.subrep

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Cloud captions on subread.space. The account is the device cookie of the site, kept in
 * [Store.device]: the first answer of the server makes it. Hours bought with [checkout] go to
 * that account.
 *
 * The device token is read and written through two functions, so a test needs no [Store].
 */
class Cloud(
    private val http: OkHttpClient,
    private val readDevice: () -> String,
    private val writeDevice: (String) -> Unit,
    private val base: String = BASE,
) {

    constructor(http: OkHttpClient, store: Store) : this(http, { store.device }, { store.device = it })

    /** The piece is lost, but the next can work: the network, or the speech service. */
    class PieceFailed(message: String) : Exception(message)

    /** The account has no hours for this piece. */
    class OutOfHours : Exception("No cloud hours left. Buy hours, or choose the phone.")

    data class Pack(val id: String, val name: String, val hours: Int, val price: String)

    data class State(val accountId: String, val secondsLeft: Long, val available: Boolean, val packs: List<Pack>)

    data class Answer(val text: String, val secondsLeft: Long)

    /** The balance and the packs for sale. Throws [IOException] without the network. */
    fun state(): State = call(Request.Builder().url("$base/api/captions").build()).use { response ->
        if (!response.isSuccessful) throw IOException("subread.space answered ${response.code}")
        parseState(response.body!!.string())
    }

    /** The address of the payment page for [packId]. It opens in the browser. */
    fun checkout(packId: String): String {
        val body = JSONObject().put("pack_id", packId).toString().toRequestBody(JSON)
        return call(Request.Builder().url("$base/api/captions/checkout").post(body).build()).use { response ->
            val text = response.body!!.string()
            if (!response.isSuccessful) throw IOException(detail(text) ?: "subread.space answered ${response.code}")
            JSONObject(text).getString("url")
        }
    }

    /** The text of [pcm], 16 kHz mono 16-bit little-endian PCM. */
    fun transcribe(pcm: ByteArray, language: String): Answer {
        val request = Request.Builder()
            .url("$base/api/captions/transcribe".toHttpUrl().newBuilder().addQueryParameter("lang", language.ifEmpty { "auto" }).build())
            .post(pcm.toRequestBody(PCM))
            .build()
        val response = try {
            call(request)
        } catch (e: IOException) {
            throw PieceFailed("Cloud: no connection (${e.message}).")
        }
        return response.use {
            val text = it.body!!.string()
            when (it.code) {
                200 -> JSONObject(text).let { json -> Answer(json.getString("text"), json.getLong("seconds_left")) }
                402 -> throw OutOfHours()
                else -> throw PieceFailed("Cloud: ${detail(text) ?: "the server answered ${it.code}"}.")
            }
        }
    }

    private fun call(request: Request): Response {
        val device = readDevice()
        val response = http.newCall(
            if (device.isEmpty()) request else request.newBuilder().header("Cookie", "$COOKIE=$device").build(),
        ).execute()
        deviceFrom(response.headers("Set-Cookie"))?.let { if (it != device) writeDevice(it) }
        return response
    }

    companion object {
        const val BASE = "https://subread.space"
        const val COOKIE = "subplz_device"
        private val JSON = "application/json".toMediaType()
        private val PCM = "application/octet-stream".toMediaType()

        /** The device token in the Set-Cookie headers of an answer, or null. */
        fun deviceFrom(setCookies: List<String>): String? = setCookies.firstNotNullOfOrNull { header ->
            header.substringBefore(';').trim().takeIf { it.startsWith("$COOKIE=") }
                ?.substringAfter('=')?.trim('"')?.takeIf { it.isNotEmpty() }
        }

        fun parseState(text: String): State {
            val json = JSONObject(text)
            val packs = json.getJSONArray("packs")
            return State(
                accountId = json.optString("account_id"),
                secondsLeft = json.getLong("seconds_left"),
                available = json.getBoolean("available"),
                packs = (0 until packs.length()).map { i ->
                    packs.getJSONObject(i).run { Pack(getString("id"), getString("name"), getInt("hours"), getString("price_display")) }
                },
            )
        }

        /** The "detail" of a FastAPI error answer, or null. */
        private fun detail(text: String): String? = runCatching { JSONObject(text).getString("detail") }.getOrNull()

        /** "12 h 5 min" for [seconds]. */
        fun hours(seconds: Long): String {
            val minutes = seconds / 60
            return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
        }
    }
}
