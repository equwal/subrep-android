package com.honjimaku.subrep

import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SegmenterTest {

    private val rate = 16_000

    private fun silence(ms: Int) = FloatArray(rate * ms / 1000)

    /** Loud noise, as speech would be: well above the gate. */
    private fun speech(ms: Int, random: Random) = FloatArray(rate * ms / 1000) { random.nextFloat() * 0.6f - 0.3f }

    /** Feeds [sound] in frames of 100 ms, as the capture does, and returns the pieces. */
    private fun pieces(segmenter: Segmenter, sound: FloatArray): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        val frame = rate / 10
        var at = 0
        while (at < sound.size) {
            val n = minOf(frame, sound.size - at)
            segmenter.feed(sound.copyOfRange(at, at + n), n, out)
            at += n
        }
        return out
    }

    @Test
    fun silenceGivesNoPiece(): Unit = runBlocking {
        checkAll(Arb.int(0..30_000), Arb.float(0f, 0.003f)) { ms, amplitude ->
            val random = Random(ms)
            val quiet = FloatArray(rate * ms / 1000) { random.nextFloat() * 2 * amplitude - amplitude }
            val segmenter = Segmenter()
            assertTrue(pieces(segmenter, quiet).isEmpty())
            assertNull(segmenter.flush())
        }
    }

    /** One burst of speech between silences is one piece: the speech, a little before, and the pause after. */
    @Test
    fun oneBurstOfSpeechIsOnePiece(): Unit = runBlocking {
        checkAll(Arb.int(500..9_000)) { ms ->
            val random = Random(ms)
            val sound = silence(1_000) + speech(ms, random) + silence(2_000)
            val out = pieces(Segmenter(), sound)
            assertEquals("pieces for $ms ms", 1, out.size)
            val samples = out[0].size
            // The piece has the speech, up to 320 ms before it, the pause of 650 ms after it, and block rounding.
            assertTrue("$samples samples for $ms ms", samples >= rate * ms / 1000)
            assertTrue("$samples samples for $ms ms", samples <= rate * (ms + 320 + 650 + 2 * 32) / 1000)
        }
    }

    @Test
    fun aShortNoiseIsDropped() {
        val random = Random(1)
        val sound = silence(1_000) + speech(200, random) + silence(2_000)
        assertTrue(pieces(Segmenter(), sound).isEmpty())
    }

    /** Speech that goes on is cut at the maximum, and each cut piece is kept. */
    @Test
    fun noPieceIsLongerThanTheMaximum(): Unit = runBlocking {
        checkAll(Arb.list(Arb.int(100..15_000), 1..6)) { lengths ->
            val random = Random(lengths.hashCode())
            var sound = silence(500)
            for ((i, ms) in lengths.withIndex()) sound += if (i % 2 == 0) speech(ms, random) else silence(ms)
            sound += silence(1_500)
            val out = pieces(Segmenter(), sound)
            for (piece in out) assertTrue("${piece.size} samples", piece.size <= rate * 11 + rate * 32 / 1000)
            // A burst under 450 ms is dropped by design. A cut at the maximum can leave a rest
            // under 450 ms behind too, so each piece may cost up to half a second of speech.
            val spoken = lengths.filterIndexed { i, ms -> i % 2 == 0 && ms >= 600 }.sum()
            val kept = out.sumOf { it.size }
            assertTrue("kept $kept samples of $spoken ms of speech", kept >= rate * spoken / 1000 - (out.size + 1) * rate / 2)
        }
    }

    @Test
    fun theOpenPieceComesOutAtTheEnd() {
        val random = Random(2)
        val segmenter = Segmenter()
        val out = pieces(segmenter, silence(500) + speech(1_500, random))
        assertTrue(out.isEmpty())
        val last = segmenter.flush()
        assertTrue(last != null && last.size >= rate * 1_500 / 1000)
        assertNull(segmenter.flush())
    }
}
