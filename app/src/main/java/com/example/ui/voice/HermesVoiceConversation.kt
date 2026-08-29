package com.example.ui.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.BackendMode
import com.example.data.FestoAppState
import com.example.ui.components.markdown.audioPathRegex
import com.example.ui.components.markdown.markdownImageRegex
import java.util.Locale

/**
 * Hands-free HERMES voice conversation: the user speaks, the transcript is
 * auto-sent after ~1.5s of silence, the reply streams into the normal chat
 * transcript, and the on-device [TextToSpeech] engine speaks it aloud. When
 * the utterance finishes (UtteranceProgressListener.onDone), listening
 * re-arms automatically -- a continuous conversation.
 *
 * Plumbing reuse (nothing duplicated):
 *  - Recognition wraps [HermesDictation] -- the same recognizer-per-listen-
 *    cycle pattern and error mapping -- with the hands-free-only
 *    [HermesDictation.onNoSpeech] callback so silence re-arms quietly
 *    instead of raising the dictation error chip.
 *  - Sending goes through [FestoAppState.sendMessage]: the exact path the
 *    composer's send button uses (same user bubble, same streaming turn).
 *  - Reply completion arrives through [FestoAppState.onHermesTurnCompleted],
 *    fired by the Hermes streaming turn when it settles -- including the
 *    "Couldn't reach Wendy..." failure text, which is worth hearing in a
 *    hands-free loop.
 *
 * Loop (all mutations on the main thread -- the recognizer must be created
 * on a looper thread, TTS callbacks arrive on one, and the Hermes turn
 * coroutine runs on the composition's main scope):
 *
 *     LISTENING --final text + 1.5s of silence--> auto-send --> THINKING
 *     THINKING  --turn completed--> SPEAKING --TTS onDone--> LISTENING
 *
 * [stop] tears the loop down cleanly from any state: pending timers
 * cancelled, recognizer destroyed, TTS stopped + shut down, the turn hook
 * cleared.
 *
 * The loop is never auto-started: it runs only while the ChatScreen
 * headset toggle is on, and ChatScreen stops it on backend switch, screen
 * leave, and backgrounding. Gen 1 voice paths are untouched. Empty final
 * transcripts re-arm without sending (micropause rule); a TTS engine that
 * fails to init degrades the loop to dictation-only (still auto-sends,
 * replies stay as text) with a one-time chip message.
 */
class HermesVoiceConversation(
    context: Context,
    private val appState: FestoAppState,
) {
    enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

    companion object {
        /** Silence window after a final transcript before the accumulated
         * text is auto-sent. Continuing speech inside the window cancels
         * the send and keeps accumulating. */
        private const val SEND_SILENCE_MS = 1500L

        /** Retry cadence when the send path refuses because a previous
         * turn is still streaming (e.g. a manually typed message). */
        private const val SEND_RETRY_MS = 1500L
        private const val SEND_MAX_ATTEMPTS = 3

        /** Watchdog for the THINKING phase: sendMessage() can no-op (and
         * the completion hook then never fires), so the loop must recover
         * to listening instead of hanging. Generous -- a tool-using turn
         * can legitimately run minutes. */
        private const val TURN_WATCHDOG_MS = 90_000L

        /** Pause before re-arming after a surfaced recognizer error, so a
         * failing engine can't spin the mic in a tight loop. */
        private const val REARM_DELAY_MS = 1200L

        /** Consecutive surfaced recognizer errors before the loop gives up
         * (notice chip + stop) instead of retrying forever. */
        private const val MAX_CONSECUTIVE_ERRORS = 5

        /** Spoken-length cap: speak the first portion of long replies. */
        private const val SPOKEN_CHAR_CAP = 800

        private const val UTTERANCE_ID = "hermes_voice_conversation_reply"
    }

    // Application context: the recognizer and the TTS engine outlive any
    // single Activity view of the chat screen.
    private val appContext: Context = context.applicationContext

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- Observable state (Compose) -------------------------------------

    /** What the loop is doing right now; IDLE when stopped. */
    var phase by mutableStateOf(Phase.IDLE)
        private set

    /** Live partial transcript while listening (or the full pending
     * message during the silence window). */
    var livePartial by mutableStateOf("")
        private set

    /** Mute switch: pauses TTS speaking only -- the loop keeps listening
     * and auto-sending, replies show as text. Reset on stop. */
    var muted by mutableStateOf(false)
        private set

    /** One-time chip messages (TTS init failure, dropped send). Dismissible
     * by ChatScreen; cleared on the next start. */
    var voiceNotice by mutableStateOf<String?>(null)

    val isActive: Boolean
        get() = phase != Phase.IDLE

    // ---- Internal state --------------------------------------------------

    private var active = false
    private var accumulated = ""
    private var sendAttempts = 0
    private var sendTimerPending = false

    /** Whether the CURRENT listen cycle has heard any speech -- a partial
     * inside the silence window means the user kept talking. */
    private var cycleHasSpeech = false
    private var consecutiveSurfaceErrors = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsInitFailed = false
    private var ttsFailureAnnounced = false

    private val sendRunnable = Runnable { sendIfTimerFired() }
    private val rearmRunnable = Runnable {
        if (active && phase == Phase.LISTENING && !sendTimerPending) {
            beginListenCycle(resetPartial = true)
        }
    }
    private val turnWatchdogRunnable = Runnable {
        if (active && phase == Phase.THINKING) {
            // The turn settled without the completion hook (cancelled
            // stream, no-op send, backend switch) -- recover the loop.
            beginListenCycle(resetPartial = true)
        }
    }

    private val ttsProgress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            if (utteranceId == UTTERANCE_ID && active && phase == Phase.SPEAKING) {
                beginListenCycle(resetPartial = true)
            }
        }

        override fun onError(utteranceId: String?) {
            // A failed utterance must not wedge the loop in SPEAKING --
            // carry on listening (the reply is still on screen as text).
            if (utteranceId == UTTERANCE_ID && active && phase == Phase.SPEAKING) {
                beginListenCycle(resetPartial = true)
            }
        }
    }

    /** The recognition side: one [HermesDictation], new recognizer instance
     * per listen cycle (its own pattern), with the hands-free onNoSpeech
     * routing so silence is quiet instead of an error chip. */
    private val recognizer = HermesDictation(
        context = appContext,
        onPartial = { partial ->
            if (active && phase == Phase.LISTENING && partial.isNotBlank()) {
                // Speech inside the silence window: the user kept talking,
                // so hold the auto-send and keep accumulating.
                cycleHasSpeech = true
                if (sendTimerPending) cancelSendTimer()
                livePartial = partial
            }
        },
        onFinal = { text -> onListenFinal(text) },
        onError = { message -> onListenError(message) },
        onNoSpeech = { onListenNoSpeech() },
    )

    // ---- Public API -------------------------------------------------------

    /** Starts the loop. Requires Hermes mode, a picked shared session, and
     * RECORD_AUDIO (the ChatScreen permission flow enforces the last one).
     * Fails fast with a chip message otherwise. */
    fun start() {
        if (active) return
        if (appState.backendMode != BackendMode.HERMES) return
        if (appState.hermesSessionId == null) {
            voiceNotice = "Pick a Wendy session in Settings first -- hands-free voice shares the session with Telegram."
            return
        }
        if (!recognizer.isAvailable) {
            voiceNotice = "Speech recognition isn't available on this device -- hands-free voice can't run here."
            return
        }
        active = true
        accumulated = ""
        sendAttempts = 0
        sendTimerPending = false
        cycleHasSpeech = false
        consecutiveSurfaceErrors = 0
        muted = false
        voiceNotice = null
        ttsInitFailed = false
        ttsFailureAnnounced = false
        ensureTts()
        appState.onHermesTurnCompleted = { finalText -> onTurnFinal(finalText) }
        phase = Phase.LISTENING
        beginListenCycle(resetPartial = true)
    }

    /** Stops the loop from any state: cancels timers, destroys the
     * recognizer, flushes + shuts down TTS, clears the turn hook.
     * Idempotent. */
    fun stop() {
        active = false
        mainHandler.removeCallbacks(sendRunnable)
        mainHandler.removeCallbacks(rearmRunnable)
        mainHandler.removeCallbacks(turnWatchdogRunnable)
        recognizer.destroy()
        appState.onHermesTurnCompleted = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsReady = false
        muted = false
        accumulated = ""
        sendAttempts = 0
        sendTimerPending = false
        cycleHasSpeech = false
        livePartial = ""
        phase = Phase.IDLE
    }

    /** Mute switch: pauses TTS speaking only. Unmuting takes effect on the
     * NEXT reply (a cut-off utterance is not re-spoken). @JvmName avoids the
     * JVM-signature clash with the `muted` property's private setter. */
    @JvmName("setMutedState")
    fun setMuted(value: Boolean) {
        if (muted == value) return
        muted = value
        if (value && phase == Phase.SPEAKING) {
            // Cut the current utterance now. TextToSpeech.stop() may end
            // it with an error callback rather than onDone (engine-
            // dependent), so resume listening here instead of waiting.
            try {
                tts?.stop()
            } catch (_: Exception) {
            }
            if (active && phase == Phase.SPEAKING) {
                beginListenCycle(resetPartial = true)
            }
        }
    }

    // ---- Listen cycle -----------------------------------------------------

    private fun beginListenCycle(resetPartial: Boolean) {
        if (!active) return
        phase = Phase.LISTENING
        if (resetPartial) livePartial = ""
        cycleHasSpeech = false
        recognizer.start()
    }

    private fun onListenFinal(text: String) {
        if (!active || phase != Phase.LISTENING) return
        consecutiveSurfaceErrors = 0
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            accumulated = if (accumulated.isBlank()) trimmed else "$accumulated $trimmed"
        }
        if (accumulated.isNotBlank()) {
            // Show the full pending message during the silence window and
            // keep the mic warm: if the user continues, the new cycle's
            // partials cancel the send and accumulate further.
            livePartial = accumulated
            armSendTimer(SEND_SILENCE_MS)
            beginListenCycle(resetPartial = false)
        } else {
            // Micropause rule: an empty transcript re-arms without sending.
            beginListenCycle(resetPartial = true)
        }
    }

    private fun onListenNoSpeech() {
        if (!active || phase != Phase.LISTENING) return
        consecutiveSurfaceErrors = 0
        if (sendTimerPending) {
            // Silence inside the auto-send window is exactly what we are
            // waiting for -- do not restart the recognizer on top of it.
            return
        }
        if (accumulated.isNotBlank()) {
            // A previous cycle errored after speech was heard (its send
            // window died with it) -- the words still deserve to go out.
            livePartial = accumulated
            armSendTimer(SEND_SILENCE_MS)
            beginListenCycle(resetPartial = false)
            return
        }
        beginListenCycle(resetPartial = true)
    }

    private fun onListenError(message: String) {
        if (!active || phase != Phase.LISTENING) return
        consecutiveSurfaceErrors++
        livePartial = message
        if (consecutiveSurfaceErrors >= MAX_CONSECUTIVE_ERRORS) {
            voiceNotice = message
            stop()
            return
        }
        mainHandler.removeCallbacks(rearmRunnable)
        mainHandler.postDelayed(rearmRunnable, REARM_DELAY_MS)
    }

    // ---- Auto-send --------------------------------------------------------

    private fun armSendTimer(delayMs: Long) {
        mainHandler.removeCallbacks(sendRunnable)
        sendTimerPending = true
        mainHandler.postDelayed(sendRunnable, delayMs)
    }

    private fun cancelSendTimer() {
        sendTimerPending = false
        mainHandler.removeCallbacks(sendRunnable)
    }

    private fun sendIfTimerFired() {
        sendTimerPending = false
        if (!active || phase != Phase.LISTENING) return
        if (cycleHasSpeech) return // user started talking again; their final re-arms
        flushAccumulatedNow()
    }

    /** Sends the accumulated transcript through the SAME send path the
     * composer uses ([FestoAppState.sendMessage]) and moves to THINKING. */
    private fun flushAccumulatedNow() {
        if (!active) return
        val text = accumulated.trim()
        if (text.isEmpty()) {
            // Micropause rule: never send an empty turn.
            beginListenCycle(resetPartial = true)
            return
        }
        // The send path refuses while a turn is streaming (e.g. a manually
        // typed message is still replying) -- retry briefly instead of
        // dropping the user's spoken words.
        if (appState.isStreamingResponse && sendAttempts < SEND_MAX_ATTEMPTS) {
            sendAttempts++
            armSendTimer(SEND_RETRY_MS)
            return
        }
        sendAttempts = 0
        if (appState.isStreamingResponse) {
            accumulated = ""
            livePartial = ""
            voiceNotice = "Wendy is still replying -- your spoken message couldn't be sent. Try again in a moment."
            beginListenCycle(resetPartial = true)
            return
        }
        if (appState.backendMode != BackendMode.HERMES || appState.hermesSessionId == null) {
            // The ground shifted under the loop (backend switch, session
            // cleared). Stop honestly rather than sending cross-backend.
            accumulated = ""
            livePartial = ""
            voiceNotice = if (appState.hermesSessionId == null) {
                "Pick a Wendy session in Settings first -- hands-free voice shares the session with Telegram."
            } else {
                "Voice conversation runs in Hermes mode only."
            }
            stop()
            return
        }
        accumulated = ""
        livePartial = ""
        recognizer.destroy()
        phase = Phase.THINKING
        mainHandler.removeCallbacks(turnWatchdogRunnable)
        mainHandler.postDelayed(turnWatchdogRunnable, TURN_WATCHDOG_MS)
        appState.sendMessage(text)
    }

    // ---- Reply speaking ---------------------------------------------------

    /** Fired by [FestoAppState.onHermesTurnCompleted] when a Hermes turn
     * settles with final text (its own auto-sent turn, or a manually typed
     * one while the loop is active). */
    private fun onTurnFinal(finalText: String) {
        if (!active) return
        mainHandler.removeCallbacks(turnWatchdogRunnable)
        when (phase) {
            Phase.THINKING, Phase.SPEAKING -> {
                // The loop's own turn finished (or another reply landed
                // while still speaking) -- speak it; QUEUE_FLUSH replaces
                // anything queued.
                speakReply(finalText)
            }
            Phase.LISTENING -> {
                if (sendTimerPending && accumulated.isNotBlank()) {
                    // A manual typed turn finished while the user was
                    // mid-utterance with a send already armed: flush the
                    // user's spoken words first -- speaking the manual
                    // reply over the hot mic would clip it AND feed the
                    // recognizer its own audio.
                    cancelSendTimer()
                    recognizer.destroy()
                    flushAccumulatedNow()
                } else {
                    // A manual typed send finished while the loop merely
                    // listened: stop the mic, speak the reply, re-arm.
                    recognizer.destroy()
                    speakReply(finalText)
                }
            }
            Phase.IDLE -> return
        }
    }

    private fun speakReply(finalText: String) {
        val spoken = spokenText(finalText)
        if (muted || !ttsReady || spoken.isBlank()) {
            // Muted / engine not ready / nothing speakable (e.g. a pure
            // code-block reply): back to listening without a dead pause.
            beginListenCycle(resetPartial = true)
            return
        }
        phase = Phase.SPEAKING
        livePartial = ""
        val queued = try {
            // QUEUE_FLUSH: a new reply always replaces whatever is queued.
            tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (queued != TextToSpeech.SUCCESS) {
            // Engine refused the utterance -- keep the loop going without
            // speech instead of wedging in SPEAKING.
            beginListenCycle(resetPartial = true)
        }
    }

    private fun ensureTts() {
        if (tts != null || ttsInitFailed) return
        val engine = try {
            TextToSpeech(appContext, TextToSpeech.OnInitListener { status -> onTtsInit(status) })
        } catch (_: Exception) {
            null
        }
        if (engine == null) {
            announceTtsFailure()
            return
        }
        engine.setOnUtteranceProgressListener(ttsProgress)
        tts = engine
    }

    private fun onTtsInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            try {
                tts?.shutdown()
            } catch (_: Exception) {
            }
            tts = null
            announceTtsFailure()
            return
        }
        ttsReady = true
        // Best-effort voice pick: a LANG_MISSING_DATA / LANG_NOT_SUPPORTED
        // result still leaves most engines usable on their default voice;
        // a failed utterance just re-arms the loop without speech.
        try {
            tts?.setLanguage(Locale.getDefault())
        } catch (_: Exception) {
        }
    }

    private fun announceTtsFailure() {
        ttsInitFailed = true
        ttsReady = false
        if (!ttsFailureAnnounced) {
            ttsFailureAnnounced = true
            if (active) {
                // One-time chip: the loop degrades to dictation-only
                // (still listens and auto-sends; replies stay as text).
                voiceNotice = "Voice replies aren't available on this device -- the loop keeps listening and auto-sending, just without speaking."
            }
        }
    }

    // ---- Reply text -> speakable plain text -------------------------------

    /** Fenced code blocks -- dropped whole: reading raw code aloud is
     * noise, and it stays visible in the transcript bubble. Streaming-safe
     * (an unterminated fence runs to end of input), same rule the
     * renderer's fence parser uses while deltas arrive. */
    private val codeFenceRegex = Regex("```[a-zA-Z0-9_-]*\\s*\\n?[\\s\\S]*?(?:```|\\z)")

    /** `[text](url)` -- links keep their visible text; the URL is not
     * spoken. (Images are already gone by this point via the renderer's
     * [markdownImageRegex].) */
    private val markdownLinkRegex = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")

    /** Inline code `like this` -- keep the content, drop the backticks. */
    private val inlineCodeRegex = Regex("`([^`]*)`")

    /** MEDIA: <token> -- defensive: the gateway may inline media pointers
     * in reply text. */
    private val mediaTokenRegex = Regex("MEDIA:\\s*\\S+", RegexOption.IGNORE_CASE)

    /** Table separator rows: `| --- | :---: | --- |` (and stray hr rules). */
    private val tableSeparatorRegex = Regex("^\\s*\\|?[\\s:|-]+\\|?\\s*$")

    private val headingPrefixRegex = Regex("^\\s{0,3}#{1,6}\\s+")
    private val quotePrefixRegex = Regex("^\\s{0,3}>\\s?")
    private val bulletPrefixRegex = Regex("^\\s{0,3}[-*+]\\s+")
    private val orderedPrefixRegex = Regex("^\\s{0,3}\\d{1,3}\\.\\s+")

    /** Strips a reply's markdown down to something worth hearing: code
     * fences, image/audio tokens and link URLs disappear; bold/italic/
     * headers/quotes/bullets become plain words (links keep their text);
     * table rows become plain lines. Capped at ~[SPOKEN_CHAR_CAP] chars. */
    private fun spokenText(raw: String): String {
        var text = raw
        text = codeFenceRegex.replace(text, " ")
        // Images: never read a URL -- least of all a base64 one. The exact
        // matcher the transcript renderer uses, so TTS skips precisely the
        // tokens that render as image blocks.
        text = markdownImageRegex.replace(text, " ")
        text = markdownLinkRegex.replace(text, "$1")
        text = inlineCodeRegex.replace(text, "$1")
        text = mediaTokenRegex.replace(text, " ")
        // Audio file paths (the gateway's audio deliveries) -- the exact
        // matcher the renderer turns into audio chips.
        text = audioPathRegex.replace(text, " ")

        // Tables / lists / quotes / headers -> plain lines.
        text = text.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (tableSeparatorRegex.matches(trimmed)) {
                ""
            } else {
                trimmed.trimStart('|').trimEnd('|').replace('|', ',')
                    .replace(headingPrefixRegex, "")
                    .replace(quotePrefixRegex, "")
                    .replace(bulletPrefixRegex, "")
                    .replace(orderedPrefixRegex, "")
            }
        }

        // Emphasis markers (bold / italic / strikethrough). Applied AFTER
        // code and paths are gone, so identifiers never lose their shapes.
        text = text.replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace("*", "")
            .replace("_", "")

        text = text.replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\s*\\n\\s*"), "\n")
            .trim()

        if (text.length > SPOKEN_CHAR_CAP) {
            // Truncate on a word boundary when there is a sensible one.
            val cut = text.lastIndexOf(' ', SPOKEN_CHAR_CAP)
            text = text.substring(0, if (cut > SPOKEN_CHAR_CAP / 2) cut else SPOKEN_CHAR_CAP).trim()
        }
        return text
    }
}
