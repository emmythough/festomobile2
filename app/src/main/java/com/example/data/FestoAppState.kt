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
    private val audioEngine: VoiceAudioEngine? = null
) {
    companion object {
        private const val MAIN_CONVERSATION_ID = "wendy-main"
        private val FALLBACK_MODEL = ModelOption(
            id = "voice",
            label = "Balanced",
            modelId = "openrouter/google/gemini-3.7-flash",
            inputCostPerMtok = 0.375,
            outputCostPerMtok = 1.875,
            isDefault = true
        )
    }

    init {
        coroutineScope.launch { loadRealModels() }
        coroutineScope.launch { loadRealHistory() }
    }

    private suspend fun loadRealModels() {
        val models = WendyApi.fetchModels()
        if (models.isNotEmpty()) {
            availableModels.clear()
            availableModels.addAll(models)
            val default = models.firstOrNull { it.isDefault } ?: models.first()
            selectedModel = default
        }
    }

    private suspend fun loadRealHistory() {
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
        isHistoryLoading = false
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
    val memories = mutableStateListOf<MemoryFact>().apply { addAll(MockData.initialMemories) }
    var memorySearchQuery by mutableStateOf("")

    // Usage & Cost tracking
    val usageEvents = mutableStateListOf<UsageEvent>().apply { addAll(MockData.initialUsageEvents) }

    // UI Layer Flags
    var isDrawerOpen by mutableStateOf(false)
    var isModelSheetOpen by mutableStateOf(false)
    var isMemorySheetOpen by mutableStateOf(false)
    var isUsageSheetOpen by mutableStateOf(false)
    var isVoiceOverlayOpen by mutableStateOf(false)

    // Search & Filter in drawer
    var conversationSearchQuery by mutableStateOf("")

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

    fun selectConversation(id: String) {
        activeConversationId = id
        isDrawerOpen = false
        val conv = conversations.find { it.id == id }
        if (conv != null) {
            selectedModel = availableModels.find { it.id == conv.modelId } ?: selectedModel
        }
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

    fun createNewConversation(initialPrompt: String? = null) {
        val newId = "conv-${UUID.randomUUID().toString().take(8)}"
        val title = if (!initialPrompt.isNullOrBlank()) {
            initialPrompt.take(30).trim() + if (initialPrompt.length > 30) "..." else ""
        } else {
            "New Conversation"
        }
        val newConv = Conversation(
            id = newId,
            title = title,
            modelId = selectedModel.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversations.add(0, newConv)
        messagesMap[newId] = mutableStateListOf()
        activeConversationId = newId
        isDrawerOpen = false

        if (!initialPrompt.isNullOrBlank()) {
            sendMessage(initialPrompt)
        }
    }

    fun deleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        messagesMap.remove(id)
        if (activeConversationId == id) {
            activeConversationId = conversations.firstOrNull()?.id
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index != -1 && newTitle.isNotBlank()) {
            conversations[index] = conversations[index].copy(title = newTitle.trim())
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isStreamingResponse) return

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
            timestamp = System.currentTimeMillis()
        )
        convMessages.add(userMessage)

        // Stream assistant reply
        streamAssistantResponse(targetConvId, text)
    }

    private fun streamAssistantResponse(convId: String, userPrompt: String) {
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

            WendyApi.sendMessage(userPrompt, currentSelectedModel.id).collect { event ->
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
            val (replyText, serverUsage) = runVoiceReply(convId, recognizedPrompt)
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
                    durationMs = (replyText.split(" ").size * 180),
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
                        val idx = messages.indexOfFirst { it.id == assistantMsgId }
                        if (idx != -1) messages[idx] = messages[idx].copy(content = "Couldn't reach Wendy: ${event.message}")
                        full = ""
                    }
                }
            }
        } catch (e: Exception) {
            full = ""
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
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): FestoAppState {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val audioEngine = remember(context) { VoiceAudioEngine(context) }
    return remember { FestoAppState(coroutineScope, audioEngine) }
}
