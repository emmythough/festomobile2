package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric tests for [MessageHistoryCache] -- the on-disk transcript
 * cache behind FestoAppState's instant, non-jarring resume. Robolectric
 * (not plain JUnit) because the cache reads and writes org.json, which
 * is stubbed out in plain local unit tests (same setup as
 * ExampleRobolectricTest).
 *
 * Contracts pinned here:
 * 1. Round-trip: writing a real message list to the real file under
 *    filesDir and reading it back reconstructs the exact Messages --
 *    including null-optional-field messages, a genuine 0-token usage row
 *    (which org.json's optInt would otherwise collapse to "absent"), and
 *    a photo attachment's display filename.
 * 2. Corruption policy: a missing, garbage, truncated, or wrong-shape
 *    cache file reads as "no cache" (null) and never throws -- a bad
 *    cache must never crash app startup.
 * 3. Secrets: the file contains only the session id and message fields.
 *    The API key and gateway URL never appear -- they live in
 *    BackendPreferences, and nothing in save()'s signature could carry
 *    them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessageHistoryCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val cache = MessageHistoryCache(context)

    private val cacheFile: File
        get() = File(context.filesDir, "hermes_history_cache.json")

    private fun fullyPopulatedMessage() = Message(
        id = "msg-abc12345",
        conversationId = "wendy-main",
        role = Role.ASSISTANT,
        content = "Reply with\nnewlines, \"quotes\", unicode \uD83D\uDE00, and `code`",
        model = "claude-sonnet-4-5",
        timestamp = 1_724_000_000_000,
        inputTokens = 1234,
        outputTokens = 567,
        costUsd = 0.0042,
    )

    private fun minimalMessage() = Message(
        id = "msg-plain000",
        conversationId = "wendy-main",
        role = Role.USER,
        content = "hi",
        timestamp = 1_724_000_001_000,
    )

    private fun photoMessage() = Message(
        id = "msg-photo000",
        conversationId = "wendy-main",
        role = Role.USER,
        content = "",
        timestamp = 1_724_000_002_000,
        attachmentFilename = "IMG_20260830.jpg",
    )

    private fun zeroUsageMessage() = Message(
        id = "msg-zerouse",
        conversationId = "wendy-main",
        role = Role.ASSISTANT,
        content = "echo",
        model = "gateway-model",
        timestamp = 1_724_000_003_000,
        inputTokens = 0,
        outputTokens = 0,
        costUsd = 0.0,
    )

    // ---- round trip ----

    @Test
    fun `writing then reading a message list round-trips through the real file`() {
        val original = listOf(
            fullyPopulatedMessage(),
            minimalMessage(),
            photoMessage(),
            zeroUsageMessage(),
        )
        cache.save("sess-1", original)
        assertTrue(cacheFile.exists())

        val restored = cache.load()
        assertEquals("sess-1", restored?.sessionId)
        assertEquals(original, restored?.messages)
    }

    @Test
    fun `round-tripped messages keep null optional fields null`() {
        // minimalMessage() has no model/tokens/cost/attachment: those must
        // come back as null, not 0 or "".
        cache.save("sess-1", listOf(minimalMessage()))
        val restored = cache.load()?.messages?.single()
        assertEquals(minimalMessage(), restored)
        assertNull(restored?.model)
        assertNull(restored?.inputTokens)
        assertNull(restored?.outputTokens)
        assertNull(restored?.costUsd)
        assertNull(restored?.attachmentFilename)
    }

    @Test
    fun `a genuine zero-token usage row survives as zero not absent`() {
        cache.save("sess-1", listOf(zeroUsageMessage()))
        val restored = cache.load()?.messages?.single()
        assertEquals(0, restored?.inputTokens)
        assertEquals(0, restored?.outputTokens)
        assertEquals(0.0, restored?.costUsd!!, 0.0)
    }

    @Test
    fun `a newer save replaces the previous one`() {
        cache.save("sess-1", listOf(minimalMessage()))
        val newer = listOf(fullyPopulatedMessage())
        cache.save("sess-1", newer)
        assertEquals(newer, cache.load()?.messages)
    }

    // ---- corruption policy ----

    @Test
    fun `a missing cache file reads as no cache`() {
        assertNull(cache.load())
    }

    @Test
    fun `a garbage cache file reads as no cache instead of crashing`() {
        cacheFile.writeText("this is definitely { not, json :: at all")
        assertNull(cache.load())
    }

    @Test
    fun `a truncated cache file reads as no cache instead of crashing`() {
        // Half a document: exactly what a crash mid-write leaves behind
        // without the temp-file rename.
        cacheFile.writeText("""{"session_id":"sess-1","messages":[{"id":"msg-1"""")
        assertNull(cache.load())
    }

    @Test
    fun `a wrong-shape cache file reads as no cache instead of crashing`() {
        // Parses as JSON but isn't the expected object shape.
        cacheFile.writeText("[1, 2, 3]")
        assertNull(cache.load())
    }

    @Test
    fun `an entry with an unknown role is dropped not fatal`() {
        val doc = JSONObject()
            .put("session_id", "sess-1")
            .put(
                "messages", JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "m1")
                            .put("conversation_id", "wendy-main")
                            .put("role", "moderator")
                            .put("content", "who wrote this?")
                            .put("timestamp", 1_000)
                    )
                    .put(
                        JSONObject()
                            .put("id", "m2")
                            .put("conversation_id", "wendy-main")
                            .put("role", "USER")
                            .put("content", "good row")
                            .put("timestamp", 2_000)
                    )
            )
        cacheFile.writeText(doc.toString())
        val restored = cache.load()?.messages
        assertEquals(listOf(minimalMessage().copy(id = "m2", content = "good row", timestamp = 2_000)), restored)
    }

    // ---- secrets contract ----

    @Test
    fun `the cache file contains only the session id and message fields`() {
        val apiKey = "sk-hermes-live-000000-secret-value"
        val gatewayUrl = "https://gateway.example-hermes.net"

        cache.save(
            "sess-1",
            listOf(fullyPopulatedMessage(), minimalMessage(), photoMessage())
        )
        val raw = cacheFile.readText()

        // Structural: exactly two top-level keys; every message object
        // carries only the reconstructable Message fields.
        val doc = JSONObject(raw)
        assertEquals(setOf("session_id", "messages"), doc.keys().asSequence().toSet())
        val allowedMessageKeys = setOf(
            "id", "conversation_id", "role", "content", "model", "timestamp",
            "input_tokens", "output_tokens", "cost_usd", "attachment_filename"
        )
        val messages = doc.getJSONArray("messages")
        for (i in 0 until messages.length()) {
            val keys = messages.getJSONObject(i).keys().asSequence().toSet()
            assertTrue("unexpected keys $keys", allowedMessageKeys.containsAll(keys))
        }

        // Literal: the API key and gateway URL live in BackendPreferences
        // and must never land in this file.
        assertFalse(raw.contains(apiKey))
        assertFalse(raw.contains(gatewayUrl))
    }
}
