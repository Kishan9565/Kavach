package com.kishan.kavach.domain.wakeword

data class WakeEnrolment(
    val templates: List<List<FloatArray>>,
    val threshold: Float
)

object WakeTemplate {

    fun createEnrolment(takes: List<List<FloatArray>>): WakeEnrolment {
        if (takes.isEmpty()) return WakeEnrolment(emptyList(), Float.MAX_VALUE)
        if (takes.size == 1) return WakeEnrolment(takes, 1.5f)

        var maxDist = 0f
        for (i in takes.indices) {
            for (j in i + 1 until takes.size) {
                val dist = Dtw.distance(takes[i], takes[j])
                if (dist > maxDist && dist < Float.MAX_VALUE / 4) {
                    maxDist = dist
                }
            }
        }

        val threshold = maxDist * 1.3f
        return WakeEnrolment(takes, threshold)
    }

    fun isWake(liveAudioMfcc: List<FloatArray>, enrolment: WakeEnrolment): Boolean {
        if (enrolment.templates.isEmpty() || liveAudioMfcc.isEmpty()) return false

        val minDist = enrolment.templates.minOfOrNull { Dtw.distance(liveAudioMfcc, it) } ?: Float.MAX_VALUE
        return minDist <= enrolment.threshold
    }
}
