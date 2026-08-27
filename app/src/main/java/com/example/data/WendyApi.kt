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
 * conversation/memory, not a separate assistant. The server resolves the
 * session server-side from a fixed allowed-user id, so this client only
 * ever sends the message text; there is no per-device session concept.
 *
 * Security note (deliberate, not an oversight): this is plaintext HTTP with
 * a static bearer token, matching the tradeoff already accepted for the
 * debug-crash endpoint on the other server. The token is sniffable on the
 * wire. Fine for now (single user, dev/testing); add TLS (e.g. Caddy +
 * a nip.io hostname in front of the VPS) before trusting this beyond that.
 */
sealed class WendyEvent {
    data class Delta(val text: String) : WendyEvent()
    data class Final(val text: String) : WendyEvent()
    data class Error(val message: String) : WendyEvent()
}

/** One turn from GET /api/history -- role is "user" or "assistant". */
data class HistoryTurn(val role: String, val text: String, val timestamp: Long)

object WendyApi {
    private const val BASE_URL = "http://74.208.155.72:8090"
    private const val API_TOKEN = "t-CWsqQbhMqW6bwXb0IsDBIOxfPWeHCMne2imx-zJJU"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // streaming reply can legitimately run minutes (tool use)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Emits Delta events as Wendy's reply streams in, then exactly one
     * Final (or one Error) before completing. NDJSON body: one JSON object
     * per line, {"type":"delta"|"final"|"error", "text"|"message":...}. */
    fun sendMessage(message: String): Flow<WendyEvent> = callbackFlow {
        val body = JSONObject().put("message", message).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/api/chat")
            .header("Authorization", "Bearer $API_TOKEN")
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

    /** Fetches the real conversation history -- same session Telegram uses.
     * Returns an empty list on any failure (fresh install with no history
     * yet, or a transient network error) rather than throwing, since a
     * blank chat screen is a perfectly fine fallback. */
    suspend fun fetchHistory(): List<HistoryTurn> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/history")
            .header("Authorization", "Bearer $API_TOKEN")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
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

    private fun readStreamingResponse(response: Response, scope: ProducerScope<WendyEvent>) {
        response.use { resp ->
            if (!resp.isSuccessful) {
                scope.trySend(WendyEvent.Error("HTTP ${resp.code}"))
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
                        "final" -> scope.trySend(WendyEvent.Final(event.optString("text")))
                        "error" -> scope.trySend(WendyEvent.Error(event.optString("message", "unknown error")))
                    }
                } catch (_: Exception) {
                    // Malformed line -- skip rather than kill the whole stream over one bad frame.
                }
            }
        }
    }
}
