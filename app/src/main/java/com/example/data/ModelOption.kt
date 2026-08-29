package com.example.data

enum class CostTier(val label: String, val symbols: String) {
    FAST("Fast", "$"),
    BALANCED("Balanced", "$$"),
    DEEP("Deep reasoning", "$$$")
}

/**
 * A selectable model, as reported by GET /api/model (Gen 1's real,
 * shared-with-Telegram model list -- bridge.py's own VALID_MODELS/
 * _MODEL_MAP, not an invented catalogue). `id` is the short key
 * ("flash", "haiku", ...) that both this app and Telegram's /model
 * command use; `modelId` is the real underlying OpenRouter id
 * ("openrouter/google/gemini-3.7-flash").
 *
 * There is no static per-tier pricing here on purpose: Gen 1 doesn't
 * quote a price up front the way v4's tiers briefly did. Real cost for
 * a turn comes back on ServerUsage after that turn completes -- inputCostPerMtok/
 * outputCostPerMtok below are 0.0 placeholders kept only so the existing
 * picker UI (which still reads them) doesn't need a redesign tonight;
 * they should be treated as "unknown ahead of time," not "free."
 */
data class ModelOption(
    val id: String,
    val modelId: String,
    val isDefault: Boolean = false,
    val inputCostPerMtok: Double = 0.0,
    val outputCostPerMtok: Double = 0.0,
    val tier: CostTier = when (id) {
        "flash", "luna", "ox-alpha" -> CostTier.FAST
        "haiku" -> CostTier.BALANCED
        "sonnet", "deepseek" -> CostTier.DEEP
        else -> CostTier.BALANCED
    },
    val description: String = when (id) {
        "flash" -> "Fast, capable default -- Gemini 3.7 Flash."
        "luna" -> "Cheapest fast option, still being battery-tested."
        "ox-alpha" -> "Free/experimental. See your notes on its data terms before sharing anything private."
        "haiku" -> "Claude Haiku 4.5 -- quick, balanced everyday model."
        "sonnet" -> "Claude Sonnet 4.5 -- deeper reasoning, slower and costlier."
        "deepseek" -> "DeepSeek Chat -- an alternate deep-reasoning option."
        else -> modelId
    }
) {
    val label: String get() = when (id) {
        "flash" -> "Flash"
        "luna" -> "Luna"
        "ox-alpha" -> "Ox-Alpha"
        "haiku" -> "Haiku"
        "sonnet" -> "Sonnet"
        "deepseek" -> "DeepSeek"
        else -> id.replaceFirstChar { it.uppercase() }
    }
    val name: String get() = label
    val contextDisplay: String get() = "Shared with Telegram"
}
