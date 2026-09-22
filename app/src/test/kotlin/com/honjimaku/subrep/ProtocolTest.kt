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

    /** The text of a caption of the engine comes out as it went in, in any script. */
    @Test
    fun aCaptionIsReadBackUnchanged(): Unit = runBlocking {
        checkAll(types, texts) { type, text ->
            val json = JSONObject().put("type", type).put("text", text).put("ts", 1.5).toString()
            assertEquals(Caption(type, text), Messages.caption(json))
        }
    }

    @Test
    fun theOtherMessagesAreNoCaption(): Unit = runBlocking {
        checkAll(Arb.element("ready", "status", "hello", "error", "gone", ""), texts) { type, text ->
            assertNull(Messages.caption(JSONObject().put("type", type).put("text", text).toString()))
        }
        assertNull(Messages.caption("not json"))
        assertNull(Messages.caption(""))
    }

    @Test
    fun anErrorGivesItsReason() {
        assertEquals("too many active tabs", Messages.error("""{"type":"error","error":"too many active tabs"}"""))
        assertEquals("error", Messages.error("""{"type":"error"}"""))
        assertNull(Messages.error("""{"type":"final","text":"x"}"""))
        assertNull(Messages.error("junk"))
    }

    @Test
    fun theHellosHaveWhatTheServersRead(): Unit = runBlocking {
        checkAll(Arb.int(8000..192000), texts, texts) { rate, lang, source ->
            val hello = JSONObject(Messages.engineHello(rate, lang, source))
            assertEquals("hello", hello.getString("type"))
            assertEquals(rate, hello.getInt("sampleRate"))
            assertEquals(lang, hello.getString("lang"))
            assertEquals(source, hello.getString("source"))
        }
        checkAll(texts, texts, texts) { secret, lang, title ->
            val hello = JSONObject(Messages.relayHello(secret, lang, title))
            assertEquals(secret, hello.getString("secret"))
            assertEquals(lang, hello.getString("lang"))
            assertEquals(title, hello.getString("title"))
        }
        assertTrue(Messages.isReady("""{"type":"ready","room":"x"}"""))
    }
}

class PcmTest {

    /** The peak of a frame is the largest sample size in it, with the sign taken away. */
    @Test
    fun thePeakIsTheLargestSampleSize(): Unit = runBlocking {
        checkAll(Arb.list(Arb.int(-32768..32767), 0..400)) { samples ->
            val bytes = ByteArray(samples.size * 2)
            samples.forEachIndexed { i, s ->
                bytes[2 * i] = (s and 0xFF).toByte()
                bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
            }
            val expected = samples.maxOfOrNull { if (it < 0) -it else it } ?: 0
            assertEquals(expected, Pcm.peak(bytes, bytes.size))
        }
    }

    @Test
    fun onlyTheFirstCountBytesCount() {
        val bytes = byteArrayOf(0, 0, 0, 0x7F.toByte())
        assertEquals(0, Pcm.peak(bytes, 2))
        assertEquals(32512, Pcm.peak(bytes, 4))
        assertEquals(0, Pcm.peak(bytes, 3))
    }
}
