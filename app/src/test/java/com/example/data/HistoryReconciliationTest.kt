package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [reconcileHistory] and [historyMessagesFromEntries] --
 * the pure diff logic behind loadHermesHistory's non-destructive
 * reconciliation. No network, no Robolectric: these are plain functions
 * over List<Message>.
 *
 * Contracts pinned here:
 * 1. An identical fetch is [HistoryReconciliation.Unchanged] even when
 *    ids and timestamps differ across fetches -- the cached cold start
 *    must leave the displayed list (and `messages.size`) untouched so
 *    ChatScreen's scroll effect has nothing to react to.
 * 2. A fetch that only extends the displayed history is
 *    [HistoryReconciliation.AppendTail] carrying exactly the new tail.
 * 3. Anything else -- mid-list divergence, role change, shorter fetch,
 *    empty fetch over a non-empty list -- is
 *    [HistoryReconciliation.Rebuild], the historical clear+rebuild path.
 * 4. Identity is (role, content) matched by POSITION: HermesHistoryEntry
 *    carries no server id, "hmsg-$index" ids are positional, locally-sent
 *    messages keep their UUID ids when the gateway stores them, and
 *    timestamps are unstable (per-fetch fallback for missing created_at,
 *    millisecond drift for locally-streamed sends).
 */
class HistoryReconciliationTest {

    private fun userMsg(id: String, content: String, ts: Long) =
        Message(
            id = id,
            conversationId = "wendy-main",
            role = Role.USER,
            content = content,
            timestamp = ts
        )

    private fun assistantMsg(id: String, content: String, ts: Long) =
        Message(
            id = id,
            conversationId = "wendy-main",
            role = Role.ASSISTANT,
            content = content,
            timestamp = ts
        )

    // ---- identical ----

    @Test
    fun `identical history is Unchanged even when ids and timestamps differ`() {
        // Cached restore produces the previous fetch's ids/timestamps; the
        // new fetch regenerates them. Same role+content per position must
        // still be Unchanged -- this is the case that keeps a no-change
        // cold start from re-firing the scroll animation.
        val current = listOf(
            userMsg("hmsg-0", "Morning!", 1_724_000_000_000),
            assistantMsg("hmsg-1", "Good morning", 1_724_000_000_500)
        )
        val fetched = listOf(
            userMsg("hmsg-0", "Morning!", 1_724_000_000_001),
            assistantMsg("hmsg-1", "Good morning", 1_724_000_000_501)
        )
        assertEquals(HistoryReconciliation.Unchanged, reconcileHistory(current, fetched))
    }

    @Test
    fun `identical history with uuid ids from local sends is still Unchanged`() {
        // A turn sent from this app: the local copies keep their UUID ids
        // and send-time timestamps; the gateway stores the same texts
        // under generated ids and its own created_at.
        val current = listOf(
            userMsg("hmsg-0", "ping", 1_724_000_000_000),
            assistantMsg("hmsg-1", "pong", 1_724_000_000_100),
            userMsg("msg-1a2b3c4d", "hello from the app", 1_724_000_050_000),
            assistantMsg("msg-9f8e7d6c", "hello! how can I help?", 1_724_000_050_800)
        )
        val fetched = listOf(
            userMsg("hmsg-0", "ping", 1_724_000_000_000),
            assistantMsg("hmsg-1", "pong", 1_724_000_000_100),
            userMsg("hmsg-2", "hello from the app", 1_724_000_050_005),
            assistantMsg("hmsg-3", "hello! how can I help?", 1_724_000_050_812)
        )
        assertEquals(HistoryReconciliation.Unchanged, reconcileHistory(current, fetched))
    }

    @Test
    fun `both lists empty is Unchanged`() {
        assertEquals(
            HistoryReconciliation.Unchanged,
            reconcileHistory(emptyList(), emptyList())
        )
    }

    // ---- appended tail ----

    @Test
    fun `new messages at the end append only the tail`() {
        val current = listOf(
            userMsg("hmsg-0", "Morning!", 1_000),
            assistantMsg("hmsg-1", "Good morning", 1_100)
        )
        val tail = listOf(
            userMsg("hmsg-2", "Anything on today?", 2_000),
            assistantMsg("hmsg-3", "One meeting at 3pm.", 2_100)
        )
        val result = reconcileHistory(current, current + tail)
        assertEquals(HistoryReconciliation.AppendTail(tail), result)
    }

    @Test
    fun `append onto an empty displayed list carries the whole fetch as the tail`() {
        // Cold start with no cache yet: everything the gateway returns is
        // "new", appended into the empty list.
        val fetched = listOf(
            userMsg("hmsg-0", "First", 1_000),
            assistantMsg("hmsg-1", "Second", 1_100)
        )
        assertEquals(
            HistoryReconciliation.AppendTail(fetched),
            reconcileHistory(emptyList(), fetched)
        )
    }

    @Test
    fun `duplicate texts are matched positionally not aliased`() {
        // Three identical user bubbles: a content-keyed diff would alias
        // them; positional matching counts positions.
        val current = listOf(
            userMsg("hmsg-0", "ok", 1_000),
            userMsg("hmsg-1", "ok", 2_000)
        )
        val fetched = current + listOf(userMsg("hmsg-2", "ok", 3_000))
        assertEquals(
            HistoryReconciliation.AppendTail(listOf(userMsg("hmsg-2", "ok", 3_000))),
            reconcileHistory(current, fetched)
        )
    }

    // ---- diverged ----

    @Test
    fun `a mid-list content difference is Rebuild even with a new tail`() {
        val current = listOf(
            userMsg("hmsg-0", "Morning!", 1_000),
            assistantMsg("hmsg-1", "Good morning", 1_100)
        )
        val fetched = listOf(
            userMsg("hmsg-0", "Morning!", 1_000),
            assistantMsg("hmsg-1", "EDITED reply", 1_100),
            userMsg("hmsg-2", "New tail", 2_000)
        )
        assertEquals(HistoryReconciliation.Rebuild, reconcileHistory(current, fetched))
    }

    @Test
    fun `a role change at the same position is Rebuild`() {
        val current = listOf(
            userMsg("hmsg-0", "Morning!", 1_000),
            assistantMsg("hmsg-1", "Good morning", 1_100)
        )
        val fetched = listOf(
            userMsg("hmsg-0", "Morning!", 1_000),
            userMsg("hmsg-1", "Good morning", 1_100)
        )
        assertEquals(HistoryReconciliation.Rebuild, reconcileHistory(current, fetched))
    }

    @Test
    fun `a shorter fetch than displayed is Rebuild`() {
        // Gateway compacted/trimmed history, a session switch, or a
        // failed fetch (fetchMessages returns emptyList on any failure).
        val current = listOf(
            userMsg("hmsg-0", "A", 1_000),
            assistantMsg("hmsg-1", "B", 1_100),
            userMsg("hmsg-2", "C", 1_200)
        )
        assertEquals(
            HistoryReconciliation.Rebuild,
            reconcileHistory(current, current.take(2))
        )
    }

    @Test
    fun `an empty fetch over a non-empty displayed list is Rebuild`() {
        val current = listOf(userMsg("hmsg-0", "A", 1_000))
        assertEquals(
            HistoryReconciliation.Rebuild,
            reconcileHistory(current, emptyList())
        )
    }

    // ---- entry mapping ----

    @Test
    fun `historyMessagesFromEntries maps roles and skips tool and system rows`() {
        val entries = listOf(
            HermesHistoryEntry(role = "user", content = "Question", createdAtMs = 1_000),
            HermesHistoryEntry(role = "assistant", content = "Answer", createdAtMs = 1_100),
            HermesHistoryEntry(role = "tool", content = "internal plumbing", createdAtMs = 1_050),
            HermesHistoryEntry(role = "system", content = "more plumbing", createdAtMs = 1_060),
        )
        val messages = historyMessagesFromEntries(entries, "wendy-main")
        assertEquals(2, messages.size)
        assertEquals(Role.USER, messages[0].role)
        assertEquals("Question", messages[0].content)
        // Ids follow the historical entry-index scheme: the index is the
        // position in the FETCHED list, not the filtered one.
        assertEquals("hmsg-0", messages[0].id)
        assertEquals(Role.ASSISTANT, messages[1].role)
        assertEquals("hmsg-1", messages[1].id)
        assertEquals(1_100, messages[1].timestamp)
        assertEquals("wendy-main", messages[0].conversationId)
    }

    @Test
    fun `entries without a parseable timestamp fall back to now`() {
        val entries = listOf(
            HermesHistoryEntry(role = "user", content = "No timestamp here", createdAtMs = null),
        )
        val before = System.currentTimeMillis()
        val messages = historyMessagesFromEntries(entries, "wendy-main")
        assertEquals(1, messages.size)
        assertEquals("No timestamp here", messages[0].content)
        assert(messages[0].timestamp >= before)
    }
}
