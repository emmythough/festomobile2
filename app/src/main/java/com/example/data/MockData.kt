package com.example.data

object MockData {
    val initialConversations: List<Conversation> = listOf(
        Conversation(
            id = "conv-1",
            title = "Mobile Audio Protocol & PCM16",
            modelId = "voice",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 45,
            updatedAt = System.currentTimeMillis() - 1000 * 60 * 12,
            preview = "24kHz mono PCM16 streams chunk-by-chunk for low latency speech playback.",
            messageCount = 4
        ),
        Conversation(
            id = "conv-2",
            title = "Minimalist Design System Spec",
            modelId = "deep",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 5,
            updatedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 2,
            preview = "Using Brand Nova #C96F4A with warm brown hairline borders and radial glow.",
            messageCount = 2
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
                model = "voice",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 39
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
- **Hierarchy**: Restrained surfaces let content and assistant reasoning take center stage.""",
                modality = Modality.TEXT,
                model = "deep",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 4
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
            content = "Backend environment runs Debian 12 with PostgreSQL 16 on Hetzner Cloud.",
            category = "Profile",
            sourceConversationTitle = "Server Infrastructure",
            createdAt = System.currentTimeMillis() - 1000 * 60 * 60 * 72
        )
    )

    val initialUsageEvents: List<UsageEvent> = emptyList()
}
