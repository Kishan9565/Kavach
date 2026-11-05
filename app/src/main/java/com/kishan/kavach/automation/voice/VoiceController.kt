package com.kishan.kavach.automation.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kishan.kavach.domain.tools.Guard
import java.util.Locale

class VoiceController(
    private val ctx: Context,
    private val onCommand: (String) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null

    init {
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("en", "IN")
            }
        }
        setupTtsListener()
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                stopListening()
            }

            override fun onDone(utteranceId: String?) {
                abandonAudioFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                abandonAudioFocus()
            }
        })
    }

    fun speak(text: String, lang: String = "en-IN") {
        if (!ttsReady) return
        stopListening()
        requestAudioFocus()

        val locale = if (lang == "hi-IN") Locale("hi", "IN") else Locale("en", "IN")
        tts?.language = locale

        if (Build.VERSION.SDK_INT >= 21) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "draivo_tts")
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun startListening(lang: String = "en-IN") {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onError("Speech recognition not available")
            return
        }

        stopListening()
        stopSpeaking()

        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    onError("Recognition error: $error")
                }

                override fun onResults(results: Bundle?) {
                    val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!spokenText.isNullOrBlank()) {
                        processVoiceCommand(spokenText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        recognizer?.startListening(intent)
    }

    fun processVoiceCommand(rawText: String) {
        // Enforce the same guard check as typed text
        val blockedReason = Guard.checkUserText(rawText)
        if (blockedReason != null) {
            speak(blockedReason)
            onError(blockedReason)
            return
        }
        onCommand(rawText)
    }

    fun stopListening() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        }
    }

    fun stopSpeaking() {
        runCatching {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
            abandonAudioFocus()
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attr)
                .build()
            focusRequest = req
            audioManager?.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    fun destroy() {
        stopListening()
        stopSpeaking()
        runCatching { tts?.shutdown() }
    }
}
