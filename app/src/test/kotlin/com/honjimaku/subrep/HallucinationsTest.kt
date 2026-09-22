package com.honjimaku.subrep

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HallucinationsTest {

    @Test
    fun aLoopIsCutToThreeOfItsPiece() {
        assertEquals("Ну конечно, о-о-о- О чаша!", Hallucinations.collapse("Ну конечно, о-о-о-о-о-о-о-о-о-о-о-о-о- О чаша!"))
        assertEquals("はいはいはい", Hallucinations.collapse("はいはいはいはいはいはいはい"))
        assertEquals("no, no, no, no", Hallucinations.collapse("no, no, no, no"))
        assertEquals("Все голоса у всех певцов одинаково мерзкие.", Hallucinations.collapse("Все голоса у всех певцов одинаково мерзкие."))
    }

    /** A collapse leaves a line without a loop as it is, and a second collapse changes nothing. */
    @Test
    fun aCollapseNeverGrowsAndIsDoneOnce(): Unit = runBlocking {
        checkAll(Arb.string(0..40), Arb.string(1..4), Arb.int(0..30)) { text, piece, times ->
            val loud = text + piece.repeat(times)
            val once = Hallucinations.collapse(loud)
            assertTrue(once.length <= loud.length)
            assertEquals(once, Hallucinations.collapse(once))
        }
    }

    @Test
    fun theCreditsThatWhisperInventsAreDropped() {
        for (line in listOf(
            "ご視聴ありがとうございました",
            "チャンネル登録お願いします",
            "字幕",
            "Субтитры сделал DimaTorzok",
            "Продолжение следует...",
            "Thanks for watching!",
            "Thank you.",
            "Subtitles by the Amara.org community",
            "Tekstitys: Yle",
            "...",
            "♪",
            "",
        )) {
            assertTrue(line, Hallucinations.isHallucination(line))
        }
    }

    @Test
    fun speechIsKept() {
        for (line in listOf(
            "女のいない男たちは、東京で暮らしている。",
            "Нет ничего спиртного. Царицы небесные!",
            "Thank you for the coffee, it was good.",
            "Kiitos, että tulit tänne tänään.",
            "The boy said: please subscribe to the newspaper.",
        )) {
            assertFalse(line, Hallucinations.isHallucination(line))
        }
    }
}
