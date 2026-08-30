package com.example.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AuthMode {
    SIGN_IN,
    CREATE_ACCOUNT
}

@Stable
class FestoAppState(
    val coroutineScope: CoroutineScope,
    /** Persisted-settings store for the theme override. Nullable so the
     * existing two-arg construction (unit tests) keeps working; the real
     * app path always provides it via rememberFestoAppState(). */
    private val themePrefs: ThemePreferences? = null,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Persisted-settings store for the Hermes gateway config (base URL,
     * API key, shared Wendy session id). Same nullable pattern as
     * themePrefs: unit tests construct without it. */
    private val backendPrefs: BackendPreferences? = null
) {
    companion object {
        private const val MAIN_CONVERSATION_ID = "wendy-main"
    }

    /** Loads the shared Hermes session's transcript. Nothing is rendered
     * until a session is picked (Settings does that); tool/system rows
     * are skipped -- they're Wendy's plumbing, not conversation bubbles. */
    private suspend fun loadHermesHistory() {
        val messages = messagesMap.getOrPut(MAIN_CONVERSATION_ID) { mutableStateListOf() }
        val sessionId = hermesSessionId
        if (sessionId == null) {
            messages.clear()
            isHistoryLoading = false
            return
        }
        val entries = HermesApi.fetchMessages(hermesBaseUrl, hermesApiKey, sessionId)
        messages.clear()
        entries.forEachIndexed { index, entry ->
            val role = when (entry.role) {
                "user" -> Role.USER
                "assistant" -> Role.ASSISTANT
                else -> return@forEachIndexed
            }
            messages.add(
                Message(
                    id = "hmsg-$index",
                    conversationId = MAIN_CONVERSATION_ID,
                    role = role,
                    content = entry.content,
                    timestamp = entry.createdAtMs ?: System.currentTimeMillis(),
                )
            )
        }
        val idx = conversations.indexOfFirst { it.id == MAIN_CONVERSATION_ID }
        if (idx != -1 && entries.isNotEmpty()) {
            val last = entries.last()
            conversations[idx] = conversations[idx].copy(
                preview = last.content.take(60),
                messageCount = entries.size,
                updatedAt = last.createdAtMs ?: System.currentTimeMillis(),
            )
        }
        isHistoryLoading = false
    }

    // Conversations: real Wendy has one continuous conversation shared
    // with Telegram, not separate per-device threads -- so there's exactly
    // one conversation here, seeded empty and filled in from the shared
    // gateway session's message history.
    val conversations = mutableStateListOf(
        Conversation(id = MAIN_CONVERSATION_ID, title = "Wendy")
    )
    var activeConversationId by mutableStateOf<String?>(MAIN_CONVERSATION_ID)
        private set

    // Messages grouped by conversation ID
    val messagesMap = mutableStateMapOf<String, MutableList<Message>>().apply {
        put(MAIN_CONVERSATION_ID, mutableStateListOf())
    }
    var isHistoryLoading by mutableStateOf(true)
        private set

    // Usage & Cost tracking -- only real server-reported usage lands in
    // the ledger, never an estimate.
    val usageEvents = mutableStateListOf<UsageEvent>()

    // UI Layer Flags
    var isDrawerOpen by mutableStateOf(false)
    var isUsageSheetOpen by mutableStateOf(false)
    var isSettingsSheetOpen by mutableStateOf(false)

    // Microphone permission mirror for the composer's voice actions.
    // ChatScreen requests RECORD_AUDIO through the platform flow and
    // reports the outcome here, so state survives recomposition.
    var micPermissionGranted by mutableStateOf(false)

    fun onMicPermissionResult(granted: Boolean) {
        micPermissionGranted = granted
    }

    // Settings: theme override (System / Light / Dark). The initial value
    // comes from the persisted store, read in MainActivity.onCreate BEFORE
    // any compose content is set; setThemeMode() writes through to the
    // same store so the choice survives relaunch. Backed by a private
    // state field because a delegated var's generated JVM setter would
    // clash with setThemeMode()'s signature.
    private var _themeMode by mutableStateOf(initialThemeMode)

    val themeMode: ThemeMode
        get() = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        if (mode == _themeMode) return
        _themeMode = mode
        themePrefs?.save(mode)
    }

    // ---- Hermes gateway state ----
    // Shared-session mode is the point: the app chats inside ONE gateway
    // session that Telegram also uses. hermesSessionId is that session;
    // run.completed's session_id is authoritative (compression may rotate
    // it) and is persisted back on every turn.

    var hermesBaseUrl by mutableStateOf(backendPrefs?.loadHermesBaseUrl() ?: HermesApi.DEFAULT_BASE_URL)
    var hermesApiKey by mutableStateOf(backendPrefs?.loadHermesApiKey() ?: "")

    var hermesSessionId by mutableStateOf(backendPrefs?.loadHermesSessionId())
        private set

    val hermesSessionKey: String
        get() = backendPrefs?.loadHermesSessionKey() ?: ""

    /** Initial transcript load. Declared AFTER every property
     * loadHermesHistory() touches (conversations/messagesMap/
     * isHistoryLoading/hermes*): an init block above them launches a
     * coroutine that reads later-declared properties, which crashes under
     * an eager dispatcher (unit tests, Main.immediate) with an NPE before
     * those properties initialize. Declaration order is the guarantee. */
    init {
        coroutineScope.launch { loadHermesHistory() }
    }

    val hermesSessions = mutableStateListOf<HermesSession>()
    var hermesSessionsLoading by mutableStateOf(false)
        private set
    var hermesSessionsError by mutableStateOf<String?>(null)
        private set

    /** Set only when the gateway answered OK but currently has no sessions.
     * Deliberately kept apart from [hermesSessionsError]: "gateway
     * unreachable / bad URL or key" and "no sessions yet" are different
     * states and must render differently (see HermesSessionsResult's doc
     * in HermesApi.kt, same reasoning as the old OutboxDownload). */
    var hermesSessionsEmptyNote by mutableStateOf<String?>(null)
        private set

    /** Live tool activity during a Hermes turn (tool.* events), rendered
     * as an activity line above the composer -- never as chat content. */
    var streamingTool by mutableStateOf<ToolActivity?>(null)
        private set

    /** Amber notice chip in ChatScreen for Hermes-specific situations:
     * no session picked yet, features the gateway doesn't have, etc. */
    var hermesNotice by mutableStateOf<String?>(null)

    /** Set while the voice-conversation loop (ChatScreen) is active:
     * invoked with the final reply text when a Hermes chat turn settles,
     * so the loop can speak it aloud. Null on the normal path -- zero
     * overhead, no behavior change. The loop clears it on stop. */
    var onHermesTurnCompleted: ((String) -> Unit)? = null

    /** Streaming companion to [onHermesTurnCompleted]: invoked on every
     * assistant.delta with the CUMULATIVE reply text so far, while the
     * turn is still streaming. The voice loop uses it to speak completed
     * sentences as they arrive instead of waiting for the whole reply.
     * Null on the normal path -- zero overhead. Cleared by the loop on
     * stop. */
    var onHermesTurnProgress: ((String) -> Unit)? = null

    /** The model the gateway is actually using, parsed from the usage
     * frames of the most recent completed turn (HermesApi reads
     * model_id/model). Null until the first reply arrives -- the top-bar
     * badge falls back to "Wendy". Read-only on purpose: the gateway
     * picks the model, there is no switch. */
    var hermesActiveModel by mutableStateOf<String?>(null)
        private set

    fun updateHermesBaseUrl(url: String) {
        // Trim on every keystroke, not just on save -- neither field has any
        // valid use for leading/trailing whitespace, and a stray space from
        // a mobile copy-paste (the realistic way this URL/key gets entered)
        // produces a silent mismatch: the request goes out with the extra
        // character and the gateway correctly rejects it, with no visible
        // difference in the Settings field to explain why.
        val trimmed = url.trim()
        hermesBaseUrl = trimmed
        backendPrefs?.saveHermesBaseUrl(trimmed)
    }

    fun updateHermesApiKey(key: String) {
        val trimmed = key.trim()
        hermesApiKey = trimmed
        backendPrefs?.saveHermesApiKey(trimmed)
    }

    /** Fetches the gateway's session list for the Settings picker. The
     * session Telegram is using (source "telegram") sorts first -- it's
     * the natural pick. */
    fun loadHermesSessions() {
        coroutineScope.launch {
            hermesSessionsLoading = true
            hermesSessionsError = null
            hermesSessionsEmptyNote = null
            when (val result = HermesApi.fetchSessions(hermesBaseUrl, hermesApiKey)) {
                is HermesSessionsResult.Ready -> {
                    hermesSessions.clear()
                    hermesSessions.addAll(
                        result.sessions.sortedWith(
                            compareByDescending<HermesSession> { it.isTelegram }
                                .thenByDescending { it.lastActivityAtMs ?: 0L }
                        )
                    )
                    if (result.sessions.isEmpty()) {
                        hermesSessionsEmptyNote = "The gateway has no sessions yet -- message Wendy on Telegram first."
                    }
                }
                is HermesSessionsResult.Failed -> {
                    hermesSessionsError = result.message?.takeIf { it.isNotBlank() }
                        ?: "Couldn't reach the Hermes gateway."
                }
            }
            hermesSessionsLoading = false
        }
    }

    /** Picks the ONE shared session the app chats inside -- the same
     * session Telegram uses. The transcript reloads from it immediately
     * so the chat screen shows the shared history. */
    fun selectHermesSession(id: String) {
        if (id.isBlank() || id == hermesSessionId) return
        hermesSessionId = id
        backendPrefs?.saveHermesSessionId(id)
        hermesNotice = null
        isHistoryLoading = true
        coroutineScope.launch { loadHermesHistory() }
    }

    // ---- Memory browser ----
    // The gateway has no /api/search endpoint, so "memory browsing" is
    // exactly what it does offer: list sessions, read transcripts, and
    // filter in the client. Nothing here invents server features.

    /** Full-screen Wendy memory browser (opened from the drawer's memory
     * row): gateway session list + read-only transcripts with a
     * client-side search filter. */
    var isMemoryBrowserOpen by mutableStateOf(false)

    /** The session whose transcript the browser is reading (null = the
     * session list is showing). */
    var memoryBrowserSessionId by mutableStateOf<String?>(null)
        private set

    var memoryBrowserTitle by mutableStateOf<String?>(null)
        private set

    /** Read-only snapshot of the browsed session's transcript. Kept apart
     * from the chat transcript so browsing never touches the live
     * conversation state. */
    val memoryBrowserMessages = mutableStateListOf<HermesHistoryEntry>()
    var memoryBrowserLoading by mutableStateOf(false)
        private set
    var memoryBrowserError by mutableStateOf<String?>(null)
        private set

    fun openMemoryTranscript(session: HermesSession) {
        if (memoryBrowserSessionId == session.id) return
        memoryBrowserSessionId = session.id
        memoryBrowserTitle = session.title
        memoryBrowserError = null
        memoryBrowserMessages.clear()
        coroutineScope.launch {
            memoryBrowserLoading = true
            when (val result = HermesApi.fetchTranscript(hermesBaseUrl, hermesApiKey, session.id)) {
                is HermesTranscriptResult.Ready -> {
                    memoryBrowserMessages.clear()
                    memoryBrowserMessages.addAll(result.entries)
                    // Ready-but-empty is NOT an error: leave the transcript
                    // empty so MemoryScreen's dedicated "no messages yet"
                    // state renders. memoryBrowserError is only for Failed
                    // (gateway said no) -- see HermesTranscriptResult's doc.
                }
                is HermesTranscriptResult.Failed -> {
                    memoryBrowserError = result.message?.takeIf { it.isNotBlank() }
                        ?: "Couldn't load this conversation."
                }
            }
            memoryBrowserLoading = false
        }
    }

    fun closeMemoryTranscript() {
        memoryBrowserSessionId = null
        memoryBrowserTitle = null
        memoryBrowserError = null
        memoryBrowserMessages.clear()
    }

    /** Set when the photo picker couldn't be served (read failure, empty
     * or oversized pick) -- surfaced as a dismissible chip in ChatScreen. */
    var attachmentError by mutableStateOf<String?>(null)

    /** HERMES photo picked in the composer, waiting to ride the next sent
     * message as a content-array image part. Cleared once sendMessage()
     * consumes it (success or failure -- a failed send shouldn't silently
     * re-attach a stale photo). */
    var pendingHermesImage by mutableStateOf<HermesImageAttachment?>(null)
        private set

    /** Called from the composer's photo-picker result with the ALREADY
     * downscaled JPEG bytes (max 1280px, quality ~80 -- the decode runs in
     * the UI layer, which owns the Context). Caps at 4MB raw: base64
     * inflates by ~33%, so this keeps the request body comfortably under
     * the gateway's ~5MB sensible ceiling instead of letting the gateway
     * be the only backstop. Sets attachmentError on rejection. */
    fun setPendingHermesImage(filename: String, jpegBytes: ByteArray) {
        if (jpegBytes.isEmpty()) {
            attachmentError = "\"$filename\" appears to be empty."
            return
        }
        if (jpegBytes.size > 4 * 1024 * 1024) {
            attachmentError = "\"$filename\" is still too large after downscaling (${jpegBytes.size / (1024 * 1024)}MB)."
            return
        }
        attachmentError = null
        pendingHermesImage = HermesImageAttachment(
            filename = filename,
            jpegBase64 = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP),
            sizeBytes = jpegBytes.size
        )
    }

    fun clearPendingHermesImage() {
        pendingHermesImage = null
        attachmentError = null
    }

    fun sendMessage(text: String) {
        val image = pendingHermesImage
        // A message needs SOME content -- text, a photo, or both; a photo
        // alone (no caption) is a real, valid send, matching how Telegram
        // already treats a bare photo upload.
        if ((text.isBlank() && image == null) || isStreamingResponse) return

        val sessionId = hermesSessionId
        if (sessionId == null) {
            hermesNotice = "Pick a Wendy session in Settings first -- the app and Telegram share one conversation."
            return
        }

        pendingHermesImage = null
        val trimmed = text.trim()
        val hermesMessages = messagesMap.getOrPut(MAIN_CONVERSATION_ID) { mutableStateListOf() }
        hermesMessages.add(
            Message(
                id = "msg-${UUID.randomUUID().toString().take(8)}",
                conversationId = MAIN_CONVERSATION_ID,
                role = Role.USER,
                content = trimmed,
                timestamp = System.currentTimeMillis(),
                // Display-only badge on the user bubble; the photo
                // itself already left the device inside the request.
                attachmentFilename = image?.filename
            )
        )
        streamAssistantResponseHermes(MAIN_CONVERSATION_ID, sessionId, trimmed, image)
    }

    /** Hermes turn: streams POST /api/sessions/{id}/chat/stream into the
     * placeholder-bubble plumbing. Tool.* events surface as an activity
     * line (streamingTool), run.completed's session_id is authoritative
     * and gets persisted (compression may rotate it), and the gateway
     * picks the model -- there's no model parameter to forward. A non-null
     * `image` switches the request body to the multimodal content-array
     * form (text part + image_url part) instead of a plain string. */
    private fun streamAssistantResponseHermes(
        convId: String,
        sessionId: String,
        userPrompt: String,
        image: HermesImageAttachment? = null
    ) {
        streamingJob?.cancel()
        isStreamingResponse = true

        val assistantMsgId = "msg-${UUID.randomUUID().toString().take(8)}"
        val convMessages = messagesMap[convId] ?: return
        // No model on the placeholder: the gateway picks the model, and
        // only the stream's usage frames reveal which one actually ran.
        val placeholderMsg = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = Role.ASSISTANT,
            content = "",
            isStreaming = true,
            timestamp = System.currentTimeMillis()
        )
        convMessages.add(placeholderMsg)

        val sessionKey = hermesSessionKey
        streamingJob = coroutineScope.launch {
            var fullResponse = ""
            var errorMessage: String? = null
            var finalUsage: ServerUsage? = null
            val startedAt = System.currentTimeMillis()
            // The session this turn is running on, tracked through its own
            // rotations -- the guard for whose rotation gets persisted.
            var turnSessionId = sessionId

            val turnFlow = if (image != null) {
                // Verified multimodal contract: "message" may be a content
                // array of {"type":"text"} / {"type":"image_url"} parts.
                // The image rides as a data URL; only a non-blank caption
                // produces a text part (an image-only send is valid).
                val parts = JSONArray()
                if (userPrompt.isNotBlank()) {
                    parts.put(JSONObject().put("type", "text").put("text", userPrompt))
                }
                parts.put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", image.dataUrl()))
                )
                HermesApi.streamChatMultimodal(
                    baseUrl = hermesBaseUrl,
                    apiKey = hermesApiKey,
                    sessionId = sessionId,
                    parts = parts,
                    sessionKey = sessionKey
                )
            } else {
                HermesApi.streamChat(
                    baseUrl = hermesBaseUrl,
                    apiKey = hermesApiKey,
                    sessionId = sessionId,
                    message = userPrompt,
                    sessionKey = sessionKey
                )
            }
            turnFlow.collect { event ->
                when (event) {
                    is HermesEvent.Delta -> {
                        streamingTool = null // assistant is talking; tool activity is over
                        fullResponse = event.textSoFar
                        onHermesTurnProgress?.invoke(fullResponse)
                        val idx = convMessages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) {
                            convMessages[idx] = convMessages[idx].copy(
                                content = fullResponse,
                                isStreaming = true
                            )
                        }
                    }
                    is HermesEvent.ToolActivity -> {
                        streamingTool = ToolActivity(toolName = event.toolName, detail = event.detail)
                    }
                    is HermesEvent.Completed -> {
                        if (event.text.isNotBlank()) fullResponse = event.text
                        finalUsage = event.usage
                    }
                    is HermesEvent.SessionRotated -> {
                        // run.completed's session_id is authoritative for the
                        // session THIS turn ran on -- persist it so the next
                        // turn and the transcript follow the rotated session.
                        // Only while that session is still the current one:
                        // if the user picked a different shared session in
                        // Settings mid-turn, their explicit pick must not be
                        // clobbered by a stale in-flight turn's rotation.
                        // Rotation chains within the turn (A -> A2 -> A3)
                        // still persist.
                        if (hermesSessionId == sessionId || hermesSessionId == turnSessionId) {
                            hermesSessionId = event.sessionId
                            backendPrefs?.saveHermesSessionId(event.sessionId)
                        }
                        turnSessionId = event.sessionId
                    }
                    is HermesEvent.Error -> errorMessage = event.message
                }
            }

            streamingTool = null

            // An error ends the turn even when partial text already
            // streamed: keep the partial text but make the failure visible
            // instead of letting the truncated reply pose as a complete one.
            if (errorMessage != null) {
                fullResponse = if (fullResponse.isBlank()) "Couldn't reach Wendy: $errorMessage"
                else "${fullResponse}\n\n(Wendy's reply was cut short: $errorMessage)"
            }

            val msgIndex = convMessages.indexOfFirst { it.id == assistantMsgId }
            if (msgIndex != -1) {
                convMessages[msgIndex] = convMessages[msgIndex].copy(
                    content = fullResponse,
                    isStreaming = false,
                    model = finalUsage?.modelId,
                    inputTokens = finalUsage?.promptTokens,
                    outputTokens = finalUsage?.completionTokens,
                    costUsd = finalUsage?.costUsd
                )
            }
            finalUsage?.modelId?.takeIf { it.isNotBlank() }?.let { hermesActiveModel = it }

            // Only a real server-reported usage lands in the ledger --
            // never an estimate.
            if (errorMessage == null && finalUsage != null) {
                usageEvents.add(
                    0,
                    UsageEvent(
                        id = System.currentTimeMillis(),
                        model = finalUsage?.modelId ?: "hermes",
                        kind = "chat",
                        inputTokens = finalUsage?.promptTokens ?: 0,
                        outputTokens = finalUsage?.completionTokens ?: 0,
                        costUsd = finalUsage?.costUsd ?: 0.0,
                        durationMs = (System.currentTimeMillis() - startedAt).toInt(),
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            isStreamingResponse = false

            // Voice-conversation hook: fired AFTER isStreamingResponse
            // clears, so the loop's next auto-send can never race an
            // in-flight turn. fullResponse may be the "Couldn't reach
            // Wendy..." failure text -- worth hearing in a hands-free
            // loop too.
            val turnCompletedHook = onHermesTurnCompleted
            if (turnCompletedHook != null && fullResponse.isNotBlank()) {
                turnCompletedHook(fullResponse)
            }
        }
    }

    // Auth State
    var isAuthenticated by mutableStateOf(true) // Default signed in for instant smooth preview
    var authMode by mutableStateOf(AuthMode.SIGN_IN)
    var userEmail by mutableStateOf("demo@festo.app")
    var userDisplayName by mutableStateOf("Joshua Baraka")
    var authInFlight by mutableStateOf(false)
    var authError by mutableStateOf<String?>(null)

    // Active Streaming Job
    private var streamingJob: Job? = null
    var isStreamingResponse by mutableStateOf(false)
        private set

    val activeMessages: List<Message>
        get() = activeConversationId?.let { messagesMap[it] } ?: emptyList()

    val totalTokens: Int
        get() = usageEvents.sumOf { it.inputTokens + it.outputTokens }

    val totalCostUsd: Double
        get() = usageEvents.sumOf { it.costUsd }

    fun selectAuthMode(mode: AuthMode) {
        authMode = mode
        authError = null
    }

    fun submitAuth(email: String, pass: String) {
        if (email.isBlank() || !email.contains("@")) {
            authError = "Please enter a valid email address"
            return
        }
        if (pass.length < 6) {
            authError = "Password must be at least 6 characters"
            return
        }

        authInFlight = true
        authError = null

        coroutineScope.launch {
            delay(800) // Simulated auth roundtrip
            authInFlight = false
            userEmail = email
            userDisplayName = email.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            isAuthenticated = true
        }
    }

    fun logout() {
        isAuthenticated = false
        isDrawerOpen = false
    }
}

@Composable
fun rememberFestoAppState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM
): FestoAppState {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val themePrefs = remember(context) { ThemePreferences(context) }
    val backendPrefs = remember(context) { BackendPreferences(context) }
    return remember {
        FestoAppState(coroutineScope, themePrefs, initialThemeMode, backendPrefs)
    }
}
