package com.honjimaku.subrep

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.random.Random

/** The cloud client against a local server that answers as subread.space does. */
class CloudTest {

    private class Seen(val method: String, val query: String?, val cookie: String?, val body: ByteArray)

    private lateinit var server: HttpServer
    private val seen = CopyOnWriteArrayList<Seen>()
    private var answer: (HttpExchange) -> Pair<Int, String> = { 200 to "{}" }
    private var device = ""

    private fun cloud() = Cloud(OkHttpClient(), { device }, { device = it }, "http://127.0.0.1:${server.address.port}")

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes()
            seen += Seen(exchange.requestMethod, exchange.requestURI.rawQuery, exchange.requestHeaders.getFirst("Cookie"), body)
            if (exchange.requestHeaders.getFirst("Cookie") == null) {
                exchange.responseHeaders.add("Set-Cookie", "subplz_device=dev123; HttpOnly; Path=/; SameSite=lax")
            }
            val (code, text) = answer(exchange)
            val bytes = text.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun theFirstAnswerMakesTheAccountAndEachLaterCallSendsIt() {
        answer = { 200 to STATE }
        val cloud = cloud()
        val state = cloud.state()
        assertEquals("dev123", device)
        assertNull(seen[0].cookie)
        cloud.state()
        assertEquals("subplz_device=dev123", seen[1].cookie)
        assertEquals(Cloud.State("acct_1", 7200, true, listOf(Cloud.Pack("captions20", "20 hours of cloud captions", 20, "$4.99"))), state)
    }

    @Test
    fun aPieceGoesAsPcmWithItsLanguage() {
        device = "dev123"
        answer = { 200 to """{"text": "こんにちは", "seconds": 3, "seconds_left": 57}""" }
        val pcm = byteArrayOf(1, 2, 3, 4)
        val result = cloud().transcribe(pcm, "ja")
        assertEquals(Cloud.Answer("こんにちは", 57), result)
        assertEquals("POST", seen[0].method)
        assertEquals("lang=ja", seen[0].query)
        assertArrayEquals(pcm, seen[0].body)
    }

    @Test
    fun noHoursLeftIsOutOfHours() {
        device = "dev123"
        answer = { 402 to """{"detail": "No cloud caption hours left."}""" }
        try {
            cloud().transcribe(ByteArray(2), "ja")
            fail("no exception")
        } catch (_: Cloud.OutOfHours) {
        }
    }

    @Test
    fun aServerErrorLosesOnlyThePiece() {
        device = "dev123"
        answer = { 502 to """{"detail": "speech service answered 500"}""" }
        try {
            cloud().transcribe(ByteArray(2), "ja")
            fail("no exception")
        } catch (e: Cloud.PieceFailed) {
            assertEquals("Cloud: speech service answered 500.", e.message)
        }
    }

    @Test
    fun noServerLosesOnlyThePiece() {
        val cloud = cloud()
        server.stop(0)
        try {
            cloud.transcribe(ByteArray(2), "ja")
            fail("no exception")
        } catch (_: Cloud.PieceFailed) {
        }
    }

    @Test
    fun checkoutGivesThePaymentPage() {
        answer = { 200 to """{"url": "https://checkout.stripe.com/c/pay/cs_1"}""" }
        assertEquals("https://checkout.stripe.com/c/pay/cs_1", cloud().checkout("captions20"))
        assertEquals("""{"pack_id":"captions20"}""", String(seen[0].body))
    }

    @Test
    fun theDeviceComesOnlyFromItsOwnCookie() {
        assertEquals("abc", Cloud.deviceFrom(listOf("other=1; Path=/", "subplz_device=abc; HttpOnly")))
        assertNull(Cloud.deviceFrom(listOf("subplz_device=; Max-Age=0")))
        assertNull(Cloud.deviceFrom(emptyList()))
    }

    @Test
    fun hoursReadAsHoursAndMinutes() {
        assertEquals("0 min", Cloud.hours(59))
        assertEquals("59 min", Cloud.hours(3599))
        assertEquals("20 h 0 min", Cloud.hours(72000))
        assertEquals("1 h 1 min", Cloud.hours(3660))
    }

    /** What goes to the cloud comes back as the same sound, within one step of 16-bit PCM. */
    @Test
    fun pcmOfFloatsReadsBackTheSameSound(): Unit = runBlocking {
        checkAll(Arb.list(Arb.float(-1f, 1f), 0..400)) { list ->
            val samples = list.toFloatArray()
            val bytes = Pcm.toBytes(samples)
            val back = FloatArray(samples.size)
            assertEquals(samples.size, Pcm.toFloat(bytes, bytes.size, back))
            for (i in samples.indices) assertTrue("${samples[i]} -> ${back[i]}", abs(samples[i] - back[i]) <= 1f / 32768f)
        }
    }

    @Test
    fun loudSoundIsClippedNotWrapped() {
        val back = FloatArray(2)
        Pcm.toFloat(Pcm.toBytes(floatArrayOf(1.5f, -1.5f)), 4, back)
        assertEquals(32767f / 32768f, back[0])
        assertEquals(-1f, back[1])
    }

    /** The engine stops asking the cloud once the hours are gone. */
    @Test
    fun theEngineStopsWhenTheHoursAreGone() {
        val calls = CopyOnWriteArrayList<Int>()
        val states = CopyOnWriteArrayList<String>()
        val captions = CopyOnWriteArrayList<String>()
        val recognizer = object : Recognizer {
            override val detectedLanguage = ""
            override fun transcribe(samples: FloatArray, language: String): String {
                calls += samples.size
                if (calls.size > 1) throw Cloud.OutOfHours()
                return "一つ目"
            }
            override fun close() = Unit
        }
        val engine = Engine({ recognizer }, "ja", { captions += it.text }, {}, { states += it })
        engine.start()
        val random = Random(7)
        repeat(3) { feed(engine, speech(1_500, random) + FloatArray(16_000)) }
        engine.stop()
        assertEquals(listOf("一つ目"), captions)
        assertEquals(2, calls.size)
        assertTrue(states.toString(), states.last().startsWith(Engine.STATE_ERROR + "No cloud hours left"))
    }

    private fun speech(ms: Int, random: Random) = FloatArray(16 * ms) { random.nextFloat() * 0.6f - 0.3f }

    /** Feeds [sound] in frames of 100 ms, and waits until the worker took each piece. */
    private fun feed(engine: Engine, sound: FloatArray) {
        val bytes = Pcm.toBytes(sound)
        var at = 0
        while (at < bytes.size) {
            val n = minOf(3_200, bytes.size - at)
            engine.feed(bytes.copyOfRange(at, at + n), n)
            at += n
        }
        Thread.sleep(300)
    }

    private companion object {
        const val STATE = """{"account_id": "acct_1", "seconds_left": 7200, "available": true,
            "packs": [{"id": "captions20", "name": "20 hours of cloud captions", "hours": 20, "price_display": "$4.99"}]}"""
    }
}
