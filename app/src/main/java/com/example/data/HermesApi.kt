package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** One live Hermes turn. Mirrors the shape of WendyEvent (full text-so-far
 * on Delta, one Final-or-Error per turn) so FestoAppState's streaming
 * plumbing treats both backends the same, plus two Hermes-only events:
 * ToolActivity (tool.* progress lines) and SessionRotated (the gateway
 * may rotate the session id on context compression -- run.completed's
 * session_id is authoritative and must be persisted). */
sealed class HermesEvent {
    /** Full reply text so far, NOT an increment -- always replace the
     * displayed bubble with this (same contract as WendyEvent.Delta). */
    data class Delta(val textSoFar: String) : HermesEvent()

    /** tool.started / tool.progress / tool.completed / tool.failed --
     * shown as an activity line, never as chat content. `detail` may be
     * blank. */
    data class ToolActivity(val toolName: String, val detail: String) : HermesEvent()

    /** End of a successful turn. `usage` carries only what the gateway
     * actually reported -- never estimated. */
    data class Completed(val text: String, val usage: ServerUsage? = null) : HermesEvent()

    /** run.completed's authoritative session_id -- may differ from the
     * one the turn started on (compression rotates it). Callers must
     * persist it as the current session id. */
    data class SessionRotated(val sessionId: String) : HermesEvent()

    data class Error(val message: String) : HermesEvent()
}

/** A tool surfacing live activity during a Hermes turn (tool.* events). */
data class ToolActivity(val toolName: String, val detail: String)

/** One entry of GET /api/sessions -- listed in Settings so the user can
 * pick the ONE session the app shares with Telegram. `source` says which
 * surface created it (e.g. "telegram"); isTelegram() flags the natural
 * pick. `lastActivityAtMs` is null when the server omitted/unparseable. */
data class HermesSession(
    val id: String,
    val title: String,
    val messageCount: Int,
    val lastActivityAtMs: Long?,
    val source: String?,
    val preview: String?
) {
    val isTelegram: Boolean
        get() = source?.contains("telegram", ignoreCase = true) == true
}

/** Result of GET /api/sessions. A sealed result instead of an empty list
 * because "gateway unreachable" (wrong URL/key -- the user is literally
 * editing those fields) must render differently in Settings from "no
 * sessions yet", same reasoning as OutboxDownload. */
sealed class HermesSessionsResult {
    data class Ready(val sessions: List<HermesSession>) : HermesSessionsResult()
    data class Failed(val message: String? = null) : HermesSessionsResult()
}

/** One turn from GET /api/sessions/{id}/messages -- role is
 * "user"/"assistant"/"tool"; the transcript renders user+assistant and
 * skips tool rows (they are Wendy's plumbing, not conversation bubbles). */
data class HermesHistoryEntry(val role: String, val content: String, val createdAtMs: Long?)

/** Client for the Hermes gateway -- the backend Telegram's Wendy brain
 * runs behind. Shared-session mode is the whole point: the app chats
 * inside ONE gateway session that Telegram also uses, so both surfaces
 * see one continuous conversation.
 *
 * Endpoints (verified against the gateway source):
 *   GET  /api/sessions                      -- session list
 *   GET  /api/sessions/{id}/messages       -- message history
 *   POST /api/sessions/{id}/chat/stream     -- SSE chat turn (the main one)
 *   POST /api/sessions/{id}/chat            -- single-shot JSON fallback
 *
 * Streaming frames are SSE: `event: <name>` + `data: <json>` + blank
 * line, CRLF or LF, with a raw `data: [DONE]` terminator. Parsed by the
 * small reader below (multiline data: lines joined with \n, comment and
 * unknown fields ignored) -- no SSE library dependency added.
 *
 * Security note: plaintext HTTP with a bearer key, same tradeoff already
 * accepted for the Gen 1 backend (see WendyApi.kt). Cleartext to the
 * default gateway IP is explicitly allowed in network_security_config.xml;
 * a user-entered http:// URL to any OTHER host will be blocked by
 * Android's cleartext policy until that host is allowlisted too. */
object HermesApi {
    const val DEFAULT_BASE_URL = "http://167.233.226.174:8642"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // a streaming turn can legitimately run minutes (tool use)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun base(baseUrl: String) = baseUrl.trim().trimEnd('/')

    private fun authed(url: String, apiKey: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")

    /** POST /api/sessions/{id}/chat/stream -- the main chat turn. Emits
     * Delta events as the reply streams in (full text so far each time),
     * ToolActivity for tool.* frames, one Completed (with usage when the
     * gateway reports it) and zero or more SessionRotated before
     * completing. If the gateway answers the streaming request with a
     * plain JSON document instead of an event stream, that single-shot
     * reply (choices[0].message.content / content) is parsed instead --
     * same contract, no streaming. Never throws; failures arrive as
     * HermesEvent.Error. */
    fun streamChat(
        baseUrl: String,
        apiKey: String,
        sessionId: String,
        message: String,
        sessionKey: String? = null,
    ): Flow<HermesEvent> = callbackFlow {
        val url = "${base(baseUrl)}/api/sessions/" +
            java.net.URLEncoder.encode(sessionId, "UTF-8") + "/chat/stream"
        val body = JSONObject().put("message", message).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = authed(url, apiKey).post(body)
        if (!sessionKey.isNullOrBlank()) {
            builder.header("X-Hermes-Session-Key", sessionKey)
        }
        val request = builder.build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(HermesEvent.Error(e.message ?: "network error"))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                readEventStream(response, this@callbackFlow)
                close()
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun readEventStream(response: Response, scope: ProducerScope<HermesEvent>) {
        response.use { resp ->
            if (!resp.isSuccessful) {
                scope.trySend(HermesEvent.Error(errorDetail(resp) ?: "HTTP ${resp.code}"))
                return
            }
            val contentType = resp.header("Content-Type") ?: ""
            if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                // The gateway answered with a single JSON document instead
                // of an event stream (the /chat non-streaming shape) --
                // honor it rather than reporting a parse failure.
                val bodyText = try {
                    resp.body?.string()
                } catch (_: Exception) {
                    null
                }
                val reply = bodyText?.let { extractReplyText(it) }
                if (reply.isNullOrBlank()) {
                    scope.trySend(HermesEvent.Error("unexpected response from the Hermes gateway"))
                } else {
                    scope.trySend(HermesEvent.Delta(reply))
                    scope.trySend(HermesEvent.Completed(reply))
                }
                return
            }
            val source = resp.body?.source() ?: run {
                scope.trySend(HermesEvent.Error("empty response body"))
                return
            }

            val accumulated = StringBuilder()
            var finalText = ""
            var finalUsage: ServerUsage? = null
            var errorMessage: String? = null
            var terminal = false

            fun handleFrame(eventName: String, data: String) {
                if (terminal) return
                if (data.trim() == "[DONE]") {
                    terminal = true
                    return
                }
                val obj = try {
                    JSONObject(data)
                } catch (_: Exception) {
                    null // malformed frame -- skip rather than kill the stream
                }
                when (eventName) {
                    "assistant.delta" -> {
                        val delta = obj?.optString("delta") ?: ""
                        if (delta.isNotEmpty()) {
                            accumulated.append(delta)
                            scope.trySend(HermesEvent.Delta(accumulated.toString()))
                        }
                    }
                    // tool.progress is the expected one; started/completed/
                    // failed carry similar payloads -- all show as activity.
                    "tool.progress", "tool.started", "tool.completed", "tool.failed" -> {
                        if (obj != null) {
                            val toolName = obj.optString("tool_name").ifBlank { "Wendy" }
                            val detail = obj.optString("delta").ifBlank { obj.optString("message") }
                            scope.trySend(HermesEvent.ToolActivity(toolName, detail))
                        }
                    }
                    "assistant.completed" -> {
                        val content = obj?.optString("content") ?: ""
                        if (content.isNotBlank()) {
                            finalText = content
                            // Authoritative final text -- replace the
                            // streamed bubble with it (deltas can drop).
                            scope.trySend(HermesEvent.Delta(content))
                        }
                    }
                    "run.completed" -> {
                        val sessionId = obj?.optString("session_id") ?: ""
                        if (sessionId.isNotBlank()) {
                            scope.trySend(HermesEvent.SessionRotated(sessionId))
                        }
                        parseUsage(obj?.optJSONObject("usage"))?.let { finalUsage = it }
                        if (finalText.isBlank()) {
                            finalText = lastAssistantContent(obj?.optJSONArray("messages"))
                        }
                    }
                    "error" -> {
                        val message = obj?.optString("message")?.ifBlank { null }
                            ?: "Wendy hit an error on the gateway"
                        errorMessage = message
                        scope.trySend(HermesEvent.Error(message))
                    }
                    "done" -> terminal = true
                    else -> {
                        // run.started / message.started / anything unknown:
                        // display-wise no-ops.
                    }
                }
            }

            var eventName = ""
            val dataLines = mutableListOf<String>()

            fun endOfFrame() {
                if (dataLines.isEmpty()) {
                    eventName = ""
                    return
                }
                val data = dataLines.joinToString("\n")
                val name = eventName
                dataLines.clear()
                eventName = ""
                handleFrame(name, data)
            }

            while (!source.exhausted() && !terminal) {
                val line = source.readUtf8Line() ?: break // readUtf8Line strips both LF and CRLF
                when {
                    line.isBlank() -> endOfFrame()
                    line.startsWith(":") -> { /* SSE comment / keepalive */ }
                    line.startsWith("event:") -> eventName = line.substring("event:".length).trim()
                    line.startsWith("data:") -> dataLines.add(line.substring("data:".length).removePrefix(" "))
                    // id:/retry:/anything else -- irrelevant here
                    else -> {}
                }
            }
            if (!terminal && dataLines.isNotEmpty()) endOfFrame() // trailing frame with no closing blank line

            // Finalize the turn exactly once, even if the gateway never
            // sent assistant.completed/run.completed (dropped connection
            // after deltas) -- the streamed text still stands.
            val reply = finalText.ifBlank { accumulated.toString() }
            if (reply.isNotBlank()) {
                scope.trySend(HermesEvent.Completed(reply, finalUsage))
            } else {
                scope.trySend(HermesEvent.Error(errorMessage ?: "Wendy's reply ended without any content"))
            }
        }
    }

    /** Lists the gateway's sessions so the user can pick the one Telegram
     * is using. Never throws; a Failed result carries a short reason. */
    suspend fun fetchSessions(baseUrl: String, apiKey: String): HermesSessionsResult =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(authed("${base(baseUrl)}/api/sessions", apiKey).get().build())
                    .execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext HermesSessionsResult.Failed("HTTP ${response.code}")
                        }
                        val body = response.body?.string()
                            ?: return@withContext HermesSessionsResult.Failed("empty response body")
                        HermesSessionsResult.Ready(parseSessions(body))
                    }
            } catch (e: Exception) {
                HermesSessionsResult.Failed(e.message)
            }
        }

    /** Fetches the shared session's transcript. Empty list on any failure
     * (no session chosen, transient network error) rather than throwing --
     * a blank chat screen is a fine fallback, same convention as
     * WendyApi.fetchHistory(). */
    suspend fun fetchMessages(baseUrl: String, apiKey: String, sessionId: String): List<HermesHistoryEntry> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(sessionId, "UTF-8")
                client.newCall(authed("${base(baseUrl)}/api/sessions/$encoded/messages", apiKey).get().build())
                    .execute().use { response ->
                        if (!response.isSuccessful) return@withContext emptyList()
                        val body = response.body?.string() ?: return@withContext emptyList()
                        parseHistory(body)
                    }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** Parses `{"data":[...]}` or a bare array (also accepts
     * `sessions`/`messages`/`items` keys defensively). */
    private fun unwrapArray(body: String, keys: List<String>): JSONArray? {
        val trimmed = body.trim()
        if (trimmed.startsWith("[")) {
            return try {
                JSONArray(trimmed)
            } catch (_: Exception) {
                null
            }
        }
        val obj = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return null
        }
        for (key in keys) {
            val arr = obj.optJSONArray(key)
            if (arr != null) return arr
        }
        return null
    }

    private fun parseSessions(body: String): List<HermesSession> {
        val array = unwrapArray(body, listOf("data", "sessions", "items")) ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id")
            if (id.isBlank()) return@mapNotNull null
            HermesSession(
                id = id,
                title = obj.optString("title").ifBlank { "Untitled session" },
                messageCount = optInt(obj, "message_count", "messageCount"),
                lastActivityAtMs = parseTimestamp(obj.opt("last_activity_at")),
                source = obj.optString("source").ifBlank { null },
                preview = obj.optString("preview").ifBlank { null },
            )
        }
    }

    private fun parseHistory(body: String): List<HermesHistoryEntry> {
        val array = unwrapArray(body, listOf("data", "messages")) ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            HermesHistoryEntry(
                role = obj.optString("role").trim().lowercase(Locale.US),
                content = readTextContent(obj),
                createdAtMs = parseTimestamp(obj.opt("created_at") ?: obj.opt("timestamp")),
            )
        }
    }

    /** `content` is normally a string; if the gateway ever stores
     * OpenAI-style parts (or a single {"text": ...} object), flatten to
     * plain text instead of rendering raw JSON. */
    private fun readTextContent(obj: JSONObject): String {
        return when (val raw = obj.opt("content")) {
            null -> ""
            is String -> raw
            is JSONObject -> raw.optString("text").ifBlank { raw.optString("content") }
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until raw.length()) {
                    when (val part = raw.opt(i)) {
                        is String -> sb.append(part)
                        is JSONObject -> sb.append(part.optString("text").ifBlank { part.optString("content") })
                        else -> {}
                    }
                }
                sb.toString()
            }
            else -> raw.toString()
        }
    }

    /** The single-shot fallback shape: `choices[0].message.content`, then
     * bare `content`, then `message` -- whichever actually carries text. */
    private fun extractReplyText(bodyText: String): String? = try {
        val obj = JSONObject(bodyText)
        val fromChoices = obj.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.ifBlank { null }
        (fromChoices
            ?: obj.optString("content").ifBlank { null }
            ?: obj.optString("message").ifBlank { null })
    } catch (_: Exception) {
        null
    }

    private fun lastAssistantContent(messages: JSONArray?): String {
        if (messages == null) return ""
        for (i in messages.length() - 1 downTo 0) {
            val obj = messages.optJSONObject(i) ?: continue
            if (obj.optString("role") == "assistant") {
                val text = readTextContent(obj)
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    /** Usage is parsed defensively: the gateway's exact keys aren't
     * pinned, so common token/cost/model spellings are all accepted and
     * absent values stay null (never estimated). */
    private fun parseUsage(u: JSONObject?): ServerUsage? {
        if (u == null) return null

        fun numericOf(vararg keys: String, asDouble: Boolean = false): Any? {
            for (key in keys) {
                if (u.has(key) && !u.isNull(key)) {
                    val raw = u.opt(key)
                    val value = if (asDouble) {
                        (raw as? Number)?.toDouble() ?: raw.toString().toDoubleOrNull()
                    } else {
                        (raw as? Number)?.toInt() ?: raw.toString().toIntOrNull()
                    }
                    if (raw != null && value != null) return value
                }
            }
            return null
        }

        val promptTokens = numericOf("prompt_tokens", "input_tokens") as? Int
        val completionTokens = numericOf("completion_tokens", "output_tokens") as? Int
        val costUsd = numericOf("cost_usd", "cost", asDouble = true) as? Double
        val modelId = u.optString("model_id").ifBlank { u.optString("model") }.ifBlank { null }
        val tier = u.optString("tier").ifBlank { null }
        if (promptTokens == null && completionTokens == null && costUsd == null && modelId == null && tier == null) {
            return null
        }
        return ServerUsage(
            tier = tier,
            modelId = modelId,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            costUsd = costUsd
        )
    }

    /** A non-2xx body can carry the real reason (`{"message": ...}` or
     * `{"error": ...}`) -- surface it instead of just the status code. */
    private fun errorDetail(response: Response): String? {
        val bodyText = try {
            response.body?.string()
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            val obj = JSONObject(bodyText)
            obj.optString("message").ifBlank { null }
                ?: obj.optJSONObject("error")?.optString("message")?.ifBlank { null }
                ?: obj.optString("error").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /** last_activity_at / created_at may arrive as epoch seconds, epoch
     * millis, or an ISO-8601 string -- accept all three, null otherwise. */
    private fun parseTimestamp(raw: Any?): Long? {
        return when (raw) {
            null -> null
            is Number -> epochToMillis(raw.toDouble())
            is String -> {
                val text = raw.trim()
                if (text.isEmpty()) {
                    null
                } else {
                    text.toDoubleOrNull()?.let { epochToMillis(it) } ?: parseIsoTimestamp(text)
                }
            }
            else -> null
        }
    }

    private fun epochToMillis(value: Double): Long? = when {
        value <= 0.0 -> null
        value > 1_000_000_000_000.0 -> value.toLong()
        else -> (value * 1000).toLong()
    }

    private fun parseIsoTimestamp(text: String): Long? {
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = format.parse(text) ?: continue
                return parsed.time
            } catch (_: ParseException) {
                // try the next pattern
            }
        }
        return null
    }

    private fun optInt(obj: JSONObject, vararg keys: String): Int {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val raw = obj.opt(key)
                val value = (raw as? Number)?.toInt() ?: raw.toString().toIntOrNull()
                if (value != null) return value
            }
        }
        return 0
    }
}
