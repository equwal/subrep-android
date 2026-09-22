package com.honjimaku.subrep

/** Sums and conversions over 16-bit little-endian PCM. */
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

    /**
     * The first [count] bytes of [bytes] as samples from -1 to 1, into [out]. Returns how many
     * samples were written: half of [count], or the size of [out] when that is less.
     */
    fun toFloat(bytes: ByteArray, count: Int, out: FloatArray): Int {
        var i = 0
        var n = 0
        while (i + 1 < count && n < out.size) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            out[n++] = sample / 32768f
            i += 2
        }
        return n
    }

    /** [samples] from -1 to 1 as 16-bit little-endian PCM: the reverse of [toFloat]. */
    fun toBytes(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for ((n, value) in samples.withIndex()) {
            val sample = Math.round(value * 32768f).coerceIn(-32768, 32767)
            out[2 * n] = sample.toByte()
            out[2 * n + 1] = (sample shr 8).toByte()
        }
        return out
    }
}
