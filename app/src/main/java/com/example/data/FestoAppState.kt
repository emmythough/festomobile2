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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.io.IOException
import java.util.UUID

enum class AuthMode {
    SIGN_IN,
    CREATE_ACCOUNT
}

@Stable
class FestoAppState(
    val coroutineScope: CoroutineScope,
    private val audioEngine: VoiceAudioEngine? = null,
    /** Persisted-settings store for the theme override. Nullable so the
     * existing two-arg construction (unit tests) keeps working; the real
     * app path always provides it via rememberFestoAppState(). */
    private val themePrefs: ThemePreferences? = null,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Persisted-settings store for backend mode + Hermes gateway config
     * (base URL, API key, shared Wendy session id). Same nullable pattern
     * as themePrefs: unit tests construct without it and stay on the
     * legacy backend, untouched. */
    private val backendPrefs: BackendPreferences? = null,
    initialBackendMode: BackendMode = BackendMode.GEN1
) {
    companion object {
        private const val MAIN_CONVERSATION_ID = "wendy-main"
        // Used only until the real GET /api/model call returns (or if it
        // ever fails) -- "flash" matches bridge.py's own DEFAULT_MODEL, so
        // this reflects the truth even before the network round trip.
        private val FALLBACK_MODEL = ModelOption(
            id = "flash",
            modelId = "openrouter/google/gemini-3.7-flash",
            isDefault = true
        )
    }

    init {
        coroutineScope.launch { loadRealModels() }
        coroutineScope.launch { loadRealHistory() }
    }

    /** Real network state for the model picker's empty view -- without
     * this, a failed fetch left availableModels empty forever and the
     * picker showed "Loading..." permanently with no way to retry. */
    var modelsLoadFailed by mutableStateOf(false)

    suspend fun loadRealModels() {
        // The model picker is a Gen 1 concept (GET /api/model on Wendy's
        // own server, shared with Telegram's /model command). Hermes has
        // no model switch -- the gateway picks -- so in HERMES mode this
        // is deliberately a no-op and the top bar shows a static badge
        // instead of the model chip.
        if (_backendMode == BackendMode.HERMES) return
        modelsLoadFailed = false
        val models = WendyApi.fetchModels()
        if (models.isNotEmpty()) {
            availableModels.clear()
            availableModels.addAll(models)
            val default = models.firstOrNull { it.isDefault } ?: models.first()
            selectedModel = default
        } else if (availableModels.isEmpty()) {
            modelsLoadFailed = true
        }
    }

    fun retryLoadModels() {
        coroutineScope.launch { loadRealModels() }
    }

    private suspend fun loadRealHistory() {
        // Each backend owns its own transcript truth: Gen 1 reads
        // /api/history off Wendy's server; Hermes reads the shared
        // gateway session's messages. Switching backends reloads from
        // the newly active one.
        if (_backendMode == BackendMode.HERMES) {
            loadHermesHistory()
        } else {
            loadGen1History()
        }
        isHistoryLoading = false
    }

    private suspend fun loadGen1History() {
        val history = WendyApi.fetchHistory()
        if (history.isNotEmpty()) {
            val messages = messagesMap.getOrPut(MAIN_CONVERSATION_ID) { mutableStateListOf() }
            messages.clear()
            messages.addAll(
                history.mapIndexed { index, turn ->
                    Message(
                        id = "hist-$index",
                        conversationId = MAIN_CONVERSATION_ID,
                        role = if (turn.role == "user") Role.USER else Role.ASSISTANT,
                        content = turn.text,
                        timestamp = turn.timestamp,
                    )
                }
            )
            val idx = conversations.indexOfFirst { it.id == MAIN_CONVERSATION_ID }
            if (idx != -1) {
                conversations[idx] = conversations[idx].copy(
                    preview = history.last().text.take(60),
                    messageCount = messages.size,
                    updatedAt = history.last().timestamp,
                )
            }
        }
    }

    /** Loads the shared Hermes session's transcript. Nothing is rendered
     * until a session is picked (Settings does that); tool/system rows
     * are skipped -- they're Wendy's plumbing, not conversation bubbles. */
    private suspend fun loadHermesHistory() {
        val messages = messagesMap.getOrPut(MAIN_CONVERSATION_ID) { mutableStateListOf() }
        val sessionId = hermesSessionId
        if (sessionId == null) {
            messages.clear()
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
    }

    // Auth State
    var isAuthenticated by mutableStateOf(true) // Default signed in for instant smooth preview
    var authMode by mutableStateOf(AuthMode.SIGN_IN)
    var userEmail by mutableStateOf("demo@festo.app")
    var userDisplayName by mutableStateOf("Joshua Baraka")
    var authInFlight by mutableStateOf(false)
    var authError by mutableStateOf<String?>(null)

    // Models & Conversations
    val availableModels = mutableStateListOf<ModelOption>()
    var selectedModel by mutableStateOf(FALLBACK_MODEL)
        private set

    // Real Wendy has one continuous conversation shared with Telegram, not
    // separate per-device threads -- so there's exactly one conversation
    // here, seeded empty and filled in from GET /api/history below rather
    // than from MockData.
    val conversations = mutableStateListOf(
        Conversation(id = MAIN_CONVERSATION_ID, title = "Wendy", modelId = FALLBACK_MODEL.id)
    )
    var activeConversationId by mutableStateOf<String?>(MAIN_CONVERSATION_ID)
        private set

    // Messages grouped by conversation ID
    val messagesMap = mutableStateMapOf<String, MutableList<Message>>().apply {
        put(MAIN_CONVERSATION_ID, mutableStateListOf())
    }
    var isHistoryLoading by mutableStateOf(true)
        private set

    // Cross-session Memories
    // Was seeded with 3 fabricated facts (fake infra details, a made-up
    // voice protocol spec) presented as if real, distilled memory --
    // confirmed nothing here ever reaches Wendy or comes from her (see
    // MemorySheet.kt's honesty-relabeled header). Starting empty until
    // this is wired to a real memory API is the honest state, not
    // "worse than no data" like the fake seed was.
    val memories = mutableStateListOf<MemoryFact>()
    var memorySearchQuery by mutableStateOf("")

    // Usage & Cost tracking
    val usageEvents = mutableStateListOf<UsageEvent>().apply { addAll(MockData.initialUsageEvents) }

    // UI Layer Flags
    var isDrawerOpen by mutableStateOf(false)
    var isModelSheetOpen by mutableStateOf(false)
    var isMemorySheetOpen by mutableStateOf(false)
    var isUsageSheetOpen by mutableStateOf(false)
    var isSettingsSheetOpen by mutableStateOf(false)
    var isFilesSheetOpen by mutableStateOf(false)

    /** Outbox badge for the drawer's "Wendy's Files" row -- refreshed
     * when the drawer opens, not polled. 0 means nothing waiting. */
    var outboxPendingCount by mutableStateOf(0)
    var isVoiceOverlayOpen by mutableStateOf(false)

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

    // ---- Backend mode (Gen 1 direct vs Hermes gateway) ----
    // Same persisted-settings pattern as the theme above: the initial
    // value comes from the store (read before construction finishes),
    // writes go through the same store. GEN1 is the hard default --
    // adding Hermes must never change behavior for someone who hasn't
    // opted in.
    private var _backendMode by mutableStateOf(initialBackendMode)

    val backendMode: BackendMode
        get() = _backendMode

    /** Switches backend at runtime: cancels any in-flight stream (its
     * finalize block won't run, so the composer is unfrozen here), clears
     * the transcript, and reloads history + (for Gen 1) models from the
     * newly active backend. */
    fun setBackendMode(mode: BackendMode) {
        if (mode == _backendMode) return
        streamingJob?.cancel()
        streamingTool = null
        hermesNotice = null
        isStreamingResponse = false
        _backendMode = mode
        backendPrefs?.saveMode(mode)
        messagesMap[MAIN_CONVERSATION_ID]?.clear()
        isHistoryLoading = true
        coroutineScope.launch {
            if (mode == BackendMode.GEN1) loadRealModels()
            loadRealHistory()
        }
    }

    // ---- Hermes gateway state (only consulted in HERMES mode) ----
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

    val hermesSessions = mutableStateListOf<HermesSession>()
    var hermesSessionsLoading by mutableStateOf(false)
        private set
    var hermesSessionsError by mutableStateOf<String?>(null)
        private set

    /** Live tool activity during a Hermes turn (tool.* events), rendered
     * as an activity line above the composer -- never as chat content. */
    var streamingTool by mutableStateOf<ToolActivity?>(null)
        private set

    /** Amber notice chip in ChatScreen for Hermes-specific situations:
     * no session picked yet, features the gateway doesn't have, etc. */
    var hermesNotice by mutableStateOf<String?>(null)

    fun updateHermesBaseUrl(url: String) {
        hermesBaseUrl = url
        backendPrefs?.saveHermesBaseUrl(url)
    }

    fun updateHermesApiKey(key: String) {
        hermesApiKey = key
        backendPrefs?.saveHermesApiKey(key)
    }

    /** Fetches the gateway's session list for the Settings picker. The
     * session Telegram is using (source "telegram") sorts first -- it's
     * the natural pick. */
    fun loadHermesSessions() {
        coroutineScope.launch {
            hermesSessionsLoading = true
            hermesSessionsError = null
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
                        hermesSessionsError = "The gateway has no sessions yet -- message Wendy on Telegram first."
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


    /** A file picked in the composer, waiting to ride on the next sent
     * message. Cleared once sendMessage() consumes it (success or
     * failure -- a failed send shouldn't leave a stale attachment
     * silently re-attached to whatever the user types next). */
    var pendingAttachment by mutableStateOf<PendingAttachment?>(null)
    var attachmentError by mutableStateOf<String?>(null)

    /** Set when the server-side session reset behind startFreshConversation()
     * fails -- surfaced as a dismissible chip in ChatScreen (same pattern as
     * attachmentError) instead of silently faking a local-only reset. */
    var sessionResetError by mutableStateOf<String?>(null)

    // Voice State Machine
    var voiceState by mutableStateOf(VoiceState.IDLE)
    var voiceLiveTranscript by mutableStateOf("")
    var voiceRecordingDurationSec by mutableStateOf(0)
    var voiceAudioLevels = mutableStateListOf(0.2f, 0.4f, 0.6f, 0.3f, 0.7f, 0.5f, 0.8f, 0.4f)

    /** Set true once the UI has obtained RECORD_AUDIO permission. */
    var micPermissionGranted by mutableStateOf(false)
        private set

    fun onMicPermissionResult(granted: Boolean) {
        micPermissionGranted = granted
    }

    // Active Streaming Job
    private var streamingJob: Job? = null
    private var voiceTimerJob: Job? = null
    var isStreamingResponse by mutableStateOf(false)

    val activeMessages: List<Message>
        get() = activeConversationId?.let { messagesMap[it] } ?: emptyList()

    val activeConversation: Conversation?
        get() = conversations.find { it.id == activeConversationId }

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

    fun selectModel(model: ModelOption) {
        selectedModel = model
        // Persist model onto active conversation
        activeConversationId?.let { convId ->
            val index = conversations.indexOfFirst { it.id == convId }
            if (index != -1) {
                val updated = conversations[index].copy(modelId = model.id)
                conversations[index] = updated
            }
        }
    }

    /** "Start Fresh" -- resets the ONE real server-side session (the same
     * shared session Telegram uses) via POST /api/new, then clears the
     * local message list. This is a real reset, not a fake second thread:
     * without the server call, the next history fetch would resurrect
     * everything that was cleared locally. */
    fun startFreshConversation(initialPrompt: String? = null) {
        // Hermes mode shares ONE session with Telegram by design; there
        // is no per-surface reset to call. Explain instead of silently
        // doing nothing (or worse, hitting the Gen 1 server).
        if (_backendMode == BackendMode.HERMES) {
            hermesNotice = "Hermes mode shares one continuous session with Telegram -- there's no separate fresh chat."
            return
        }
        coroutineScope.launch {
            sessionResetError = null
            val ok = WendyApi.resetSession()
            if (!ok) {
                // Do NOT clear local messages on a failed reset -- the
                // server session is still live, and history would
                // resurrect everything on the next fetch anyway. Surface
                // the failure honestly instead.
                sessionResetError = "Couldn't reset the session on the server. Your history with Wendy is unchanged."
                return@launch
            }
            messagesMap[MAIN_CONVERSATION_ID]?.clear()
            isDrawerOpen = false
            if (!initialPrompt.isNullOrBlank()) {
                sendMessage(initialPrompt)
            }
        }
    }

    /** Called from the composer's file-picker result. Caps client-side at
     * 12MB raw (server's real limit is 15MB decoded; base64 inflates by
     * ~33%, so this leaves real headroom rather than relying on the
     * server to be the only backstop). Sets attachmentError on rejection
     * instead of silently dropping the pick. */
    fun setPendingAttachment(filename: String, bytes: ByteArray) {
        // The Hermes gateway contract has no attachment field -- refuse at
        // pick time with the honest reason rather than accepting a file
        // that could never be sent.
        if (_backendMode == BackendMode.HERMES) {
            attachmentError = "Attachments aren't wired to the Hermes gateway yet."
            return
        }
        val maxRawBytes = 12 * 1024 * 1024
        if (bytes.size > maxRawBytes) {
            attachmentError = "\"$filename\" is too large (${bytes.size / (1024 * 1024)}MB, limit 12MB)."
            return
        }
        if (bytes.isEmpty()) {
            attachmentError = "\"$filename\" appears to be empty."
            return
        }
        attachmentError = null
        pendingAttachment = PendingAttachment(
            filename = filename,
            dataBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        )
    }

    fun clearPendingAttachment() {
        pendingAttachment = null
        attachmentError = null
    }

    fun sendMessage(text: String) {
        val attachment = pendingAttachment
        // A message needs SOME content -- text, or an attachment, or both;
        // an attachment alone (no caption) is a real, valid send, matching
        // how Telegram already treats a bare photo/document upload.
        if ((text.isBlank() && attachment == null) || isStreamingResponse) return

        // Hermes mode has its own send path: no attachment field in the
        // gateway contract, and a shared session must be picked first.
        if (_backendMode == BackendMode.HERMES) {
            if (attachment != null) {
                // Refuse honestly instead of silently dropping the file:
                // keep the attachment pending so the user can switch back
                // to Gen 1 and send it, or clear it.
                attachmentError = "Attachments aren't wired to the Hermes gateway yet -- clear the file or switch back to Gen 1."
                return
            }
            val sessionId = hermesSessionId
            if (sessionId == null) {
                hermesNotice = "Pick a Wendy session in Settings first -- the app and Telegram share one conversation."
                return
            }
            pendingAttachment = null
            val hermesMessages = messagesMap.getOrPut(MAIN_CONVERSATION_ID) { mutableStateListOf() }
            hermesMessages.add(
                Message(
                    id = "msg-${UUID.randomUUID().toString().take(8)}",
                    conversationId = MAIN_CONVERSATION_ID,
                    role = Role.USER,
                    content = text.trim(),
                    modality = Modality.TEXT,
                    timestamp = System.currentTimeMillis()
                )
            )
            streamAssistantResponseHermes(MAIN_CONVERSATION_ID, sessionId, text.trim())
            return
        }

        pendingAttachment = null

        var targetConvId = activeConversationId
        if (targetConvId == null) {
            val newId = "conv-${UUID.randomUUID().toString().take(8)}"
            val title = text.take(32).trim()
            val newConv = Conversation(
                id = newId,
                title = title,
                modelId = selectedModel.id
            )
            conversations.add(0, newConv)
            messagesMap[newId] = mutableStateListOf()
            activeConversationId = newId
            targetConvId = newId
        }

        val convMessages = messagesMap.getOrPut(targetConvId) { mutableStateListOf() }

        // Auto rename if title is placeholder
        val currentConv = conversations.find { it.id == targetConvId }
        if (currentConv != null && (currentConv.title == "New Conversation" || convMessages.isEmpty())) {
            val updatedTitle = text.take(32).trim() + if (text.length > 32) "..." else ""
            val idx = conversations.indexOfFirst { it.id == targetConvId }
            if (idx != -1) {
                conversations[idx] = conversations[idx].copy(title = updatedTitle, preview = text.take(60))
            }
        }

        val userMessage = Message(
            id = "msg-${UUID.randomUUID().toString().take(8)}",
            conversationId = targetConvId,
            role = Role.USER,
            content = text.trim(),
            modality = Modality.TEXT,
            timestamp = System.currentTimeMillis(),
            attachmentFilename = attachment?.filename
        )
        convMessages.add(userMessage)

        // Stream assistant reply
        streamAssistantResponse(targetConvId, text, attachment)
    }

    private fun streamAssistantResponse(convId: String, userPrompt: String, attachment: PendingAttachment? = null) {
        streamingJob?.cancel()
        isStreamingResponse = true

        val assistantMsgId = "msg-${UUID.randomUUID().toString().take(8)}"
        val currentSelectedModel = selectedModel
        val placeholderMsg = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = Role.ASSISTANT,
            content = "",
            modality = Modality.TEXT,
            model = currentSelectedModel.id,
            isStreaming = true,
            timestamp = System.currentTimeMillis()
        )
        val convMessages = messagesMap[convId] ?: return
        convMessages.add(placeholderMsg)

        streamingJob = coroutineScope.launch {
            var fullResponse = ""
            var errorMessage: String? = null
            var finalUsage: ServerUsage? = null
            val startedAt = System.currentTimeMillis()

            // Server tolerates a blank message when an attachment rides
            // along (it falls back to just the file-location note) --
            // the earlier check in sendMessage() already guarantees at
            // least one of text/attachment is real.
            WendyApi.sendMessage(userPrompt, currentSelectedModel.id, attachment).collect { event ->
                when (event) {
                    is WendyEvent.Delta -> {
                        fullResponse = event.text
                        val msgIndex = convMessages.indexOfFirst { it.id == assistantMsgId }
                        if (msgIndex != -1) {
                            convMessages[msgIndex] = convMessages[msgIndex].copy(
                                content = fullResponse,
                                isStreaming = true
                            )
                        }
                    }
                    is WendyEvent.Final -> {
                        fullResponse = event.text
                        finalUsage = event.usage
                    }
                    is WendyEvent.Error -> errorMessage = event.message
                }
            }

            if (errorMessage != null && fullResponse.isBlank()) {
                fullResponse = "Couldn't reach Wendy: $errorMessage"
            }

            val msgIndex = convMessages.indexOfFirst { it.id == assistantMsgId }
            if (msgIndex != -1) {
                convMessages[msgIndex] = convMessages[msgIndex].copy(
                    content = fullResponse,
                    isStreaming = false,
                    model = finalUsage?.modelId ?: currentSelectedModel.id,
                    inputTokens = finalUsage?.promptTokens,
                    outputTokens = finalUsage?.completionTokens,
                    costUsd = finalUsage?.costUsd
                )
            }

            if (errorMessage == null && finalUsage?.costUsd != null) {
                val event = UsageEvent(
                    id = System.currentTimeMillis(),
                    model = finalUsage?.modelId ?: currentSelectedModel.id,
                    kind = "chat",
                    inputTokens = finalUsage?.promptTokens ?: 0,
                    outputTokens = finalUsage?.completionTokens ?: 0,
                    costUsd = finalUsage?.costUsd ?: 0.0,
                    durationMs = (System.currentTimeMillis() - startedAt).toInt(),
                    timestamp = System.currentTimeMillis()
                )
                usageEvents.add(0, event)

                // Distill memory if notable
                maybeDistillMemory(userPrompt, fullResponse, activeConversation?.title)
            }

            isStreamingResponse = false
        }
    }

    /** Hermes turn: streams POST /api/sessions/{id}/chat/stream into the
     * same placeholder-bubble plumbing the Gen 1 path uses. Differences
     * from the legacy path: tool.* events surface as an activity line
     * (streamingTool), run.completed's session_id is authoritative and
     * gets persisted (compression may rotate it), and the gateway picks
     * the model -- there's no model parameter to forward. */
    private fun streamAssistantResponseHermes(convId: String, sessionId: String, userPrompt: String) {
        streamingJob?.cancel()
        isStreamingResponse = true

        val assistantMsgId = "msg-${UUID.randomUUID().toString().take(8)}"
        val convMessages = messagesMap[convId] ?: return
        val placeholderMsg = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = Role.ASSISTANT,
            content = "",
            modality = Modality.TEXT,
            model = "hermes",
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

            HermesApi.streamChat(
                baseUrl = hermesBaseUrl,
                apiKey = hermesApiKey,
                sessionId = sessionId,
                message = userPrompt,
                sessionKey = sessionKey
            ).collect { event ->
                when (event) {
                    is HermesEvent.Delta -> {
                        streamingTool = null // assistant is talking; tool activity is over
                        fullResponse = event.textSoFar
                        val msgIndex = convMessages.indexOfFirst { it.id == assistantMsgId }
                        if (msgIndex != -1) {
                            convMessages[msgIndex] = convMessages[msgIndex].copy(
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
                        // run.completed's session_id is authoritative --
                        // persist it so the next turn and the transcript
                        // follow the rotated session.
                        hermesSessionId = event.sessionId
                        backendPrefs?.saveHermesSessionId(event.sessionId)
                    }
                    is HermesEvent.Error -> errorMessage = event.message
                }
            }

            streamingTool = null

            if (errorMessage != null && fullResponse.isBlank()) {
                fullResponse = "Couldn't reach Wendy: $errorMessage"
            }

            val msgIndex = convMessages.indexOfFirst { it.id == assistantMsgId }
            if (msgIndex != -1) {
                convMessages[msgIndex] = convMessages[msgIndex].copy(
                    content = fullResponse,
                    isStreaming = false,
                    model = finalUsage?.modelId ?: "hermes",
                    inputTokens = finalUsage?.promptTokens,
                    outputTokens = finalUsage?.completionTokens,
                    costUsd = finalUsage?.costUsd
                )
            }

            // Only a real server-reported usage lands in the ledger --
            // never an estimate (same rule as the Gen 1 path).
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
        }
    }

    private fun maybeDistillMemory(userPrompt: String, assistantReply: String, convTitle: String?) {
        val lower = userPrompt.lowercase()
        if (lower.contains("i prefer") || lower.contains("i always") || lower.contains("my project") || lower.contains("remember")) {
            val memoryContent = if (lower.contains("i prefer")) {
                "User stated preference: " + userPrompt.substringAfter("i prefer").take(70).trim()
            } else if (lower.contains("my project")) {
                "User project context: " + userPrompt.take(80).trim()
            } else {
                "Noted fact: " + userPrompt.take(80).trim()
            }
            memories.add(0, MemoryFact(
                id = "mem-${UUID.randomUUID().toString().take(8)}",
                content = memoryContent,
                category = "Preference",
                sourceConversationTitle = convTitle ?: "Chat",
                createdAt = System.currentTimeMillis()
            ))
        }
    }

    // Voice Engine -- real mic → STT → chat → TTS → playback.
    // The full pipeline runs through the same Wendy the text chat uses, so
    // a spoken message and a typed one produce identical replies. STT and
    // TTS are proxied server-side (the OpenRouter key never ships in the
    // app); the chat call reuses the existing streamAssistantResponse path.
    fun startVoiceRecording() {
        if (voiceState != VoiceState.IDLE) return
        // Voice is a Gen 1 pipeline (STT/TTS are proxied through Wendy's
        // own server); the Hermes gateway has no audio endpoints, so the
        // overlay would be a dead end in HERMES mode.
        if (_backendMode == BackendMode.HERMES) {
            voiceLiveTranscript = "Voice isn't wired to the Hermes gateway yet -- type instead."
            return
        }
        val engine = audioEngine ?: return
        if (!micPermissionGranted) {
            // The UI should have requested RECORD_AUDIO before opening the
            // overlay; if it somehow didn't, refuse rather than crash.
            voiceLiveTranscript = "Microphone permission required. Allow access to record voice."
            return
        }
        voiceLiveTranscript = ""
        voiceRecordingDurationSec = 0
        engine.startRecording()
        voiceState = VoiceState.RECORDING

        voiceTimerJob?.cancel()
        voiceTimerJob = coroutineScope.launch {
            while (voiceState == VoiceState.RECORDING) {
                delay(100)
                voiceRecordingDurationSec++
                // Real mic level from the recorder, not random bars.
                val level = engine.currentLevel()
                if (voiceAudioLevels.isNotEmpty()) {
                    for (i in voiceAudioLevels.indices) {
                        voiceAudioLevels[i] = if (i % 2 == 0) level else (level * 0.7f + 0.2f)
                    }
                }
            }
        }
    }

    fun stopVoiceRecordingAndSend() {
        if (voiceState != VoiceState.RECORDING) return
        voiceTimerJob?.cancel()
        val engine = audioEngine ?: return
        val audioBase64 = engine.stopRecordingAndGetBase64()
        voiceState = VoiceState.SENDING

        coroutineScope.launch {
            if (audioBase64 == null) {
                voiceState = VoiceState.IDLE
                voiceLiveTranscript = "No audio captured. Please try again."
                return@launch
            }

            // 1) Speech-to-text
            val recognizedPrompt = try {
                WendyApi.transcribeAudio(audioBase64, format = "m4a")
            } catch (e: IOException) {
                voiceState = VoiceState.IDLE
                voiceLiveTranscript = "Couldn't transcribe audio: ${e.message}"
                return@launch
            }.trim()

            if (recognizedPrompt.isEmpty()) {
                voiceState = VoiceState.IDLE
                voiceLiveTranscript = "I couldn't hear anything. Please try again."
                return@launch
            }
            voiceLiveTranscript = recognizedPrompt

            val convId = activeConversationId ?: run {
                val newId = "conv-${UUID.randomUUID().toString().take(8)}"
                val newConv = Conversation(id = newId, title = recognizedPrompt.take(30), modelId = selectedModel.id)
                conversations.add(0, newConv)
                messagesMap[newId] = mutableStateListOf()
                activeConversationId = newId
                newId
            }

            // 2) Append the transcribed user spoken message
            val userMsg = Message(
                id = "vmsg-user-${UUID.randomUUID().toString().take(8)}",
                conversationId = convId,
                role = Role.USER,
                content = recognizedPrompt,
                modality = Modality.VOICE,
                audioDurationSec = (voiceRecordingDurationSec / 10f).coerceAtLeast(0.5f),
                timestamp = System.currentTimeMillis()
            )
            messagesMap[convId]?.add(userMsg)

            // 3) Run through the SAME reply path as text (real Wendy brain).
            voiceState = VoiceState.THINKING
            // Real wall-clock around the actual model round trip -- the
            // same measurement the text path logs for its UsageEvent.
            // STT and TTS are separate calls outside this window and are
            // deliberately not folded into the number.
            val replyStartedAt = System.currentTimeMillis()
            val (replyText, serverUsage) = runVoiceReply(convId, recognizedPrompt)
            val replyDurationMs = (System.currentTimeMillis() - replyStartedAt).toInt()
            if (voiceState != VoiceState.THINKING) return@launch // cancelled

            if (replyText == null || replyText.isBlank()) {
                voiceState = VoiceState.IDLE
                return@launch
            }

            // 4) Text-to-speech and playback
            val mp3 = try {
                WendyApi.synthesizeSpeech(replyText)
            } catch (e: IOException) {
                // TTS failed; leave the text reply visible instead of a crash.
                voiceState = VoiceState.IDLE
                voiceLiveTranscript = ""
                return@launch
            }

            val assistantMsg = Message(
                id = "vmsg-asst-${UUID.randomUUID().toString().take(8)}",
                conversationId = convId,
                role = Role.ASSISTANT,
                content = replyText,
                modality = Modality.VOICE,
                model = serverUsage?.modelId ?: selectedModel.id,
                audioDurationSec = (replyText.split(" ").size * 0.22f).coerceAtLeast(2.0f),
                inputTokens = serverUsage?.promptTokens,
                outputTokens = serverUsage?.completionTokens,
                costUsd = serverUsage?.costUsd,
                timestamp = System.currentTimeMillis()
            )
            messagesMap[convId]?.add(assistantMsg)
            if (serverUsage?.costUsd != null) {
                usageEvents.add(0, UsageEvent(
                    id = System.currentTimeMillis(),
                    model = serverUsage.modelId ?: selectedModel.id,
                    kind = "voice",
                    inputTokens = serverUsage.promptTokens ?: 0,
                    outputTokens = serverUsage.completionTokens ?: 0,
                    costUsd = serverUsage.costUsd,
                    durationMs = replyDurationMs,
                    timestamp = System.currentTimeMillis()
                ))
            }

            // 5) Play the synthesized reply through the speaker.
            voiceLiveTranscript = ""
            voiceState = VoiceState.SPEAKING
            engine.onPlaybackEnd = {
                if (voiceState == VoiceState.SPEAKING) {
                    voiceState = VoiceState.IDLE
                }
            }
            engine.playMp3(mp3)
        }
    }

    /**
     * Runs the transcribed prompt through the normal assistant reply path
     * and returns the final text and server usage (or null if cancelled / failed). Keeps the
     * voice reply identical to a typed message on the same conversation.
     */
    private suspend fun runVoiceReply(convId: String, prompt: String): Pair<String?, ServerUsage?> {
        val messages = messagesMap[convId] ?: return Pair(null, null)
        val assistantMsgId = "msg-${UUID.randomUUID().toString().take(8)}"
        val currentSelectedModel = selectedModel
        val placeholder = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = Role.ASSISTANT,
            content = "",
            modality = Modality.VOICE,
            model = currentSelectedModel.id,
            isStreaming = true,
            timestamp = System.currentTimeMillis()
        )
        messages.add(placeholder)

        var full = ""
        var finalUsage: ServerUsage? = null
        var failureReason: String? = null
        try {
            WendyApi.sendMessage(prompt, currentSelectedModel.id).collect { event ->
                when (event) {
                    is WendyEvent.Delta -> {
                        full = event.text
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) messages[idx] = messages[idx].copy(content = full)
                    }
                    is WendyEvent.Final -> {
                        full = event.text
                        finalUsage = event.usage
                    }
                    is WendyEvent.Error -> {
                        // This text-mode placeholder gets removed below
                        // regardless of outcome (the voice path shows its
                        // own VOICE-modality message instead) -- writing
                        // the error here and then deleting it was real
                        // dead code. voiceLiveTranscript is the overlay's
                        // only actual message surface; that's what the
                        // caller needs, not this placeholder.
                        failureReason = event.message
                        full = ""
                    }
                }
            }
        } catch (e: Exception) {
            failureReason = e.message ?: "unknown error"
            full = ""
        }
        if (full.isBlank()) {
            voiceLiveTranscript = "Couldn't reach Wendy: ${failureReason ?: "no reply"}"
        }

        val idx = messages.indexOfFirst { it.id == assistantMsgId }
        if (idx != -1) {
            messages[idx] = messages[idx].copy(
                content = full,
                isStreaming = false,
                isError = full.isBlank(),
                model = finalUsage?.modelId ?: currentSelectedModel.id,
                inputTokens = finalUsage?.promptTokens,
                outputTokens = finalUsage?.completionTokens,
                costUsd = finalUsage?.costUsd
            )
        }
        // runVoiceReply appends the assistant message itself; the voice
        // pipeline adds a separate VOICE modality copy, so remove the text
        // placeholder to avoid duplication.
        messages.removeAll { it.id == assistantMsgId }
        return if (full.isBlank()) Pair(null, null) else Pair(full, finalUsage)
    }

    fun bargeInStopPlayback() {
        if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING) {
            audioEngine?.stopPlayback()
            voiceState = VoiceState.IDLE
            voiceLiveTranscript = ""
        }
    }

    fun cancelVoiceTurn() {
        voiceTimerJob?.cancel()
        audioEngine?.stopPlayback()
        audioEngine?.cancelRecording()
        voiceState = VoiceState.IDLE
        voiceLiveTranscript = ""
    }


    fun addMemory(fact: String, category: String = "Preference") {
        if (fact.isBlank()) return
        memories.add(0, MemoryFact(
            id = "mem-${UUID.randomUUID().toString().take(8)}",
            content = fact.trim(),
            category = category,
            sourceConversationTitle = "Manual Entry",
            createdAt = System.currentTimeMillis()
        ))
    }

    fun deleteMemory(id: String) {
        memories.removeAll { it.id == id }
    }
}

@Composable
fun rememberFestoAppState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialBackendMode: BackendMode = BackendMode.GEN1
): FestoAppState {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val audioEngine = remember(context) { VoiceAudioEngine(context) }
    val themePrefs = remember(context) { ThemePreferences(context) }
    val backendPrefs = remember(context) { BackendPreferences(context) }
    return remember {
        FestoAppState(coroutineScope, audioEngine, themePrefs, initialThemeMode, backendPrefs, initialBackendMode)
    }
}
