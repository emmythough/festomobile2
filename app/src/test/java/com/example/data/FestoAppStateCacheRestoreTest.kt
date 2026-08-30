package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric tests for FestoAppState's synchronous cache restore in its
 * init block -- the mechanism behind the instant, non-jarring cold start.
 *
 * The scope is a [StandardTestDispatcher] that is deliberately NEVER
 * advanced: the init block's loadHermesHistory() coroutine stays queued,
 * so these tests prove the restore happens from the cache BEFORE any
 * network runs, with no gateway call attempted. (If a fetch ever did run
 * and fail in this environment, reconciliation would Rebuild the restored
 * rows away and the content assertions below would fail loudly.)
 *
 * Contracts pinned here:
 * 1. A cache matching the currently selected session repopulates
 *    activeMessages synchronously during construction and clears
 *    isHistoryLoading -- zero spinner, zero delay.
 * 2. A cache written for a DIFFERENT session is not shown for the
 *    current one; the spinner stays until the fetch settles.
 * 3. A corrupt cache file does not crash construction and falls back
 *    cleanly to the normal network path.
 * 4. No cache file at all behaves exactly like the pre-cache world.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FestoAppStateCacheRestoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun buildState(
        backendPrefs: BackendPreferences,
        historyCache: MessageHistoryCache?
    ): FestoAppState =
        FestoAppState(
            CoroutineScope(StandardTestDispatcher()), // never advanced: no fetch runs
            backendPrefs = backendPrefs,
            historyCache = historyCache
        )

    private fun cachedTranscript() = listOf(
        Message(
            id = "hmsg-0",
            conversationId = "wendy-main",
            role = Role.USER,
            content = "Cached question",
            timestamp = 1_724_000_000_000
        ),
        Message(
            id = "hmsg-1",
            conversationId = "wendy-main",
            role = Role.ASSISTANT,
            content = "Cached answer",
            model = "gateway-model",
            timestamp = 1_724_000_000_500,
            inputTokens = 10,
            outputTokens = 20,
            costUsd = 0.001
        )
    )

    @Test
    fun `a cache matching the picked session restores before any network runs`() {
        val prefs = BackendPreferences(context).apply { saveHermesSessionId("sess-1") }
        val cache = MessageHistoryCache(context)
        val transcript = cachedTranscript()
        cache.save("sess-1", transcript)

        val state = buildState(prefs, cache)

        // Synchronously present at construction time -- not after a fetch.
        assertEquals(transcript, state.activeMessages)
        assertFalse(state.isHistoryLoading)
    }

    @Test
    fun `a cache written for a different session is not shown for the current one`() {
        val prefs = BackendPreferences(context).apply { saveHermesSessionId("sess-current") }
        val cache = MessageHistoryCache(context)
        cache.save("sess-other", cachedTranscript())

        val state = buildState(prefs, cache)

        assertTrue(state.activeMessages.isEmpty())
        assertTrue(state.isHistoryLoading) // spinner until the real fetch settles
    }

    @Test
    fun `a corrupt cache file does not crash construction and falls back cleanly`() {
        val prefs = BackendPreferences(context).apply { saveHermesSessionId("sess-1") }
        val cache = MessageHistoryCache(context)
        File(context.filesDir, "hermes_history_cache.json")
            .writeText("{{ not json at all")

        val state = buildState(prefs, cache)

        assertTrue(state.activeMessages.isEmpty())
        assertTrue(state.isHistoryLoading) // normal network path proceeds
    }

    @Test
    fun `no cache file behaves exactly like the pre-cache world`() {
        val prefs = BackendPreferences(context).apply { saveHermesSessionId("sess-1") }

        val state = buildState(prefs, MessageHistoryCache(context))

        assertTrue(state.activeMessages.isEmpty())
        assertTrue(state.isHistoryLoading)
    }

    @Test
    fun `no session picked means nothing to restore and no cache touched`() {
        // Fresh prefs: no hermes_session_id. Construction must not read or
        // require a cache (and loadHermesHistory's queued body would early
        // out without any fetch if it ever ran).
        val state = buildState(BackendPreferences(context), MessageHistoryCache(context))

        assertTrue(state.activeMessages.isEmpty())
        assertTrue(state.isHistoryLoading)
    }
}
