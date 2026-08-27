package com.example.data

enum class CostTier(val label: String, val symbols: String) {
    FAST("Fast", "$"),
    ECONOMY("Economy", "$"),
    STANDARD("Standard", "$$"),
    PREMIUM("Premium", "$$$")
}

data class ModelOption(
    val id: String,
    val name: String,
    val provider: String,
    val contextLength: Int,
    val contextDisplay: String,
    val inputPricePerM: Double,
    val outputPricePerM: Double,
    val tier: CostTier,
    val tag: String? = null,
    val description: String,
    val isDefault: Boolean = false,
    val notice: String? = null
)
