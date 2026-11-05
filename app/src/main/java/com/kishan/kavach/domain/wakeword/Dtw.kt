package com.kishan.kavach.domain.wakeword

import kotlin.math.abs
import kotlin.math.sqrt

object Dtw {

    fun distance(a: List<FloatArray>, b: List<FloatArray>, bandRatio: Float = 0.3f): Float {
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0) return Float.MAX_VALUE

        val band = maxOf(abs(n - m), (maxOf(n, m) * bandRatio).toInt())
        val inf = Float.MAX_VALUE / 4
        var prev = FloatArray(m + 1) { inf }
        prev[0] = 0f

        for (i in 1..n) {
            val cur = FloatArray(m + 1) { inf }
            for (j in maxOf(1, i - band)..minOf(m, i + band)) {
                val cost = euclid(a[i - 1], b[j - 1])
                cur[j] = cost + minOf(prev[j], cur[j - 1], prev[j - 1])
            }
            prev = cur
        }

        return prev[m] / (n + m)
    }

    private fun euclid(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val len = minOf(a.size, b.size)
        for (k in 0 until len) {
            val diff = a[k] - b[k]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}
