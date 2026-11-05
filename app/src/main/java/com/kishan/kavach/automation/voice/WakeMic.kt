package com.kishan.kavach.automation.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.kishan.kavach.domain.wakeword.Mfcc
import com.kishan.kavach.domain.wakeword.WakeEnrolment
import com.kishan.kavach.domain.wakeword.WakeTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val SAMPLE_RATE = 16000
private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
private const val REFRACTORY_MS = 2500L
private const val ENERGY_THRESHOLD = 500f

class WakeMic(
    private val scope: CoroutineScope,
    private val onWakeWordDetected: () -> Unit
) {
    @Volatile var enabled: Boolean = false
    @Volatile var isScreenOn: Boolean = true
    @Volatile var isTtsSpeaking: Boolean = false
    @Volatile var isRecognizerActive: Boolean = false
    @Volatile var enrolment: WakeEnrolment? = null

    private var recordJob: Job? = null
    private var lastTriggerTime = 0L
    private val triggerStamps = ArrayDeque<Long>()

    fun start() {
        if (recordJob?.isActive == true) return
        val currentEnrolment = enrolment ?: return
        if (currentEnrolment.templates.isEmpty()) return

        recordJob = scope.launch(Dispatchers.IO) {
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            val bufferSize = maxOf(minBufSize, 3200)

            @SuppressLint("MissingPermission")
            val recorder = runCatching {
                AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufferSize)
            }.getOrNull() ?: return@launch

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return@launch
            }

            try {
                recorder.startRecording()
                val audioBuffer = ShortArray(1600) // 100ms chunks

                val speechSegment = ArrayList<Short>()
                var silenceCount = 0

                while (isActive) {
                    if (!enabled || !isScreenOn || isTtsSpeaking || isRecognizerActive) {
                        kotlinx.coroutines.delay(200)
                        speechSegment.clear()
                        continue
                    }

                    val readShorts = recorder.read(audioBuffer, 0, audioBuffer.size)
                    if (readShorts <= 0) continue

                    val rms = computeRms(audioBuffer, readShorts)

                    if (rms > ENERGY_THRESHOLD) {
                        // Speech active
                        for (i in 0 until readShorts) speechSegment.add(audioBuffer[i])
                        silenceCount = 0
                    } else if (speechSegment.isNotEmpty()) {
                        // Silence during speech segment
                        for (i in 0 until readShorts) speechSegment.add(audioBuffer[i])
                        silenceCount++

                        // If silence >= 300ms (3 chunks), process segment
                        if (silenceCount >= 3) {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastTriggerTime >= REFRACTORY_MS) {
                                val pcmSamples = speechSegment.toShortArray()
                                val liveMfcc = Mfcc.extract(pcmSamples)

                                if (liveMfcc.isNotEmpty() && WakeTemplate.isWake(liveMfcc, currentEnrolment)) {
                                    lastTriggerTime = now
                                    recordTrigger(now)
                                    onWakeWordDetected()
                                }
                            }
                            speechSegment.clear()
                            silenceCount = 0
                        }
                    }

                    // Limit segment size to max 3 seconds (48,000 samples)
                    if (speechSegment.size > 48000) {
                        speechSegment.clear()
                        silenceCount = 0
                    }
                }
            } finally {
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                    recorder.release()
                }
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
    }

    private fun computeRms(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / size).toFloat()
    }

    private fun recordTrigger(nowMs: Long) {
        synchronized(triggerStamps) {
            triggerStamps.addLast(nowMs)
            while (triggerStamps.isNotEmpty() && nowMs - triggerStamps.first() > 3_600_000L) {
                triggerStamps.removeFirst()
            }
            Log.d("WakeMic", "Wake word trigger count in last hour: ${triggerStamps.size}")
        }
    }
}
