package com.kishan.kavach.domain.wakeword

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

/**
 * Pure Kotlin MFCC extractor.
 * 16 kHz mono PCM16, frame size 400, hop 160, FFT 512, 26 Mel filters, 13 coefficients.
 * Mean-normalised per utterance.
 */
object Mfcc {

    private const val FRAME_SIZE = 400
    private const val HOP_SIZE = 160
    private const val FFT_SIZE = 512
    private const val NUM_MEL = 26
    private const val NUM_DCT = 13
    private const val SAMPLE_RATE = 16000f

    fun extract(pcm16Samples: ShortArray): List<FloatArray> {
        if (pcm16Samples.size < FRAME_SIZE) return emptyList()

        val numFrames = (pcm16Samples.size - FRAME_SIZE) / HOP_SIZE + 1
        val frames = ArrayList<FloatArray>(numFrames)

        val hamming = FloatArray(FRAME_SIZE) { i ->
            (0.54 - 0.46 * cos(2 * PI * i / (FRAME_SIZE - 1))).toFloat()
        }

        val melBank = createMelFilterBank()

        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE
            val real = FloatArray(FFT_SIZE)
            val imag = FloatArray(FFT_SIZE)

            for (i in 0 until FRAME_SIZE) {
                real[i] = pcm16Samples[offset + i] * hamming[i]
            }

            fft(real, imag)

            val powerSpec = FloatArray(FFT_SIZE / 2 + 1)
            for (i in 0..FFT_SIZE / 2) {
                powerSpec[i] = (real[i] * real[i] + imag[i] * imag[i]) / FFT_SIZE
            }

            val melEnergies = FloatArray(NUM_MEL)
            for (m in 0 until NUM_MEL) {
                var energy = 0f
                val filter = melBank[m]
                for (k in filter.indices) {
                    energy += powerSpec[k] * filter[k]
                }
                melEnergies[m] = log10(energy.coerceAtLeast(1e-6f))
            }

            val dctCoeffs = FloatArray(NUM_DCT)
            for (i in 0 until NUM_DCT) {
                var sum = 0f
                for (m in 0 until NUM_MEL) {
                    sum += melEnergies[m] * cos(PI * i * (m + 0.5) / NUM_MEL).toFloat()
                }
                dctCoeffs[i] = sum
            }

            frames.add(dctCoeffs)
        }

        // Mean-normalise across utterance
        if (frames.isNotEmpty()) {
            val mean = FloatArray(NUM_DCT)
            for (frame in frames) {
                for (i in 0 until NUM_DCT) mean[i] += frame[i]
            }
            for (i in 0 until NUM_DCT) mean[i] /= frames.size.toFloat()

            for (frame in frames) {
                for (i in 0 until NUM_DCT) frame[i] -= mean[i]
            }
        }

        return frames
    }

    private fun createMelFilterBank(): Array<FloatArray> {
        val numBins = FFT_SIZE / 2 + 1
        val bank = Array(NUM_MEL) { FloatArray(numBins) }

        val lowMel = hzToMel(0f)
        val highMel = hzToMel(SAMPLE_RATE / 2)
        val melPoints = FloatArray(NUM_MEL + 2) { i ->
            lowMel + i * (highMel - lowMel) / (NUM_MEL + 1)
        }

        val binPoints = IntArray(NUM_MEL + 2) { i ->
            val hz = melToHz(melPoints[i])
            ((FFT_SIZE + 1) * hz / SAMPLE_RATE).toInt().coerceIn(0, numBins - 1)
        }

        for (m in 1..NUM_MEL) {
            val left = binPoints[m - 1]
            val center = binPoints[m]
            val right = binPoints[m + 1]

            for (k in left until center) {
                if (center != left) bank[m - 1][k] = (k - left).toFloat() / (center - left)
            }
            for (k in center until right) {
                if (right != center) bank[m - 1][k] = (right - k).toFloat() / (right - center)
            }
        }

        return bank
    }

    private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float): Float = 700f * (Math.pow(10.0, (mel / 2595f).toDouble()).toFloat() - 1f)

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var l = 1
        while (l < n) {
            val step = l shl 1
            val theta = -PI / l
            val wRe = cos(theta).toFloat()
            val wIm = Math.sin(theta).toFloat()

            var uRe = 1f
            var uIm = 0f

            for (m in 0 until l) {
                for (i in m until n step step) {
                    val ip = i + l
                    val tRe = uRe * real[ip] - uIm * imag[ip]
                    val tIm = uRe * imag[ip] + uIm * real[ip]

                    real[ip] = real[i] - tRe
                    imag[ip] = imag[i] - tIm
                    real[i] += tRe
                    imag[i] += tIm
                }
                val newUre = uRe * wRe - uIm * wIm
                uIm = uRe * wIm + uIm * wRe
                uRe = newUre
            }
            l = step
        }
    }
}
