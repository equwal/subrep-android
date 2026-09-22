package com.honjimaku.subrep

import android.os.SystemClock
import java.io.File
import java.util.concurrent.LinkedBlockingDeque
import kotlin.concurrent.thread

/**
 * Speech recognition on the phone: the sound goes through the [Segmenter], and each piece goes
 * to Whisper on one worker thread. A piece that waits while Whisper is behind by more than
 * [QUEUE_LIMIT] pieces is dropped, the oldest first: a late caption helps nobody.
 */
class LocalEngine(
    private val model: File,
    private val language: String,
    private val onCaption: (Caption) -> Unit,
    private val onLanguage: (String) -> Unit,
    private val onState: (String) -> Unit,
) {
    private val segmenter = Segmenter()
    private val queue = LinkedBlockingDeque<FloatArray>()
    private val pieces = ArrayList<FloatArray>()
    private var floats = FloatArray(0)
    private var worker: Thread? = null

    @Volatile private var stopping = false

    /** How many pieces were dropped because Whisper was behind. */
    @Volatile var late = 0
        private set

    /** The time of the last piece divided by its length: below 1 keeps up. */
    @Volatile var speed = 0f
        private set

    fun start() {
        onState(STATE_LOADING)
        worker = thread(name = "whisper") { work() }
    }

    /** The first [count] bytes of [frame], 16-bit PCM, from the capture thread. */
    fun feed(frame: ByteArray, count: Int) {
        if (floats.size < count / 2) floats = FloatArray(count / 2)
        val n = Pcm.toFloat(frame, count, floats)
        pieces.clear()
        segmenter.feed(floats, n, pieces)
        for (piece in pieces) offer(piece)
    }

    /** Stops after the piece that Whisper works on, and the piece that was open. */
    fun stop() {
        stopping = true
        segmenter.flush()?.let { queue.offerLast(it) }
        queue.offerLast(END)
        worker?.join(STOP_WAIT_MS)
        worker?.interrupt()
        worker = null
    }

    private fun offer(piece: FloatArray) {
        while (queue.size >= QUEUE_LIMIT) {
            queue.pollFirst() ?: break
            late++
        }
        queue.offerLast(piece)
    }

    private fun work() {
        val whisper = try {
            Whisper.open(model)
        } catch (e: Exception) {
            onState("${STATE_ERROR}${e.message}")
            return
        } catch (e: UnsatisfiedLinkError) {
            onState("${STATE_ERROR}${e.message}")
            return
        }
        onState(STATE_READY)
        var lastLanguage = ""
        try {
            while (!Thread.currentThread().isInterrupted) {
                val piece = queue.takeFirst()
                if (piece === END) break
                val started = SystemClock.elapsedRealtime()
                val text = try {
                    Hallucinations.collapse(whisper.transcribe(piece, language)).trim()
                } catch (e: WhisperUnavailable) {
                    onState("${STATE_ERROR}${e.message}")
                    continue
                }
                speed = (SystemClock.elapsedRealtime() - started) / 1000f / (piece.size / 16_000f)
                if (language == "auto") {
                    val found = whisper.detectedLanguage
                    if (found.isNotEmpty() && found != lastLanguage) {
                        lastLanguage = found
                        onLanguage(found)
                    }
                }
                if (text.isEmpty() || Hallucinations.isHallucination(text)) continue
                onCaption(Caption("final", text))
            }
        } catch (_: InterruptedException) {
            // Stopped while a piece waited.
        } finally {
            whisper.close()
        }
    }

    companion object {
        const val STATE_LOADING = "loading"
        const val STATE_READY = "ready"
        const val STATE_ERROR = "error: "
        private const val QUEUE_LIMIT = 3
        private const val STOP_WAIT_MS = 8_000L
        private val END = FloatArray(0)
    }
}
