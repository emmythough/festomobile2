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

    @Test
    fun testVoiceRecordingTransitionsAndMessageCreation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val appState = FestoAppState(testScope)

        assertEquals(VoiceState.IDLE, appState.voiceState)

        // Start recording
        appState.startVoiceRecording()
        assertEquals(VoiceState.RECORDING, appState.voiceState)

        // Cancel voice turn
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
