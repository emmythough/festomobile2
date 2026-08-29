package com.example.data

enum class CostTier(val label: String, val symbols: String) {
    FAST("Fast", "$"),
    BALANCED("Balanced", "$$"),
    DEEP("Deep reasoning", "$$$")
}

data class ModelOption(
    val id: String,
    val label: String,
    val modelId: String,
    val inputCostPerMtok: Double,
    val outputCostPerMtok: Double,
    val isDefault: Boolean = false,
    val tier: CostTier = when (id) {
        "reflex" -> CostTier.FAST
        "voice" -> CostTier.BALANCED
        "deep" -> CostTier.DEEP
        else -> CostTier.BALANCED
    },
    val description: String = when (id) {
        "reflex" -> "Ultra-fast response latency with lightweight reasoning."
        "voice" -> "Optimized balance of speed, tone, and intelligence."
        "deep" -> "Deep analytical reasoning, multi-step code synthesis, and nuance."
        else -> modelId
    }
) {
    val name: String get() = label
    val contextDisplay: String get() = "1M tokens"
}
