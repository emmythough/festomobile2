package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class RealVoiceEngine(
    private val context: Context,
    private val onRmsChangedCallback: (Float) -> Unit,
    private val onPartialTranscriptCallback: (String) -> Unit,
    private val onFinalTranscriptCallback: (String) -> Unit,
    private val onErrorCallback: (String) -> Unit,
    private val onTtsStartCallback: () -> Unit,
    private val onTtsDoneCallback: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isListening = false

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val result = engine.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }
                    engine.setPitch(1.0f)
                    engine.setSpeechRate(1.05f)
                    isTtsReady = true
                    Log.d("RealVoiceEngine", "TextToSpeech initialized successfully")
                }
            } else {
                Log.e("RealVoiceEngine", "TextToSpeech init failed with status: $status")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    onTtsStartCallback()
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    onTtsDoneCallback()
                }
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    onTtsDoneCallback()
                }
            }
        })
    }

    fun startListening() {
        mainHandler.post {
            stopSpeaking()

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onErrorCallback("Speech recognition service is not available on this device.")
                return@post
            }

            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            Log.d("RealVoiceEngine", "Ready for speech")
                        }

                        override fun onBeginningOfSpeech() {
                            Log.d("RealVoiceEngine", "Beginning of speech")
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // Convert dB (-2 to 10 dB) to normalized 0f..1f range
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                            onRmsChangedCallback(normalized)
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening = false
                            Log.d("RealVoiceEngine", "End of speech")
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                                SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                                SpeechRecognizer.ERROR_SERVER -> "Server error"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                                else -> "Recognition error ($error)"
                            }
                            Log.w("RealVoiceEngine", "SpeechRecognizer error: $message ($error)")
                            onErrorCallback(message)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognizedText = matches?.firstOrNull()?.trim() ?: ""
                            Log.d("RealVoiceEngine", "Final recognition: $recognizedText")
                            if (recognizedText.isNotBlank()) {
                                onFinalTranscriptCallback(recognizedText)
                            } else {
                                onErrorCallback("No speech detected")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partialText = matches?.firstOrNull()?.trim() ?: ""
                            if (partialText.isNotBlank()) {
                                onPartialTranscriptCallback(partialText)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("RealVoiceEngine", "Failed to start listening", e)
                onErrorCallback("Could not start microphone: ${e.localizedMessage}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                }
            } catch (e: Exception) {
                Log.e("RealVoiceEngine", "Error stopping listening", e)
            }
        }
    }

    fun cancelListening() {
        mainHandler.post {
            try {
                isListening = false
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("RealVoiceEngine", "Error canceling listening", e)
            }
        }
    }

    fun speak(text: String) {
        mainHandler.post {
            if (tts == null || !isTtsReady) {
                Log.w("RealVoiceEngine", "TTS not ready yet, queueing after re-init")
                // Still fire start & done callbacks so UI flow does not stall
                onTtsStartCallback()
                mainHandler.postDelayed({ onTtsDoneCallback() }, 1500)
                return@post
            }

            // Remove markdown characters for cleaner speech synthesis
            val cleanedText = text
                .replace(Regex("`{1,3}[^`]*`{1,3}"), "")
                .replace(Regex("[#*_~]"), "")
                .trim()

            val utteranceId = "utt_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }

            tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                tts?.stop()
            } catch (e: Exception) {
                Log.e("RealVoiceEngine", "Error stopping TTS", e)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
                isTtsReady = false
            } catch (e: Exception) {
                Log.e("RealVoiceEngine", "Error destroying RealVoiceEngine", e)
            }
        }
    }
}
