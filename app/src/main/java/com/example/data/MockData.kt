package com.example.data

object MockData {
    val models: List<ModelOption> = listOf(
        ModelOption(
            id = "google/gemini-2.5-flash",
            name = "Gemini 2.5 Flash",
            provider = "Google",
            contextLength = 1048576,
            contextDisplay = "1M tokens",
            inputPricePerM = 0.30,
            outputPricePerM = 2.50,
            tier = CostTier.FAST,
            tag = "Default",
            description = "Fast, multimodal, high throughput general intelligence.",
            isDefault = true
        ),
        ModelOption(
            id = "anthropic/claude-haiku-4.5",
            name = "Claude Haiku 4.5",
            provider = "Anthropic",
            contextLength = 200000,
            contextDisplay = "200K tokens",
            inputPricePerM = 1.00,
            outputPricePerM = 5.00,
            tier = CostTier.FAST,
            tag = "Fast Writing",
            description = "Ultra-fast response latency with polished reasoning and writing tone."
        ),
        ModelOption(
            id = "anthropic/claude-sonnet-4.5",
            name = "Claude Sonnet 4.5",
            provider = "Anthropic",
            contextLength = 1000000,
            contextDisplay = "1M tokens",
            inputPricePerM = 3.00,
            outputPricePerM = 15.00,
            tier = CostTier.PREMIUM,
            tag = "Best Quality",
            description = "Highest reasoning accuracy, deep code synthesis, and analytical nuance."
        ),
        ModelOption(
            id = "openai/gpt-5.1",
            name = "GPT-5.1",
            provider = "OpenAI",
            contextLength = 400000,
            contextDisplay = "400K tokens",
            inputPricePerM = 1.25,
            outputPricePerM = 10.00,
            tier = CostTier.STANDARD,
            tag = "Generalist",
            description = "Strong versatile problem solver across structured data and domain logic."
        ),
        ModelOption(
            id = "deepseek/deepseek-chat",
            name = "DeepSeek Chat",
            provider = "DeepSeek",
            contextLength = 163840,
            contextDisplay = "164K tokens",
            inputPricePerM = 0.26,
            outputPricePerM = 1.03,
            tier = CostTier.ECONOMY,
            tag = "Cost Efficient",
            description = "High efficiency mathematical and coding reasoning at low cost."
        ),
        ModelOption(
            id = "z-ai/glm-5.3-flash",
            name = "GLM 5.3 Flash",
            provider = "Z-AI",
            contextLength = 1048576,
            contextDisplay = "1M tokens",
            inputPricePerM = 0.075,
            outputPricePerM = 0.25,
            tier = CostTier.ECONOMY,
            tag = "Cheapest",
            description = "Exceptional economic throughput with full 1M context window capacity."
        )
    )

    fun getDefaultModel(): ModelOption = models.first { it.isDefault }

    fun findModel(id: String): ModelOption = models.find { it.id == id } ?: getDefaultModel()

    val initialConversations: List<Conversation> = listOf(
        Conversation(
            id = "conv-1",
            title = "Mobile Audio Protocol & PCM16",
            modelId = "google/gemini-2.5-flash",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 45,
            updatedAt = System.currentTimeMillis() - 1000 * 60 * 12,
            preview = "24kHz mono PCM16 streams chunk-by-chunk for low latency speech playback.",
            messageCount = 4
        ),
        Conversation(
            id = "conv-2",
            title = "Minimalist Design System Spec",
            modelId = "anthropic/claude-sonnet-4.5",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 5,
            updatedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
            preview = "Using Brand Nova #C96F4A with warm brown hairline borders and radial glow.",
            messageCount = 6
        ),
        Conversation(
            id = "conv-3",
            title = "PostgreSQL pgvector Memory Recall",
            modelId = "openai/gpt-5.1",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 28,
            updatedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 26,
            preview = "HNSW cosine distance index over 1536-dimensional embeddings for memory distillation.",
            messageCount = 3
        )
    )

    val initialMessages: Map<String, List<Message>> = mapOf(
        "conv-1" to listOf(
            Message(
                id = "m1-1",
                conversationId = "conv-1",
                role = Role.USER,
                content = "How should we handle incoming PCM16 audio chunks on Android to minimize latency?",
                modality = Modality.TEXT,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 40
            ),
            Message(
                id = "m1-2",
                conversationId = "conv-1",
                role = Role.ASSISTANT,
                content = """To ensure stutter-free, near-instant audio playback on Android:

1. **AudioTrack Streaming**: Use `AudioTrack` in `MODE_STREAM` configured with `AudioFormat.ENCODING_PCM_16BIT` at **24,000 Hz** mono.
2. **Buffer Queue**: Start playing on the very first decoded 1-2 chunks rather than buffering the entire turn.
3. **Barge-in Support**: Call `audioTrack.pause()` and `audioTrack.flush()` immediately when the user taps anywhere during playback.""",
                modality = Modality.TEXT,
                model = "google/gemini-2.5-flash",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 39,
                inputTokens = 248,
                outputTokens = 132,
                costUsd = 0.000404
            ),
            Message(
                id = "m1-3",
                conversationId = "conv-1",
                role = Role.USER,
                content = "Understood. Let's make sure the mic recording also uses standard 16kHz or 24kHz WAV.",
                modality = Modality.VOICE,
                audioDurationSec = 3.8f,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 15
            ),
            Message(
                id = "m1-4",
                conversationId = "conv-1",
                role = Role.ASSISTANT,
                content = "Confirmed! Spoken input is encoded as standard WAV audio, transcribed on the fly, and attached to this thread with unified memory persistence.",
                modality = Modality.VOICE,
                model = "google/gemini-2.5-flash",
                audioDurationSec = 4.2f,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 12,
                inputTokens = 180,
                outputTokens = 42,
                costUsd = 0.000159
            )
        ),
        "conv-2" to listOf(
            Message(
                id = "m2-1",
                conversationId = "conv-2",
                role = Role.USER,
                content = "Outline the key principles of our minimalist design system.",
                modality = Modality.TEXT,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 5
            ),
            Message(
                id = "m2-2",
                conversationId = "conv-2",
                role = Role.ASSISTANT,
                content = """### Festo Mobile Visual Identity
- **Palette**: Signature Brand Nova (`#C96F4A`) paired with warm-brown hairline borders (`#E6DCD0` light / `#3D3128` dark).
- **Typography**: Clean display weights with spacious line height and letterspaced section headers.
- **Hierarchy**: Restrained surfaces (`#FFFFFF` & `#0D0D0D`) let content and assistant reasoning take center stage.""",
                modality = Modality.TEXT,
                model = "anthropic/claude-sonnet-4.5",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 4,
                inputTokens = 310,
                outputTokens = 155,
                costUsd = 0.003255
            )
        ),
        "conv-3" to listOf(
            Message(
                id = "m3-1",
                conversationId = "conv-3",
                role = Role.USER,
                content = "Why do we pin pgvector embedding vectors to 1536 dimensions?",
                modality = Modality.TEXT,
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 28
            ),
            Message(
                id = "m3-2",
                conversationId = "conv-3",
                role = Role.ASSISTANT,
                content = "OpenAI's `text-embedding-3-small` outputs exactly **1536 dimensions**. This matches pgvector's HNSW index capability (which caps at 2,000 dimensions) and delivers fast sub-millisecond similarity search across conversational memories.",
                modality = Modality.TEXT,
                model = "openai/gpt-5.1",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 26,
                inputTokens = 190,
                outputTokens = 78,
                costUsd = 0.001018
            )
        )
    )

    val initialMemories: List<MemoryFact> = listOf(
        MemoryFact(
            id = "mem-1",
            content = "User prefers Kotlin & Jetpack Compose with Material 3 styling and edge-to-edge layouts.",
            category = "Preference",
            sourceConversationTitle = "Architecture & Setup",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 48
        ),
        MemoryFact(
            id = "mem-2",
            content = "Voice protocol uses 24kHz mono PCM16 chunks with immediate barge-in on playback.",
            category = "Decision",
            sourceConversationTitle = "Mobile Audio Protocol",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 24
        ),
        MemoryFact(
            id = "mem-3",
            content = "Backend environment runs Debian 12 with PostgreSQL 16 + pgvector on Hetzner Cloud.",
            category = "Profile",
            sourceConversationTitle = "Server Infrastructure",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 72
        ),
        MemoryFact(
            id = "mem-4",
            content = "Default model is Gemini 2.5 Flash ($0.30 in / $2.50 out per million tokens).",
            category = "Preference",
            sourceConversationTitle = "Model Selection",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 12
        )
    )

    val initialUsageEvents: List<UsageEvent> = listOf(
        UsageEvent(
            id = 1L,
            model = "google/gemini-2.5-flash",
            kind = "chat",
            inputTokens = 248,
            outputTokens = 132,
            costUsd = 0.000404,
            durationMs = 940,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 39
        ),
        UsageEvent(
            id = 2L,
            model = "google/gemini-2.5-flash",
            kind = "voice",
            inputTokens = 180,
            outputTokens = 42,
            costUsd = 0.000159,
            durationMs = 1250,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 12
        ),
        UsageEvent(
            id = 3L,
            model = "anthropic/claude-sonnet-4.5",
            kind = "chat",
            inputTokens = 310,
            outputTokens = 155,
            costUsd = 0.003255,
            durationMs = 2100,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 4
        ),
        UsageEvent(
            id = 4L,
            model = "openai/gpt-5.1",
            kind = "chat",
            inputTokens = 190,
            outputTokens = 78,
            costUsd = 0.001018,
            durationMs = 1450,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 26
        )
    )
}
