package com.example.data

data class Conversation(
    val id: String,
    val title: String,
    val modelId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val preview: String = "",
    val messageCount: Int = 0,
    val isArchived: Boolean = false
)
