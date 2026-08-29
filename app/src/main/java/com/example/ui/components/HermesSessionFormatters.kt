package com.example.ui.components

import com.example.data.HermesSession

/** Formatting helpers shared by every surface that lists gateway
 * sessions (the Settings picker and the Wendy memory browser) so
 * "12 messages · 2h ago" reads identically in both places. */

fun hermesMessageCountLabel(count: Int): String =
    "$count ${if (count == 1) "message" else "messages"}"

/** Relative last-activity label, or null when too stale to be useful
 * (the title + count identify the session well enough by then). */
fun hermesRelativeTimeLabel(lastActivityAtMs: Long?): String? {
    if (lastActivityAtMs == null) return null
    val diffMs = System.currentTimeMillis() - lastActivityAtMs
    if (diffMs < 0) return "just now"
    val minutes = diffMs / 60_000
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        minutes < 60L * 24 -> "${minutes / 60}h ago"
        minutes < 60L * 24 * 30 -> "${minutes / (60 * 24)}d ago"
        else -> null
    }
}

fun hermesSessionSubtitle(session: HermesSession): String {
    val parts = mutableListOf(hermesMessageCountLabel(session.messageCount))
    hermesRelativeTimeLabel(session.lastActivityAtMs)?.let { parts.add(it) }
    if (!session.isTelegram && session.source != null) {
        parts.add("via ${session.source}")
    }
    return parts.joinToString(" · ")
}
