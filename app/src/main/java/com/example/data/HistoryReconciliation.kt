package com.example.data

/** Outcome of reconciling a freshly fetched Hermes transcript against
 * the transcript currently displayed. Plain data over List<Message> --
 * deliberately not buried inside the suspend network-calling function so
 * the diff rules are directly unit-testable. */
sealed class HistoryReconciliation {
    /** Fetched history is content-identical to what's shown (the cached
     * cold-start path). The displayed list must be left completely
     * alone -- same elements, same size, same object identity -- so
     * `messages.size` never changes and ChatScreen's scroll-to-bottom
     * LaunchedEffect (keyed on `messages.size`) has nothing to react to.
     * Re-adding identical rows would restart the same scroll animation
     * the cache exists to prevent. */
    object Unchanged : HistoryReconciliation()

    /** The fetch extends what's shown by [messages] at the end -- the
     * real, common case (something arrived via Telegram while the app
     * was closed). [messages] contains ONLY the new tail entries; the
     * caller appends them without touching existing rows. Size grows,
     * which is the one case where scrolling to the new bottom is
     * actually correct. */
    data class AppendTail(val messages: List<Message>) : HistoryReconciliation()

    /** No meaningful diff exists -- a session switch, gateway compaction,
     * or an empty fetch (fetchMessages returns emptyList on ANY failure,
     * including transient network errors, and a blank chat is its
     * documented fallback). The caller falls back to the historical
     * clear-then-rebuild behavior. */
    object Rebuild : HistoryReconciliation()
}

/** Diffs the currently displayed transcript against the freshly fetched
 * one by position.
 *
 * Why (role, content) and not id: HermesHistoryEntry carries NO
 * server-side id (checked HermesApi.parseHistory -- role, content,
 * createdAtMs only), so the "hmsg-$index" ids are purely positional;
 * they rotate whenever the gateway trims or compacts history, and a
 * message this app sent locally (UUID id) comes back from the gateway
 * under a generated id. The identity that survives a refetch is the
 * message's role and content.
 *
 * Why not timestamp either: entries whose created_at the gateway didn't
 * parse get a per-fetch System.currentTimeMillis() fallback, and
 * locally-streamed messages carry send-time millis that differ from the
 * gateway's stored created_at by milliseconds -- comparing timestamps
 * would turn byte-identical history into a false "diverged" and undo
 * the whole non-jarring-resume fix. Positional comparison also handles
 * duplicate texts ("ok" / "ok") correctly, where a content-keyed
 * multiset diff would alias them.
 *
 * Current is required to be a strict prefix of fetched (role and content
 * per position). Anything else -- mismatch mid-list, or fetched shorter
 * than current -- is [HistoryReconciliation.Rebuild]. */
fun reconcileHistory(
    current: List<Message>,
    fetched: List<Message>
): HistoryReconciliation {
    // The displayed list can never legitimately shrink on a refetch of
    // the same session -- a shorter fetch means trimmed/compacted
    // history, a session switch, or a failed fetch. Rebuild covers all.
    if (fetched.size < current.size) return HistoryReconciliation.Rebuild
    for (i in current.indices) {
        val shown = current[i]
        val fresh = fetched[i]
        if (shown.role != fresh.role || shown.content != fresh.content) {
            return HistoryReconciliation.Rebuild
        }
    }
    return if (fetched.size == current.size) {
        HistoryReconciliation.Unchanged
    } else {
        HistoryReconciliation.AppendTail(fetched.drop(current.size))
    }
}

/** Maps a fetched transcript into display [Message]s, skipping tool and
 * system rows (they're Wendy's plumbing, not conversation bubbles).
 * Ids keep the historical entry-index scheme ("hmsg-$index", where
 * index is the position in the FETCHED entry list, not the filtered
 * one). Entries without a parseable created_at fall back to "now" --
 * same fallback loadHermesHistory has always used for display. */
fun historyMessagesFromEntries(
    entries: List<HermesHistoryEntry>,
    conversationId: String
): List<Message> = entries.mapIndexedNotNull { index, entry ->
    val role = when (entry.role) {
        "user" -> Role.USER
        "assistant" -> Role.ASSISTANT
        else -> return@mapIndexedNotNull null
    }
    Message(
        id = "hmsg-$index",
        conversationId = conversationId,
        role = role,
        content = entry.content,
        timestamp = entry.createdAtMs ?: System.currentTimeMillis(),
    )
}
