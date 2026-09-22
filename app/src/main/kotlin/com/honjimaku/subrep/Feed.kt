package com.honjimaku.subrep

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArraySet

/**
 * What the screen shows: the state of the service, the model download, and the last captions.
 * The service writes it from its threads; the screen reads it on the main thread after each
 * change.
 */
object Feed {

    fun interface Listener {
        fun onChange()
    }

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val lock = Any()
    private val finals = ArrayDeque<String>()
    private var lastLevelAt = 0L

    @Volatile var running = false
        private set

    /** [LocalEngine.STATE_LOADING], [LocalEngine.STATE_READY], or an error with its reason. */
    @Volatile var engine = ""
        private set

    /** The time Whisper took for the last piece, divided by the length of the piece. */
    @Volatile var speed = 0f
        private set
    @Volatile var late = 0
        private set
    @Volatile var relayUp = false
        private set
    @Volatile var relayOn = false
        private set
    @Volatile var overlayAnswer = ""
        private set
    @Volatile var error = ""
        private set

    /** The loudest sample of the last frame, 0 to 32768. Shows that sound comes in at all. */
    @Volatile var peak = 0
        private set
    @Volatile var partial = ""
        private set

    /** The model that downloads now, and its progress: bytes so far, total (-1 unknown). */
    @Volatile var downloading: Model? = null
        private set
    @Volatile var downloadDone = 0L
        private set
    @Volatile var downloadTotal = -1L
        private set
    @Volatile var downloadError = ""
        private set

    fun add(listener: Listener) = listeners.add(listener)

    fun remove(listener: Listener) = listeners.remove(listener)

    fun start(relayOn: Boolean) {
        synchronized(lock) { finals.clear() }
        partial = ""
        error = ""
        engine = ""
        speed = 0f
        late = 0
        overlayAnswer = ""
        peak = 0
        relayUp = false
        this.relayOn = relayOn
        running = true
        changed()
    }

    fun stop() {
        running = false
        relayUp = false
        peak = 0
        changed()
    }

    fun fail(reason: String) {
        error = reason
        changed()
    }

    fun engine(state: String) {
        engine = state
        changed()
    }

    fun progress(speed: Float, late: Int) {
        this.speed = speed
        this.late = late
    }

    fun relay(up: Boolean) {
        relayUp = up
        changed()
    }

    fun overlay(answer: String) {
        if (answer == overlayAnswer) return
        overlayAnswer = answer
        changed()
    }

    /** The level meter of the screen, 0 to 10. */
    fun bars(): Int = (peak * 10 / 32768).coerceIn(0, 10)

    fun level(peak: Int) {
        val before = bars()
        this.peak = peak
        // Frames come ten times a second. The screen redraws only when the meter changes, and
        // not more than twice a second: an e-ink screen cannot draw more.
        val now = SystemClock.elapsedRealtime()
        if (bars() == before || now - lastLevelAt < 500) return
        lastLevelAt = now
        changed()
    }

    fun caption(caption: Caption) {
        synchronized(lock) {
            when (caption.type) {
                "final" -> {
                    finals.addLast(caption.text)
                    while (finals.size > LINES) finals.removeFirst()
                    partial = ""
                }
                "partial" -> partial = caption.text
                "clear" -> {
                    finals.clear()
                    partial = ""
                }
            }
        }
        changed()
    }

    fun download(model: Model?, done: Long, total: Long, error: String = "") {
        downloading = model
        downloadDone = done
        downloadTotal = total
        downloadError = error
        changed()
    }

    /** The last lines, and the partial line after them. */
    fun text(): String {
        val lines = synchronized(lock) { finals.toList() }
        return (lines + listOf(partial).filter { it.isNotEmpty() }.map { "$it …" }).joinToString("\n")
    }

    private fun changed() = main.post { listeners.forEach { it.onChange() } }

    private const val LINES = 8
}
