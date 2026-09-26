package com.honjimaku.subrep

import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.random.Random

/** The cloud client against a local server that answers as subread.space does. */
class CloudTest {

    private val site = FakeSite()

    @After
    fun stop() = site.stop()

    @Test
    fun theFirstAnswerMakesTheAccountAndEachLaterCallSendsIt() {
        site.answer = { 200 to STATE }
        val cloud = site.cloud()
        val state = cloud.state()
        assertEquals("dev123", site.device)
        assertNull(site.seen[0].cookie)
        cloud.state()
        assertEquals("subplz_device=dev123", site.seen[1].cookie)
        assertEquals(Cloud.State("acct_1", 7200, true, listOf(Cloud.Pack("captions20", "20 hours of cloud captions", 20, "$4.99"))), state)
    }

    @Test
    fun aPieceGoesAsPcmWithItsLanguage() {
        site.device = "dev123"
        site.answer = { 200 to """{"text": "こんにちは", "seconds": 3, "seconds_left": 57}""" }
        val pcm = byteArrayOf(1, 2, 3, 4)
        val result = site.cloud().transcribe(pcm, "ja")
        assertEquals(Cloud.Answer("こんにちは", 57), result)
        assertEquals("POST", site.seen[0].method)
        assertEquals("lang=ja", site.seen[0].query)
        assertArrayEquals(pcm, site.seen[0].body)
    }

    @Test
    fun noHoursLeftIsOutOfHours() {
        site.device = "dev123"
        site.answer = { 402 to """{"detail": "No cloud caption hours left."}""" }
        try {
            site.cloud().transcribe(ByteArray(2), "ja")
            fail("no exception")
        } catch (_: Cloud.OutOfHours) {
        }
    }

    @Test
    fun aServerErrorLosesOnlyThePiece() {
        site.device = "dev123"
        site.answer = { 502 to """{"detail": "speech service answered 500"}""" }
        try {
            site.cloud().transcribe(ByteArray(2), "ja")
            fail("no exception")
        } catch (e: Cloud.PieceFailed) {
            assertEquals("Cloud: speech service answered 500.", e.message)
        }
    }

    @Test
    fun noServerLosesOnlyThePiece() {
        val cloud = site.cloud()
        site.stop()
        try {
            cloud.transcribe(ByteArray(2), "ja")
            fail("no exception")
        } catch (_: Cloud.PieceFailed) {
        }
    }

    @Test
    fun aPlayPurchaseGoesWithItsTokenAndGivesTheHoursLeft() {
        site.device = "dev123"
        site.answer = { 200 to """{"seconds_left": 72000}""" }
        assertEquals(72000L, site.cloud().redeem("captions20", "tok.A-_1"))
        val seen = site.seen.single()
        assertEquals("POST", seen.method)
        assertEquals("/api/captions/play-purchase", seen.path)
        assertEquals("subplz_device=dev123", seen.cookie)
        val body = JSONObject(String(seen.body))
        assertEquals(setOf("product_id", "purchase_token"), body.keys().asSequence().toSet())
        assertEquals("captions20", body.getString("product_id"))
        assertEquals("tok.A-_1", body.getString("purchase_token"))
    }

    @Test
    fun aRefusedPlayPurchaseGivesTheReasonOfTheSite() {
        site.device = "dev123"
        site.answer = { 403 to """{"detail": "This purchase belongs to another account."}""" }
        try {
            site.cloud().redeem("captions20", "tok")
            fail("no exception")
        } catch (e: IOException) {
            assertEquals("This purchase belongs to another account.", e.message)
        }
    }

    @Test
    fun theStateSaysWhetherGooglePlayCanSell() {
        val state = Cloud.parseState(
            """{"account_id": "acct_1", "seconds_left": 0, "available": false, "play_available": true, "packs": []}""",
        )
        assertFalse(state.available)
        assertTrue(state.playAvailable)
        // A server from before Google Play sends no play_available.
        assertFalse(Cloud.parseState(STATE).playAvailable)
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
