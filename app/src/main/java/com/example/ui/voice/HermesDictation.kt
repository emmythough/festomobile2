package com.example.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * On-device voice dictation for HERMES mode.
 *
 * The Gen 1 voice pipeline (VoiceAudioEngine -> base64 -> WendyApi STT ->
 * chat -> TTS -> playback) is coupled to Wendy's own server proxies, which
 * the Hermes gateway has no equivalent of -- there is NO audio/STT
 * endpoint to upload recordings to. So HERMES dictation takes the least
 * invasive path: the platform SpeechRecognizer transcribes on the device
 * (no recording file, no upload, no new dependency), and the transcript is
 * dropped into the chat composer where the user can edit it before
 * sending. It is never auto-sent.
 *
 * All callbacks arrive on the main thread (the recognizer must be created
 * on the thread's looper, which the UI composition provides). Callbacks
 * are one-shot terminal events: after [onFinal] or [onError] the
 * underlying recognizer is released on a posted message (never from inside
 * its own callback, which some OEM implementations handle badly), and the
 * next [start] builds a fresh one.
 *
 * Callers must hold RECORD_AUDIO before calling [start] -- the ChatScreen
 * permission flow already does for the voice path.
 */
class HermesDictation(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Whether this device has any speech recognition service at all. */
    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts listening. Exactly one of [onPartial]->...->[onFinal] /
     * [onError] follows; a synchronous unavailability lands straight in
     * [onError]. */
    fun start() {
        if (!isAvailable) {
            onError("Voice dictation isn't available on this device -- type instead.")
            return
        }
        destroy()
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onPartial("")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                releaseAfterCallback(rec)
                onError(describeError(error))
            }

            override fun onResults(results: Bundle?) {
                releaseAfterCallback(rec)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isEmpty()) {
                    onError("Didn't catch that -- tap the mic and try again.")
                } else {
                    onFinal(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        rec.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                // Partial hypotheses drive the live "Listening..." chip.
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        )
    }

    /** Graceful stop: ends the current utterance and lets the service
     * deliver its final result (which arrives via [onFinal] or [onError]).
     * This is the "Done" affordance; use [destroy] to cancel instead. */
    fun stopListening() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    /** Cancels any in-flight recognition and releases the recognizer.
     * Safe to call any time, any number of times. */
    fun destroy() {
        val rec = recognizer ?: return
        recognizer = null
        try {
            rec.destroy()
        } catch (_: Exception) {
        }
    }

    /** Terminal callback path: forget the recognizer and release it on a
     * posted message so destroy() never runs re-entrantly inside the
     * service's own callback. */
    private fun releaseAfterCallback(rec: SpeechRecognizer) {
        if (recognizer === rec) recognizer = null
        mainHandler.post {
            try {
                rec.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Didn't hear anything -- tap the mic and try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is required for dictation."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Dictation hit a network problem -- try again."
        SpeechRecognizer.ERROR_BUSY ->
            "The speech recognizer is busy -- try again in a moment."
        SpeechRecognizer.ERROR_RECOGNIZER_ACTIVE ->
            "Dictation is already listening."
        else -> "Dictation failed (code $error) -- try again."
    }
}
