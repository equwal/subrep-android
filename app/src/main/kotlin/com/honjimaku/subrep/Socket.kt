package com.honjimaku.subrep

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One websocket that comes back on its own after a loss. The first message after each connect
 * is the hello of the protocol, so the other side knows what follows.
 */
class Socket(
    private val client: OkHttpClient,
    private val url: String,
    private val hello: () -> String,
    private val onText: (String) -> Unit,
    /** True when the socket is open, false when it was lost. Not called for a failed connect. */
    private val onState: (Boolean) -> Unit,
) : WebSocketListener() {

    private val timer = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var wanted = false
    @Volatile var open = false
        private set
    private var backoffMs = 1000L

    fun start() {
        wanted = true
        connect()
    }

    fun stop() {
        wanted = false
        open = false
        timer.shutdownNow()
        socket?.close(1000, "stop")
        socket = null
    }

    fun send(text: String): Boolean = open && socket?.send(text) == true

    /**
     * Sends the first [count] bytes of [bytes]. Dropped when the socket is not open, or when
     * a slow connection holds more than a few seconds of sound: late captions help nobody.
     */
    fun send(bytes: ByteArray, count: Int): Boolean {
        val s = socket ?: return false
        if (!open || s.queueSize() > QUEUE_LIMIT) return false
        return s.send(bytes.toByteString(0, count))
    }

    private fun connect() {
        if (!wanted) return
        socket = client.newWebSocket(Request.Builder().url(url).build(), this)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        backoffMs = 1000
        open = true
        webSocket.send(hello())
        onState(true)
    }

    override fun onMessage(webSocket: WebSocket, text: String) = onText(text)

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = lost()

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = lost()

    private fun lost() {
        val was = open
        open = false
        if (was) onState(false)
        if (!wanted) return
        val wait = backoffMs
        backoffMs = minOf(backoffMs * 2, 30_000)
        runCatching { timer.schedule({ connect() }, wait, TimeUnit.MILLISECONDS) }
    }

    private companion object {
        const val QUEUE_LIMIT = 256 * 1024L
    }
}
