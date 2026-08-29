package com.example.data

object MockData {
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
