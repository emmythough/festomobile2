package com.example.data

data class MemoryFact(
    val id: String,
    val content: String,
    val category: String, // "Preference", "Project", "Profile", "Decision"
    val sourceConversationTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
