package com.honjimaku.subrep

/** Sums over 16-bit little-endian PCM, for the level meter. */
object Pcm {

    /** The largest sample size in the first [count] bytes of [bytes]: 0 for silence, up to 32768. */
    fun peak(bytes: ByteArray, count: Int): Int {
        var peak = 0
        var i = 0
        while (i + 1 < count) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            val size = if (sample < 0) -sample else sample
            if (size > peak) peak = size
            i += 2
        }
        return peak
    }
}
