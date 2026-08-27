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
import java.util.UUID

enum class AuthMode {
    SIGN_IN,
    CREATE_ACCOUNT
}

@Stable
class FestoAppState(
    val coroutineScope: CoroutineScope
) {
    companion object {
        private const val MAIN_CONVERSATION_ID = "wendy-main"
    }

    init {
        coroutineScope.launch { loadRealHistory() }
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
    val availableModels = mutableStateListOf<ModelOption>().apply { addAll(MockData.models) }
    var selectedModel by mutableStateOf(MockData.getDefaultModel())
        private set

    // Real Wendy has one continuous conversation shared with Telegram, not
    // separate per-device threads -- so there's exactly one conversation
    // here, seeded empty and filled in from GET /api/history below rather
    // than from MockData.
    val conversations = mutableStateListOf(
        Conversation(id = MAIN_CONVERSATION_ID, title = "Wendy", modelId = MockData.getDefaultModel().id)
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
            selectedModel = MockData.findModel(conv.modelId)
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
        val placeholderMsg = Message(
            id = assistantMsgId,
            conversationId = convId,
            role = Role.ASSISTANT,
            content = "",
            modality = Modality.TEXT,
            model = selectedModel.id,
            isStreaming = true,
            timestamp = System.currentTimeMillis()
        )
        val convMessages = messagesMap[convId] ?: return
        convMessages.add(placeholderMsg)

        streamingJob = coroutineScope.launch {
            var fullResponse = ""
            var errorMessage: String? = null
            val startedAt = System.currentTimeMillis()

            WendyApi.sendMessage(userPrompt).collect { event ->
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
                    is WendyEvent.Final -> fullResponse = event.text
                    is WendyEvent.Error -> errorMessage = event.message
                }
            }

            if (errorMessage != null && fullResponse.isBlank()) {
                fullResponse = "Couldn't reach Wendy: $errorMessage"
            }

            // Real token counts aren't returned by the API yet -- these are
            // the same length-based estimates the mock used, kept only for
            // the usage/cost UI until the server reports real usage.
            val inTokens = (userPrompt.length / 3.8).toInt().coerceAtLeast(15)
            val outTokens = (fullResponse.length / 3.6).toInt().coerceAtLeast(25)
            val cost = (inTokens * selectedModel.inputPricePerM + outTokens * selectedModel.outputPricePerM) / 1_000_000.0

            val msgIndex = convMessages.indexOfFirst { it.id == assistantMsgId }
            if (msgIndex != -1) {
                convMessages[msgIndex] = convMessages[msgIndex].copy(
                    content = fullResponse,
                    isStreaming = false,
                    inputTokens = inTokens,
                    outputTokens = outTokens,
                    costUsd = cost
                )
            }

            if (errorMessage == null) {
                val event = UsageEvent(
                    id = System.currentTimeMillis(),
                    model = selectedModel.id,
                    kind = "chat",
                    inputTokens = inTokens,
                    outputTokens = outTokens,
                    costUsd = cost,
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

    // Voice Engine Simulation & State Transitions
    fun startVoiceRecording() {
        if (voiceState != VoiceState.IDLE) return
        voiceState = VoiceState.RECORDING
        voiceLiveTranscript = ""
        voiceRecordingDurationSec = 0

        voiceTimerJob?.cancel()
        voiceTimerJob = coroutineScope.launch {
            while (voiceState == VoiceState.RECORDING) {
                delay(100)
                voiceRecordingDurationSec++
                // Dynamic audio visualizer level simulation
                for (i in voiceAudioLevels.indices) {
                    voiceAudioLevels[i] = (0.2f + Math.random().toFloat() * 0.75f).coerceIn(0.1f, 1.0f)
                }
            }
        }
    }

    fun stopVoiceRecordingAndSend() {
        if (voiceState != VoiceState.RECORDING) return
        voiceTimerJob?.cancel()
        voiceState = VoiceState.SENDING

        coroutineScope.launch {
            delay(500)
            voiceState = VoiceState.THINKING

            val simulatedSpokenInputs = listOf(
                "Can you summarize the difference between Gemini 2.5 Flash and Claude Sonnet 4.5?",
                "What is our strategy for handling real-time audio playback buffering?",
                "Let's review the memory recall system and vector index dimension rules.",
                "How does the model picker persist choices across conversations?"
            )
            val recognizedPrompt = simulatedSpokenInputs.random()
            val convId = activeConversationId ?: run {
                val newId = "conv-${UUID.randomUUID().toString().take(8)}"
                val newConv = Conversation(id = newId, title = recognizedPrompt.take(30), modelId = selectedModel.id)
                conversations.add(0, newConv)
                messagesMap[newId] = mutableStateListOf()
                activeConversationId = newId
                newId
            }

            // Append user spoken message
            val userMsg = Message(
                id = "vmsg-user-${UUID.randomUUID().toString().take(8)}",
                conversationId = convId,
                role = Role.USER,
                content = recognizedPrompt,
                modality = Modality.VOICE,
                audioDurationSec = (voiceRecordingDurationSec / 10f).coerceAtLeast(2.0f),
                timestamp = System.currentTimeMillis()
            )
            messagesMap[convId]?.add(userMsg)

            delay(700)
            voiceState = VoiceState.SPEAKING
            voiceLiveTranscript = ""

            val spokenReply = generateIntelligentVoiceReply(recognizedPrompt, selectedModel)
            val words = spokenReply.split(" ")

            for (i in words.indices) {
                if (voiceState != VoiceState.SPEAKING) break // Barge-in check
                delay(180) // Spoken word rate (~150-180ms per word)
                voiceLiveTranscript += (if (i == 0) "" else " ") + words[i]
            }

            if (voiceState == VoiceState.SPEAKING) {
                // Completed spoken reply
                val inTokens = (recognizedPrompt.length / 3.8).toInt().coerceAtLeast(15)
                val outTokens = (spokenReply.length / 3.6).toInt().coerceAtLeast(20)
                val cost = (inTokens * selectedModel.inputPricePerM + outTokens * selectedModel.outputPricePerM) / 1_000_000.0

                val assistantMsg = Message(
                    id = "vmsg-asst-${UUID.randomUUID().toString().take(8)}",
                    conversationId = convId,
                    role = Role.ASSISTANT,
                    content = spokenReply,
                    modality = Modality.VOICE,
                    model = selectedModel.id,
                    audioDurationSec = (words.size * 0.22f).coerceAtLeast(3.0f),
                    inputTokens = inTokens,
                    outputTokens = outTokens,
                    costUsd = cost,
                    timestamp = System.currentTimeMillis()
                )
                messagesMap[convId]?.add(assistantMsg)

                usageEvents.add(0, UsageEvent(
                    id = System.currentTimeMillis(),
                    model = selectedModel.id,
                    kind = "voice",
                    inputTokens = inTokens,
                    outputTokens = outTokens,
                    costUsd = cost,
                    durationMs = (words.size * 180),
                    timestamp = System.currentTimeMillis()
                ))

                delay(800)
                voiceState = VoiceState.IDLE
            }
        }
    }

    fun bargeInStopPlayback() {
        if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING) {
            voiceState = VoiceState.IDLE
            voiceLiveTranscript = ""
        }
    }

    fun cancelVoiceTurn() {
        voiceTimerJob?.cancel()
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

    private fun generateIntelligentVoiceReply(prompt: String, model: ModelOption): String {
        return "I heard your spoken request. Using ${model.name}, I've processed the context and updated our shared memory log."
    }
}

@Composable
fun rememberFestoAppState(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): FestoAppState {
    return remember { FestoAppState(coroutineScope) }
}
