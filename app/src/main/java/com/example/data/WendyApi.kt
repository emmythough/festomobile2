package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to v4 -- Wendy's current generation, same one Telegram talks to via
 * tg_outbound.py (one continuous conversation/memory, not a separate
 * assistant). The server resolves the session server-side from a fixed
 * allowed-user id, so this client only ever sends the message text; there
 * is no per-device session concept.
 *
 * v4's mobile API (gateways/api_gateway.py, port 8091) is accept-and-poll,
 * not a streamed HTTP body like the old Gen 1 mobile_api.py on 8090:
 * POST /api/v4/message returns a bare receipt, and the actual reply(ies)
 * are drained by polling GET /api/v4/replies. There is no explicit
 * "turn complete" flag in that stream -- instead the reply shape itself is
 * the signal (see readReplies below), read straight off wendy_core.py's
 * three publish sites rather than guessed from timing:
 *   - {"kind":"ack", "speech":"On it.", ...}                  -> ack placeholder, ignored here
 *   - {"speech": "<text so far>", "reply_key":..., no "blocks"} -> streaming delta (replace-in-place)
 *   - {"speech": "<final text>", "blocks":[...], ...}         -> the real final reply (blocks always present, even [])
 *
 * Security note (deliberate, not an oversight): this is plaintext HTTP with
 * a static bearer token, matching the tradeoff already accepted for the
 * debug-crash endpoint on the other server. The token is sniffable on the
 * wire. Fine for now (single user, dev/testing); add TLS (e.g. Caddy +
 * a nip.io hostname in front of the VPS) before trusting this beyond that.
 */
sealed class WendyEvent {
    /** Full text-so-far, not an increment -- always replace the displayed bubble with this. */
    data class Delta(val text: String) : WendyEvent()
    data class Final(val text: String) : WendyEvent()
    data class Error(val message: String) : WendyEvent()
}

/** One turn from GET /api/history -- role is "user" or "assistant". */
data class HistoryTurn(val role: String, val text: String, val timestamp: Long)

object WendyApi {
    // v4's real mobile gateway (gateways/api_gateway.py) -- the one Telegram
    // now shares. Distinct port from the retired Gen 1 mobile_api.py (8090).
    private const val BASE_URL = "http://74.208.155.72:8091"
    private const val API_TOKEN = "t-CWsqQbhMqW6bwXb0IsDBIOxfPWeHCMne2imx-zJJU"

    // v4's api_gateway.py exposes ONLY /api/v4/{health,message,replies} --
    // no history, no audio (§ v4/README.md "explicitly NOT built yet").
    // Gen 1's mobile_api.py (port 8090) is still deployed and still the only
    // place /api/history and /api/audio/* exist, so those three calls below
    // stay pointed here. This is NOT the same conversation store sendMessage
    // talks to -- it's the least-bad source for a chat backlog and voice
    // I/O until v4 grows its own equivalents.
    private const val GEN1_BASE_URL = "http://74.208.155.72:8090"

    private const val POLL_INTERVAL_MS = 700L
    private const val POLL_TIMEOUT_MS = 120_000L // matches v4's model-call budget; ample for tool use

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Posts the message (receipt only -- v4's /api/v4/message never returns
     * the reply body, see the class doc), then polls /api/v4/replies until
     * the real final reply (the one carrying "blocks") arrives, an "error"
     * item shows up, or POLL_TIMEOUT_MS elapses. Emits Delta for each
     * streaming edit seen along the way, then exactly one Final or Error.
     *
     * "poll, not push" means another device's turn could theoretically
     * interleave replies here -- fine for the current single-allowed-user
     * setup (ALLOWED_USER_ID is one fixed id server-side), not fine the day
     * this app supports multiple accounts.
     */
    fun sendMessage(message: String): Flow<WendyEvent> = callbackFlow {
        val body = JSONObject().put("message", message).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/api/v4/message")
            .header("Authorization", "Bearer $API_TOKEN")
            .post(body)
            .build()

        try {
            val posted = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    trySend(WendyEvent.Error("HTTP ${response.code} posting message"))
                    false
                } else {
                    true
                }
            }
            if (posted) {
                pollReplies(this)
            }
        } catch (e: IOException) {
            trySend(WendyEvent.Error(e.message ?: "network error"))
        }
        close()

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    private suspend fun pollReplies(scope: ProducerScope<WendyEvent>) {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (scope.isActive && System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val request = Request.Builder()
                .url("$BASE_URL/api/v4/replies")
                .header("Authorization", "Bearer $API_TOKEN")
                .get()
                .build()
            val replies = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        scope.trySend(WendyEvent.Error("HTTP ${response.code} polling replies"))
                        return
                    }
                    val bodyText = response.body?.string() ?: "{}"
                    JSONObject(bodyText).optJSONArray("replies") ?: JSONArray()
                }
            } catch (e: IOException) {
                scope.trySend(WendyEvent.Error(e.message ?: "network error"))
                return
            }

            for (i in 0 until replies.length()) {
                val item = replies.getJSONObject(i)
                when {
                    item.optString("kind") == "ack" -> {
                        // "On it." placeholder -- server-side UX cue only, nothing to show here.
                    }
                    item.has("blocks") -> {
                        scope.trySend(WendyEvent.Final(item.optString("speech")))
                        return // this turn is done; stop polling
                    }
                    item.has("error") -> {
                        scope.trySend(WendyEvent.Error(item.optString("error", "unknown error")))
                        return
                    }
                    else -> {
                        scope.trySend(WendyEvent.Delta(item.optString("speech")))
                    }
                }
            }
        }
        scope.trySend(WendyEvent.Error("timed out waiting for a reply"))
    }

    /** Fetches recent conversation history from Gen 1's still-live history
     * endpoint (see GEN1_BASE_URL note above). Returns an empty list on
     * any failure (fresh install with no history yet, or a transient
     * network error) rather than throwing, since a blank chat screen is a
     * perfectly fine fallback. */
    suspend fun fetchHistory(): List<HistoryTurn> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$GEN1_BASE_URL/api/history")
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
            val request = Request.Builder()
                .url("$GEN1_BASE_URL/api/audio/transcribe")
                .header("Authorization", "Bearer $API_TOKEN")
                .post(body)
                .build()
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
            val request = Request.Builder()
                .url("$GEN1_BASE_URL/api/audio/speak")
                .header("Authorization", "Bearer $API_TOKEN")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("speak HTTP ${response.code}: ${response.body?.string()}")
                }
                response.body?.bytes() ?: ByteArray(0)
            }
        }
}
