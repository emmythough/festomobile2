package com.example.data

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class Modality {
    TEXT,
    VOICE
}

data class Message(
    val id: String,
    val conversationId: String,
    val role: Role,
    val content: String,
    val modality: Modality = Modality.TEXT,
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val costUsd: Double? = null,
    val audioDurationSec: Float? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    /** Set on a user message that carried a file attachment -- display
     * only, the actual bytes already left the device with the request. */
    val attachmentFilename: String? = null
)
