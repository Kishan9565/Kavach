package com.kishan.kavach.domain.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTest {

    @Test
    fun `dtw distance for identical signals is zero`() {
        val signal = listOf(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(2f, 3f, 4f),
            floatArrayOf(3f, 4f, 5f)
        )
        val dist = Dtw.distance(signal, signal)
        assertEquals(0f, dist, 0.001f)
    }

    @Test
    fun `dtw distance on empty input returns MAX_VALUE`() {
        val empty = emptyList<FloatArray>()
        val signal = listOf(floatArrayOf(1f, 2f, 3f))

        assertEquals(Float.MAX_VALUE, Dtw.distance(empty, signal), 0.001f)
        assertEquals(Float.MAX_VALUE, Dtw.distance(signal, empty), 0.001f)
        assertFalse(WakeTemplate.isWake(empty, WakeEnrolment(listOf(signal), 1.0f)))
    }

    @Test
    fun `time stretched copy is closer than completely different signal`() {
        val original = listOf(
            floatArrayOf(1f, 1f, 1f),
            floatArrayOf(2f, 2f, 2f),
            floatArrayOf(3f, 3f, 3f)
        )

        val stretched = listOf(
            floatArrayOf(1f, 1f, 1f),
            floatArrayOf(1f, 1f, 1f),
            floatArrayOf(2f, 2f, 2f),
            floatArrayOf(3f, 3f, 3f),
            floatArrayOf(3f, 3f, 3f)
        )

        val different = listOf(
            floatArrayOf(10f, 10f, 10f),
            floatArrayOf(20f, 20f, 20f),
            floatArrayOf(30f, 30f, 30f)
        )

        val distStretched = Dtw.distance(original, stretched)
        val distDifferent = Dtw.distance(original, different)

        assertTrue(distStretched < distDifferent)
    }

    @Test
    fun `mfcc extraction on short pcm returns empty`() {
        val pcmShort = ShortArray(100)
        val mfcc = Mfcc.extract(pcmShort)
        assertTrue(mfcc.isEmpty())
    }

    @Test
    fun `enrolment threshold is 1_3x max pairwise distance`() {
        val take1 = listOf(floatArrayOf(1f, 1f), floatArrayOf(2f, 2f))
        val take2 = listOf(floatArrayOf(1.1f, 1.1f), floatArrayOf(2.1f, 2.1f))
        val take3 = listOf(floatArrayOf(1.2f, 1.2f), floatArrayOf(2.2f, 2.2f))

        val enrolment = WakeTemplate.createEnrolment(listOf(take1, take2, take3))
        assertEquals(3, enrolment.templates.size)

        val d12 = Dtw.distance(take1, take2)
        val d13 = Dtw.distance(take1, take3)
        val d23 = Dtw.distance(take2, take3)
        val maxDist = maxOf(d12, maxOf(d13, d23))

        assertEquals(maxDist * 1.3f, enrolment.threshold, 0.01f)
    }
}
