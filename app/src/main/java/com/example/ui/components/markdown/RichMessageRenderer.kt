package com.example.ui.components.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed class MessageBlock {
    data class Markdown(val content: String) : MessageBlock()
    data class Code(val code: String, val language: String) : MessageBlock()
    data class Chart(val spec: ChartSpec, val fallbackRaw: String) : MessageBlock()
}

fun parseMessageBlocks(raw: String): List<MessageBlock> {
    if (raw.isBlank()) return emptyList()
    if (!raw.contains("```")) {
        return listOf(MessageBlock.Markdown(raw.trim()))
    }

    val blocks = mutableListOf<MessageBlock>()
    val fenceRegex = Regex("```([a-zA-Z0-9_-]*)\\s*\\n?([\\s\\S]*?)(?:```|\$)", RegexOption.MULTILINE)
    var lastIndex = 0

    val matches = fenceRegex.findAll(raw).toList()
    for (match in matches) {
        val start = match.range.first
        if (start > lastIndex) {
            val textBefore = raw.substring(lastIndex, start).trim()
            if (textBefore.isNotEmpty()) {
                blocks.add(MessageBlock.Markdown(textBefore))
            }
        }

        val lang = match.groupValues.getOrNull(1)?.trim()?.lowercase() ?: ""
        val codeBody = match.groupValues.getOrNull(2)?.trimEnd() ?: ""

        if (lang == "chart") {
            val spec = parseChartSpec(codeBody)
            if (spec != null) {
                blocks.add(MessageBlock.Chart(spec, codeBody))
            } else {
                // Fallback to standard code block if chart JSON is invalid or incomplete
                blocks.add(MessageBlock.Code(codeBody, "chart"))
            }
        } else {
            blocks.add(MessageBlock.Code(codeBody, lang.ifBlank { "text" }))
        }

        lastIndex = match.range.last + 1
    }

    if (lastIndex < raw.length) {
        val remaining = raw.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            blocks.add(MessageBlock.Markdown(remaining))
        }
    }

    return if (blocks.isEmpty()) listOf(MessageBlock.Markdown(raw)) else blocks
}

@Composable
fun RichMessageRenderer(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseMessageBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MessageBlock.Markdown -> {
                    MarkdownTextView(markdown = block.content)
                }
                is MessageBlock.Code -> {
                    CodeBlockView(code = block.code, language = block.language)
                }
                is MessageBlock.Chart -> {
                    MessageChartBlock(spec = block.spec)
                }
            }
        }
    }
}
