package com.example

import androidx.compose.runtime.mutableStateListOf
import com.example.data.FestoAppState
import com.example.data.Modality
import com.example.data.Role
import com.example.data.VoiceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineTest {

    /**
     * This test previously asserted startVoiceRecording() moves straight
     * to RECORDING, but FestoAppState(testScope) here has no real
     * VoiceAudioEngine (it wraps MediaRecorder -- not constructible in a
     * plain JVM unit test without Robolectric) and no granted mic
     * permission. That assertion was failing -- not testing a real
     * behavior, testing a state that guards introduced after this test
     * was written correctly prevent. Rewritten to verify what actually
     * happens now, which is a real, worth-having safety property: no
     * engine or no permission means recording never silently starts.
     */
    @Test
    fun testVoiceRecordingRefusesWithoutEngineOrPermission() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val appState = FestoAppState(testScope)

        assertEquals(VoiceState.IDLE, appState.voiceState)

        // No audioEngine and no granted permission -- must refuse, not crash.
        appState.startVoiceRecording()
        assertEquals(VoiceState.IDLE, appState.voiceState)

        // Granting permission alone still isn't enough without a real engine.
        appState.onMicPermissionResult(true)
        appState.startVoiceRecording()
        assertEquals(VoiceState.IDLE, appState.voiceState)

        // cancelVoiceTurn() on an already-IDLE state must be a harmless no-op.
        appState.cancelVoiceTurn()
        assertEquals(VoiceState.IDLE, appState.voiceState)
    }

    @Test
    fun testBargeInPlaybackInterruption() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val appState = FestoAppState(testScope)

        appState.voiceState = VoiceState.SPEAKING
        appState.voiceLiveTranscript = "Speaking some audio..."

        appState.bargeInStopPlayback()

        assertEquals(VoiceState.IDLE, appState.voiceState)
        assertEquals("", appState.voiceLiveTranscript)
    }
}
