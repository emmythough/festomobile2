package com.example.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import java.io.File

/**
 * Thin wrapper around Android's [MediaRecorder] (real mic capture) and
 * [MediaPlayer] (real audio playback). Kept separate from FestoAppState so
 * the UI/state machine stays pure and this can be swapped for a fake in
 * tests.
 *
 * Recording is captured as M4A (AAC in MPEG-4), which OpenRouter's STT
 * accepts directly. Playback plays raw MP3 bytes returned by the server's
 * TTS proxy.
 */
class VoiceAudioEngine(private val appContext: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    /** The single MediaPlayer instance; non-null only while speaking. */
    var player: MediaPlayer? = null
        private set

    var onPlaybackEnd: (() -> Unit)? = null

    /**
     * True while actively recording. Used by FestoAppState to drive the
     * RECORDING state and the live audio-level meter.
     */
    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Start capturing from the microphone into a fresh M4A file in the app
     * cache dir. Caller must have already obtained RECORD_AUDIO permission.
     */
    fun startRecording() {
        stopRecordingInternal(release = false)
        val dir = File(appContext.cacheDir, "voice")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val rec = MediaRecorder()
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioSamplingRate(16000)
        rec.setAudioEncodingBitRate(96000)
        rec.setOutputFile(file.absolutePath)
        try {
            rec.prepare()
            rec.start()
            recorder = rec
            isRecording = true
        } catch (e: Exception) {
            Log.e("VoiceAudioEngine", "startRecording failed", e)
            try { rec.release() } catch (_: Exception) {}
            recorder = null
            isRecording = false
        }
    }

    /**
     * Current microphone amplitude in 0..1 (or 0 if not recording). Drives
     * the live waveform visualizer with real data instead of fake bars.
     */
    fun currentLevel(): Float {
        val rec = recorder ?: return 0f
        if (!isRecording) return 0f
        return try {
            val amp = rec.maxAmplitude
            if (amp <= 0) 0.1f else (amp / 32767f).coerceIn(0.05f, 1f)
        } catch (_: Exception) {
            0.1f
        }
    }

    /**
     * Stop recording and return the captured audio as base64, or null if
     * nothing was captured. Caller then sends it to the STT endpoint.
     */
    fun stopRecordingAndGetBase64(): String? {
        val file = outputFile ?: run {
            stopRecordingInternal(release = true)
            return null
        }
        stopRecordingInternal(release = true)
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return null
        }
        val bytes = file.readBytes()
        file.delete()
        if (bytes.isEmpty()) return null
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Stop a recording and discard its bytes without returning them. */
    fun cancelRecording() {
        stopRecordingInternal(release = true)
        outputFile?.delete()
        outputFile = null
    }

    private fun stopRecordingInternal(release: Boolean) {
        isRecording = false
        val rec = recorder ?: return
        try {
            rec.stop()
        } catch (_: Exception) {
            // Called with no valid recording (e.g. too-short capture).
        }
        try {
            rec.reset()
            rec.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    /** Play raw MP3 bytes through the speaker/media stream. */
    fun playMp3(mp3Bytes: ByteArray) {
        stopPlayback()
        if (mp3Bytes.isEmpty()) {
            onPlaybackEnd?.invoke()
            return
        }
        val file = File(appContext.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
        file.writeBytes(mp3Bytes)
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setOnCompletionListener { onPlaybackEnd?.invoke() }
        mp.setOnErrorListener { _, _, _ ->
            onPlaybackEnd?.invoke()
            true
        }
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            Log.e("VoiceAudioEngine", "playMp3 failed", e)
            try { mp.release() } catch (_: Exception) {}
            player = null
            onPlaybackEnd?.invoke()
        }
    }

    /** Stop playback immediately (barge-in). */
    fun stopPlayback() {
        val mp = player ?: return
        player = null
        try {
            if (mp.isPlaying) mp.stop()
            mp.reset()
            mp.release()
        } catch (_: Exception) {
        }
    }

    fun release() {
        stopPlayback()
        cancelRecording()
    }
}
