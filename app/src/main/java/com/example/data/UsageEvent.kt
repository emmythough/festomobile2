package com.example.data

data class UsageEvent(
    val id: Long,
    val model: String,
    val kind: String, // "chat", "voice", "embedding"
    val inputTokens: Int,
    val outputTokens: Int,
    val costUsd: Double,
    val durationMs: Int,
    val timestamp: Long = System.currentTimeMillis()
)
