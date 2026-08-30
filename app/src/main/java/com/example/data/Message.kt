package com.example.data

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM
}

data class Message(
    val id: String,
    val conversationId: String,
    val role: Role,
    val content: String,
    /** The model that actually produced this reply, as reported by the
     * gateway's usage frames. Null while streaming / when the gateway
     * didn't report one. */
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val costUsd: Double? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    /** Set on a user message that carried a photo -- display only, the
     * actual bytes already left the device with the request. */
    val attachmentFilename: String? = null
)
