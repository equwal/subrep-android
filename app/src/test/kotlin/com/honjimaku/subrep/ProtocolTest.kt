package com.honjimaku.subrep

import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomTest {

    /** What the relay accepts as a room id. */
    private val roomId = Regex("^[a-zA-Z0-9_-]{6,64}$")

    @Test
    fun aRoomIdIsOneTheRelayAcceptsAndTwoAreNotTheSame() {
        val ids = List(200) { Room.newId() }
        ids.forEach { assertTrue(it, roomId.matches(it)) }
        ids.forEach { assertEquals(12, it.length) }
        assertEquals(200, ids.toSet().size)
    }

    @Test
    fun aSecretIsLongEnoughForTheRelay() {
        repeat(50) { assertTrue(Room.newSecret().length >= 16) }
    }

    @Test
    fun theWatchLinkIsTheHttpsPageOfTheRoom() {
        assertEquals("https://honjimaku.com/subrep/w/abc123def456", Room.watchUrl("wss://honjimaku.com/subrep", "abc123def456"))
        assertEquals("https://honjimaku.com/subrep/w/abc123def456", Room.watchUrl("wss://honjimaku.com/subrep/", "abc123def456"))
        assertEquals("http://127.0.0.1:8812/w/r", Room.watchUrl("ws://127.0.0.1:8812", "r"))
    }
}

class MessagesTest {

    private val texts = Arb.string(0..60)
    private val types = Arb.element("partial", "final", "clear")

    /** A caption reaches the viewer page as the desktop app sends it: type, text, tr, ts. */
    @Test
    fun aCaptionHasWhatTheViewerPageReads(): Unit = runBlocking {
        checkAll(types, texts) { type, text ->
            val json = JSONObject(Messages.caption(Caption(type, text), nowSeconds = 12.5))
            assertEquals(type, json.getString("type"))
            assertEquals(text, json.getString("text"))
            assertEquals("", json.getString("tr"))
            assertEquals(12.5, json.getDouble("ts"), 0.0)
        }
    }

    @Test
    fun anErrorGivesItsReason() {
        assertEquals("wrong room secret", Messages.error("""{"type":"error","error":"wrong room secret"}"""))
        assertEquals("error", Messages.error("""{"type":"error"}"""))
        assertNull(Messages.error("""{"type":"ready","room":"x"}"""))
        assertNull(Messages.error("junk"))
    }

    @Test
    fun theHelloAndTheStatusHaveWhatTheRelayReads(): Unit = runBlocking {
        checkAll(texts, texts, texts) { secret, lang, title ->
            val hello = JSONObject(Messages.relayHello(secret, lang, title))
            assertEquals(secret, hello.getString("secret"))
            assertEquals(lang, hello.getString("lang"))
            assertEquals(title, hello.getString("title"))
            val status = JSONObject(Messages.status(true, lang, title))
            assertEquals("status", status.getString("type"))
            assertTrue(status.getBoolean("live"))
            assertEquals(lang, status.getString("lang"))
        }
        assertTrue(Messages.isReady("""{"type":"ready","room":"x"}"""))
    }
}

class PcmTest {

    private fun bytesOf(samples: List<Int>): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /** The peak of a frame is the largest sample size in it, with the sign taken away. */
    @Test
    fun thePeakIsTheLargestSampleSize(): Unit = runBlocking {
        checkAll(Arb.list(Arb.int(-32768..32767), 0..400)) { samples ->
            val bytes = bytesOf(samples)
            val expected = samples.maxOfOrNull { if (it < 0) -it else it } ?: 0
            assertEquals(expected, Pcm.peak(bytes, bytes.size))
        }
    }

    /** Each sample comes out as its value over 32768, in the same order. */
    @Test
    fun theFloatsAreTheSamplesOver32768(): Unit = runBlocking {
        checkAll(Arb.list(Arb.int(-32768..32767), 0..400)) { samples ->
            val bytes = bytesOf(samples)
            val out = FloatArray(samples.size)
            assertEquals(samples.size, Pcm.toFloat(bytes, bytes.size, out))
            samples.forEachIndexed { i, s -> assertEquals(s / 32768f, out[i], 0f) }
        }
    }

    @Test
    fun onlyTheFirstCountBytesCount() {
        val bytes = byteArrayOf(0, 0, 0, 0x7F.toByte())
        assertEquals(0, Pcm.peak(bytes, 2))
        assertEquals(32512, Pcm.peak(bytes, 4))
        assertEquals(0, Pcm.peak(bytes, 3))
        val out = FloatArray(4)
        assertEquals(1, Pcm.toFloat(bytes, 3, out))
    }
}
