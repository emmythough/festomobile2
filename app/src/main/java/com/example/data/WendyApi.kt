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
import java.util.concurrent.TimeUnit

/**
 * Talks to the SAME Wendy that Telegram talks to -- one continuous
 * conversation, one continuous memory, one shared model selection.
 *
 * As of 2026-08-29 this is Gen 1's mobile_api.py (port 8090) again --
 * NOT v4. Mobile briefly ran on v4's api_gateway.py (port 8091) earlier
 * tonight, which meant Telegram and the app were silently answering from
 * two different brains with two different histories. The owner's own
 * words: "what is telegram should be what is on the app -- same chat."
 * Reverted deliberately. v4 stays where tonight's other real work lives
 * (model-tier infra, usage reporting, memory browser groundwork) and can
 * become the shared backend later -- as a real, tested decision, not a
 * silent side effect of one client migrating alone.
 *
 * Model selection is real and BIDIRECTIONAL: GET/POST /api/model reads
 * and writes bridge.py's own model_for()/set_model_for() -- the exact
 * same per-chat state Telegram's /model command drives. Switching model
 * here changes what Telegram uses on its next message, and vice versa.
 * Verified live 2026-08-29: a POST here landed in bridge.py's own
 * models.json before this comment was written.
 *
 * Security note (deliberate, not an oversight): this is plaintext HTTP
 * with a static bearer token, matching the tradeoff already accepted for
 * the debug-crash endpoint on the other server. The token is sniffable
 * on the wire. Fine for now (single user, dev/testing); add TLS (e.g.
 * Caddy + a nip.io hostname in front of the VPS) before trusting this
 * beyond that.
 */
sealed class WendyEvent {
    /** Full text-so-far, not an increment -- always replace the displayed bubble with this. */
    data class Delta(val text: String) : WendyEvent()
    data class Final(val text: String, val usage: ServerUsage? = null) : WendyEvent()
    data class Error(val message: String) : WendyEvent()
}

/** Server-reported token/cost usage for a completed turn -- read from
 * opencode's own finished message, not estimated. `tier` is the model
 * KEY ("flash", "haiku", ...), `modelId` is the real underlying model
 * ("google/gemini-3.7-flash"). Absent on failures; never substitute a
 * local estimate for a missing value. */
data class ServerUsage(
    val tier: String? = null,
    val modelId: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val costUsd: Double? = null
)

/** One turn from GET /api/history -- role is "user" or "assistant". */
data class HistoryTurn(val role: String, val text: String, val timestamp: Long)

object WendyApi {
    // Gen 1's mobile_api.py -- the one real backend chat, history, models,
    // and audio all live on. Single base URL, single token, on purpose:
    // the two-backend split earlier tonight was the exact bug this fixes.
    private const val BASE_URL = "http://74.208.155.72:8090"
    private const val API_TOKEN = "t-CWsqQbhMqW6bwXb0IsDBIOxfPWeHCMne2imx-zJJU"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // streaming reply can legitimately run minutes (tool use)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun authed(url: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $API_TOKEN")

    /** Emits Delta events as Wendy's reply streams in, then exactly one
     * Final (carrying real server usage when available) or one Error
     * before completing. NDJSON body: one JSON object per line,
     * {"type":"delta"|"final"|"error", "text"|"message":..., "usage"?:{...}}.
     * `model` is optional -- omitted means "whatever is already selected"
     * (shared with Telegram); passing it also SWITCHES the shared
     * selection, same as sending /model on Telegram would. */
    fun sendMessage(message: String, model: String? = null): Flow<WendyEvent> = callbackFlow {
        val json = JSONObject().put("message", message)
        if (model != null) json.put("model", model)
        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = authed("$BASE_URL/api/chat")
            .post(body)
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(WendyEvent.Error(e.message ?: "network error"))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                readStreamingResponse(response, this@callbackFlow)
                close()
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun readStreamingResponse(response: Response, scope: ProducerScope<WendyEvent>) {
        response.use { resp ->
            if (!resp.isSuccessful) {
                // A non-2xx here can carry a real, actionable body (e.g.
                // {"error":"unknown model","allowed":[...]} from a bad
                // model switch riding on this same request) -- surface it
                // rather than just the status code where we can.
                val detail = try {
                    resp.body?.string()?.let { JSONObject(it).optString("error") }
                } catch (_: Exception) {
                    null
                }
                scope.trySend(WendyEvent.Error(detail?.takeIf { it.isNotBlank() } ?: "HTTP ${resp.code}"))
                return
            }
            val source = resp.body?.source() ?: run {
                scope.trySend(WendyEvent.Error("empty response body"))
                return
            }
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                try {
                    val event = JSONObject(line)
                    when (event.optString("type")) {
                        "delta" -> scope.trySend(WendyEvent.Delta(event.optString("text")))
                        "final" -> {
                            val usage = event.optJSONObject("usage")?.let { u ->
                                ServerUsage(
                                    tier = u.optString("tier").ifBlank { null },
                                    modelId = u.optString("model_id").ifBlank { null },
                                    promptTokens = if (u.has("prompt_tokens") && !u.isNull("prompt_tokens")) u.optInt("prompt_tokens") else null,
                                    completionTokens = if (u.has("completion_tokens") && !u.isNull("completion_tokens")) u.optInt("completion_tokens") else null,
                                    costUsd = if (u.has("cost_usd") && !u.isNull("cost_usd")) u.optDouble("cost_usd") else null
                                )
                            }
                            scope.trySend(WendyEvent.Final(event.optString("text"), usage))
                        }
                        "error" -> scope.trySend(WendyEvent.Error(event.optString("message", "unknown error")))
                    }
                } catch (_: Exception) {
                    // Malformed line -- skip rather than kill the whole stream over one bad frame.
                }
            }
        }
    }

    /** Fetches the real, currently-selected model plus every option --
     * the SAME state GET reads Telegram's /model command would show.
     * Returns an empty selectable list (and null current) on any failure
     * rather than throwing; the picker degrades to "unavailable" instead
     * of a crash. */
    suspend fun fetchModels(): List<ModelOption> = withContext(Dispatchers.IO) {
        try {
            client.newCall(authed("$BASE_URL/api/model").get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyText = response.body?.string() ?: return@withContext emptyList()
                val obj = JSONObject(bodyText)
                val current = obj.optString("current").ifBlank { null }
                val array = obj.optJSONArray("models") ?: return@withContext emptyList()
                (0 until array.length()).mapNotNull { i ->
                    val entry = array.getJSONObject(i)
                    val id = entry.optString("id")
                    if (id.isBlank()) return@mapNotNull null
                    ModelOption(
                        id = id,
                        modelId = entry.optString("model_id"),
                        isDefault = id == current,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Fetches the real conversation history -- same session Telegram uses.
     * Returns an empty list on any failure (fresh install with no history
     * yet, or a transient network error) rather than throwing, since a
     * blank chat screen is a perfectly fine fallback. */
    suspend fun fetchHistory(): List<HistoryTurn> = withContext(Dispatchers.IO) {
        try {
            client.newCall(authed("$BASE_URL/api/history").get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val array = JSONArray(body)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    HistoryTurn(
                        role = obj.optString("role"),
                        text = obj.optString("text"),
                        timestamp = obj.optLong("timestamp"),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Speech-to-text via the server-side proxy /api/audio/transcribe.
     * Sends base64-encoded audio bytes; the server holds the OpenRouter key.
     * Returns the transcribed text, or throws on failure.
     */
    @Throws(IOException::class)
    suspend fun transcribeAudio(audioBase64: String, format: String = "m4a"): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("audio", audioBase64)
                .put("format", format)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = authed("$BASE_URL/api/audio/transcribe").post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("transcribe HTTP ${response.code}: ${response.body?.string()}")
                }
                val text = response.body?.string() ?: return@withContext ""
                val json = JSONObject(text)
                json.optString("text")
            }
        }

    /**
     * Text-to-speech via the server-side proxy /api/audio/speak.
     * Returns raw mp3 bytes; the server holds the OpenRouter key.
     */
    @Throws(IOException::class)
    suspend fun synthesizeSpeech(text: String, voice: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("text", text).apply {
                if (voice != null) put("voice", voice)
            }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = authed("$BASE_URL/api/audio/speak").post(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("speak HTTP ${response.code}: ${response.body?.string()}")
                }
                response.body?.bytes() ?: ByteArray(0)
            }
        }
}
