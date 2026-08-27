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
import java.util.UUID

enum class AuthMode {
    SIGN_IN,
    CREATE_ACCOUNT
}

@Stable
class FestoAppState(
    val coroutineScope: CoroutineScope
) {
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

    val conversations = mutableStateListOf<Conversation>().apply { addAll(MockData.initialConversations) }
    var activeConversationId by mutableStateOf<String?>(MockData.initialConversations.firstOrNull()?.id)
        private set

    // Messages grouped by conversation ID
    val messagesMap = mutableStateMapOf<String, MutableList<Message>>().apply {
        MockData.initialMessages.forEach { (convId, list) ->
            put(convId, mutableStateListOf<Message>().apply { addAll(list) })
        }
    }

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
            val fullResponse = generateIntelligentResponse(userPrompt, selectedModel)
            val tokens = fullResponse.split(" ")
            var currentContent = ""

            for (i in tokens.indices) {
                delay(35) // Realistic token stream cadence
                currentContent += (if (i == 0) "" else " ") + tokens[i]
                val msgIndex = convMessages.indexOfFirst { it.id == assistantMsgId }
                if (msgIndex != -1) {
                    convMessages[msgIndex] = convMessages[msgIndex].copy(
                        content = currentContent,
                        isStreaming = true
                    )
                }
            }

            // Finalize message with usage metrics
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

            // Log usage event
            val event = UsageEvent(
                id = System.currentTimeMillis(),
                model = selectedModel.id,
                kind = "chat",
                inputTokens = inTokens,
                outputTokens = outTokens,
                costUsd = cost,
                durationMs = (tokens.size * 35),
                timestamp = System.currentTimeMillis()
            )
            usageEvents.add(0, event)

            // Distill memory if notable
            maybeDistillMemory(userPrompt, fullResponse, activeConversation?.title)

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

    private fun generateIntelligentResponse(prompt: String, model: ModelOption): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("spec") || lower.contains("architecture") ->
                "### Architectural Summary\n\n- **Client**: Native Jetpack Compose mobile shell with M3 dynamic theming & Brand Nova (`#C96F4A`).\n- **Voice**: 24kHz mono PCM16 audio pipeline with instant barge-in support.\n- **Vector Memory**: PostgreSQL `pgvector` HNSW cosine indexing at 1536 dimensions.\n- **Security**: OpenRouter API key strictly guarded server-side behind JWT auth."

            lower.contains("model") || lower.contains("cost") || lower.contains("compare") ->
                "Currently using **${model.name}** (${model.tier.label} tier, ${model.contextDisplay} context).\n\n- **Input**: $${model.inputPricePerM} / Mtok\n- **Output**: $${model.outputPricePerM} / Mtok\n- **Capability**: ${model.description}\n\nYou can switch models at any time from the top bar chip; history and memory remain unified."

            lower.contains("voice") || lower.contains("audio") ->
                "Voice and text exist as **one unified conversation**. When you speak, audio is processed, transcribed, and saved directly to the active thread history so you can alternate seamlessly between typing and speaking."

            else ->
                "I've processed your request using **${model.name}**. With our cross-conversation memory layer and unified text-voice state, all context remains synced across your active workspace."
        }
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
