package com.honjimaku.subrep

import kotlin.math.sqrt

/**
 * Cuts the sound into the pieces that go to Whisper: speech, closed by a pause.
 *
 * The rules are those of the desktop app. The sound comes in blocks of [blockMs]. A block is
 * speech when its level is above the gate: the noise floor times [ratio], and at least
 * [floor]. A piece starts [prerollMs] before the first speech block, and closes after
 * [pauseMs] of silence, or at [maxMs] when the speech goes on. A piece with less than
 * [minSpeechMs] of speech is dropped: a click, a cough.
 */
class Segmenter(
    sampleRate: Int = 16_000,
    blockMs: Int = 32,
    private val floor: Float = 0.004f,
    private val ratio: Float = 3f,
    pauseMs: Int = 650,
    minSpeechMs: Int = 450,
    maxMs: Int = 11_000,
    prerollMs: Int = 320,
) {
    val blockSamples = sampleRate * blockMs / 1000
    private val pauseBlocks = (pauseMs + blockMs - 1) / blockMs
    private val minSpeechBlocks = (minSpeechMs + blockMs - 1) / blockMs
    private val maxSamples = sampleRate * maxMs / 1000
    private val prerollBlocks = (prerollMs + blockMs - 1) / blockMs

    private val partial = FloatArray(blockSamples)
    private var partialCount = 0
    private val preroll = ArrayDeque<FloatArray>()
    private var open: MutableList<FloatArray>? = null
    private var openSamples = 0
    private var speechBlocks = 0
    private var silenceBlocks = 0
    private var noise = floor

    /** Feeds the first [count] samples of [samples]. The pieces that closed go to [out]. */
    fun feed(samples: FloatArray, count: Int, out: MutableList<FloatArray>) {
        var i = 0
        while (i < count) {
            val take = minOf(blockSamples - partialCount, count - i)
            System.arraycopy(samples, i, partial, partialCount, take)
            partialCount += take
            i += take
            if (partialCount == blockSamples) {
                block(partial.copyOf(), out)
                partialCount = 0
            }
        }
    }

    /** The open piece at the end of the sound, when it has enough speech; else null. */
    fun flush(): FloatArray? {
        val blocks = open ?: return null
        open = null
        return if (speechBlocks >= minSpeechBlocks) join(blocks) else null
    }

    private fun block(block: FloatArray, out: MutableList<FloatArray>) {
        val level = rms(block)
        val speech = level > maxOf(floor, noise * ratio)
        // The noise floor follows the quiet blocks: down at once, up slowly.
        if (!speech) noise += (level - noise) * (if (level < noise) 0.5f else 0.05f)

        val blocks = open
        if (blocks == null) {
            preroll.addLast(block)
            while (preroll.size > prerollBlocks) preroll.removeFirst()
            if (speech) {
                open = preroll.toMutableList()
                openSamples = preroll.sumOf { it.size }
                preroll.clear()
                speechBlocks = 1
                silenceBlocks = 0
            }
            return
        }

        blocks.add(block)
        openSamples += block.size
        if (speech) {
            speechBlocks++
            silenceBlocks = 0
        } else {
            silenceBlocks++
        }
        when {
            silenceBlocks >= pauseBlocks -> close(out, goesOn = false)
            openSamples >= maxSamples -> close(out, goesOn = speech)
        }
    }

    /** Closes the open piece. When the speech [goesOn], the next piece starts at once. */
    private fun close(out: MutableList<FloatArray>, goesOn: Boolean) {
        val blocks = open ?: return
        if (speechBlocks >= minSpeechBlocks) out.add(join(blocks))
        open = if (goesOn) mutableListOf() else null
        openSamples = 0
        speechBlocks = 0
        silenceBlocks = 0
    }

    private fun join(blocks: List<FloatArray>): FloatArray {
        val whole = FloatArray(blocks.sumOf { it.size })
        var at = 0
        for (b in blocks) {
            System.arraycopy(b, 0, whole, at, b.size)
            at += b.size
        }
        return whole
    }

    private fun rms(block: FloatArray): Float {
        var sum = 0.0
        for (x in block) sum += x.toDouble() * x
        return sqrt(sum / block.size).toFloat()
    }
}
