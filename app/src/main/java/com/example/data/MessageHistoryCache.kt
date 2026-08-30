package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The transcript [MessageHistoryCache.load] returns, tagged with the
 * shared session it was saved for: a cache written under one Hermes
 * session must never be shown for a different one (the session picker
 * switches sessions, and Telegram keeps chatting into whichever the
 * gateway rotates to), so the session id rides along and the consumer
 * compares it against the currently-selected session before use. */
data class CachedHistory(
    val sessionId: String,
    val messages: List<Message>
)

/** Local on-disk cache of the shared Wendy conversation transcript,
 * stored as one JSON file under [Context.getFilesDir].
 *
 * Why it exists: FestoAppState is created via Compose `remember`, so it
 * survives in-app navigation but dies with the Android process -- and
 * process restarts (backgrounding, memory pressure, device reboot) are
 * the common case, not the exception. Every restart used to begin with a
 * blank chat and a spinner until the gateway fetch resolved. This cache
 * lets construction repopulate the transcript synchronously, before the
 * network call resolves, so a cold start shows the real conversation
 * with zero delay; the background fetch then reconciles against it (see
 * [reconcileHistory] in HistoryReconciliation.kt) and writes back here
 * only when something actually changed.
 *
 * Why a real file rather than SharedPreferences (the convention
 * BackendPreferences/ThemePreferences use): a full transcript is a
 * growing, unbounded document -- past a small handful of messages it
 * outgrows what SharedPreferences's XML backing handles well. One
 * flat file that is wholly rewritten on each save is exactly the right
 * shape for write-once-per-change data like this.
 *
 * Security note: the file contains ONLY message content plus the session
 * id. The Hermes API key and gateway URL never enter it -- those live in
 * BackendPreferences' SharedPreferences (festo_settings), and nothing in
 * the save() signature could carry them even by accident.
 *
 * Corruption policy: every read and write is guarded -- a truncated,
 * half-written, or otherwise unreadable file is treated as "no cache"
 * (load returns null) and never crashes startup. The next successful
 * gateway fetch repopulates the file, so a bad cache self-heals. */
class MessageHistoryCache(context: Context) {
    private val cacheFile: File = File(context.filesDir, CACHE_FILE_NAME)
    private val tmpFile: File = File(context.filesDir, "$CACHE_FILE_NAME.tmp")

    /** Reads the cached transcript for comparison against the currently
     * selected session. Null means "no usable cache" -- file absent,
     * unparseable, or missing its session id. Never throws. */
    fun load(): CachedHistory? = try {
        if (!cacheFile.exists()) {
            null
        } else {
            val obj = JSONObject(cacheFile.readText())
            val sessionId = obj.optString(KEY_SESSION_ID).ifBlank { null }
                ?: return null
            val array = obj.optJSONArray(KEY_MESSAGES) ?: return null
            val messages = ArrayList<Message>(array.length())
            for (i in 0 until array.length()) {
                val message = messageFromJson(array.optJSONObject(i) ?: continue)
                    ?: continue
                messages.add(message)
            }
            CachedHistory(sessionId, messages)
        }
    } catch (_: Exception) {
        // Anything wrong with the file (truncated write, JSON garbage,
        // unreadable) is "no cache" -- the network fetch is the source of
        // truth and will rewrite it.
        null
    }

    /** Persists the transcript for [sessionId], wholesale replacing the
     * previous file. Never throws: cache I/O must never take app startup
     * or a turn down with it. Writes through a sibling temp file first so
     * a crash mid-write can't leave a half-document behind (a corrupt
     * file would only cost us the instant-resume, but the rename keeps
     * that window near-zero). */
    fun save(sessionId: String, messages: List<Message>) {
        try {
            val array = JSONArray()
            for (message in messages) {
                array.put(messageToJson(message))
            }
            val doc = JSONObject()
                .put(KEY_SESSION_ID, sessionId)
                .put(KEY_MESSAGES, array)
            val text = doc.toString()
            tmpFile.writeText(text)
            if (cacheFile.exists()) cacheFile.delete()
            if (!tmpFile.renameTo(cacheFile)) {
                // renameTo is platform-picky about existing targets; the
                // direct write is the fallback, not the happy path.
                cacheFile.writeText(text)
                tmpFile.delete()
            }
        } catch (_: Exception) {
            // Same policy as load(): a failed cache write is silently
            // survivable -- the next reconciliation simply rewrites it.
        }
    }

    /** Message fields that carry real content are written; the transient
     * flags (isStreaming/isError) are deliberately NOT persisted -- a
     * cached transcript must never resurrect a streaming placeholder as
     * one, and reconstruction defaults both to false, which is correct
     * for resting messages. */
    private fun messageToJson(m: Message): JSONObject {
        val obj = JSONObject()
            .put("id", m.id)
            .put("conversation_id", m.conversationId)
            .put("role", m.role.name)
            .put("content", m.content)
            .put("timestamp", m.timestamp)
        // Optional fields are written only when present, so old files
        // stay parseable and absent values read back as null, not 0/"".
        m.model?.let { obj.put("model", it) }
        m.inputTokens?.let { obj.put("input_tokens", it) }
        m.outputTokens?.let { obj.put("output_tokens", it) }
        m.costUsd?.takeIf { !it.isNaN() }?.let { obj.put("cost_usd", it) }
        m.attachmentFilename?.let { obj.put("attachment_filename", it) }
        return obj
    }

    /** Inverse of [messageToJson]. Null (entry skipped) for entries with
     * an unparseable role -- a hand-edited or partially corrupt entry
     * costs one row, never the whole restore. */
    private fun messageFromJson(obj: JSONObject): Message? = try {
        Message(
            id = obj.optString("id"),
            conversationId = obj.optString("conversation_id"),
            role = Role.valueOf(obj.optString("role")),
            content = obj.optString("content"),
            model = obj.optString("model").ifBlank { null },
            timestamp = obj.optLong("timestamp"),
            inputTokens = optionalInt(obj, "input_tokens"),
            outputTokens = optionalInt(obj, "output_tokens"),
            costUsd = optionalDouble(obj, "cost_usd"),
            attachmentFilename = obj.optString("attachment_filename").ifBlank { null },
        )
    } catch (_: Exception) {
        null
    }

    /** org.json's optInt/optDouble collapse "absent" into 0/NaN, which
     * would corrupt a genuine 0-token usage row; these check presence
     * explicitly. */
    private fun optionalInt(obj: JSONObject, key: String): Int? =
        if (obj.has(key) && !obj.isNull(key)) obj.optInt(key) else null

    private fun optionalDouble(obj: JSONObject, key: String): Double? =
        if (obj.has(key) && !obj.isNull(key)) obj.optDouble(key) else null

    companion object {
        private const val CACHE_FILE_NAME = "hermes_history_cache.json"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_MESSAGES = "messages"
    }
}
