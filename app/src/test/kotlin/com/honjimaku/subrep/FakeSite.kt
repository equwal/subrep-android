package com.honjimaku.subrep

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A local server that answers as subread.space does, and the device cookie that the phone keeps.
 * It keeps each request that it saw. The first request without a cookie gets the device dev123.
 */
class FakeSite {

    class Seen(val method: String, val path: String, val query: String?, val cookie: String?, val body: ByteArray)

    val seen = CopyOnWriteArrayList<Seen>()

    /** The status and the body of the answer to each request. The server thread reads it. */
    @Volatile var answer: (HttpExchange) -> Pair<Int, String> = { 200 to "{}" }

    /** The device cookie on the phone. */
    var device = ""

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes()
            val cookie = exchange.requestHeaders.getFirst("Cookie")
            seen += Seen(exchange.requestMethod, exchange.requestURI.rawPath, exchange.requestURI.rawQuery, cookie, body)
            if (cookie == null) {
                exchange.responseHeaders.add("Set-Cookie", "subplz_device=dev123; HttpOnly; Path=/; SameSite=lax")
            }
            val (code, text) = answer(exchange)
            val bytes = text.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    /** A client of this server with the device cookie of [device]. */
    fun cloud() = Cloud(OkHttpClient(), { device }, { device = it }, "http://127.0.0.1:${server.address.port}")

    fun stop() = server.stop(0)
}
