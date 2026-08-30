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

    /** Assistant markdown image ![alt](src) -- the Hermes gateway inlines
     * Wendy's images as data URLs ("data:image/png;base64,..."); http(s)
     * image URLs are supported too. Rendered by MessageImageBlock because
     * the app's Markwon setup has no images plugin. */
    data class Image(val source: String, val alt: String) : MessageBlock()

    /** A bare audio file path Wendy dropped in her reply text (how the
     * gateway surfaces generated audio) -- rendered as a "audio: file"
     * chip by MessageAudioChip, no player this pass. */
    data class AudioFile(val path: String) : MessageBlock()
}

/** `![alt](src)` -- src must be space/paren-free, which both data URLs
 * (base64 + "data:image/...;base64,") and http(s) URLs satisfy.
 * internal (not private) so the Hermes voice-conversation loop's TTS
 * stripper removes exactly the same image tokens the renderer shows. */
internal val markdownImageRegex = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)")

/** A path-like token ending in an audio extension -- how the gateway
 * surfaces generated audio files in reply text. Deliberately conservative:
 * not preceded by "(", a word char, "@" or "." (so sentence words and
 * email-ish strings don't half-match), and not preceded by a URL-ish ":"
 * or a path separator either -- "https://host/song.mp3" and
 * "[listen](files/a.mp3)" must stay one link, not "[listen](https:" plus
 * a bogus "//host/song.mp3" chip, and "C:\music\song.mp3" must not chip
 * as a bare "song.mp3" (the token class crosses "/" and "\", so only the
 * lookbehind can tell). Also not followed by a word char or "." (so
 * "song.mp3." keeps its sentence period). internal (not private) for the
 * same TTS-stripper reuse as above. */
internal val audioPathRegex = Regex(
    "(?<![\\w@(.:/\\\\])[\\w./\\\\-]+\\.(?:mp3|m4a|m4b|wav|ogg|oga|flac|aac|opus|wma)(?![\\w.])",
    RegexOption.IGNORE_CASE
)

/** Splits a plain markdown segment into Markdown / Image / AudioFile
 * blocks. Text without media comes back as a single Markdown block --
 * identical to the pre-media behavior. */
private fun splitMediaBlocks(markdown: String): List<MessageBlock> {
    if (markdown.isBlank()) return emptyList()

    val imageMatches = markdownImageRegex.findAll(markdown).toList()
    if (imageMatches.isEmpty()) {
        return splitAudioBlocks(markdown)
    }

    val blocks = mutableListOf<MessageBlock>()
    var cursor = 0
    for (match in imageMatches) {
        if (match.range.first > cursor) {
            val before = markdown.substring(cursor, match.range.first).trim()
            if (before.isNotEmpty()) {
                blocks.addAll(splitAudioBlocks(before))
            }
        }
        blocks.add(
            MessageBlock.Image(
                source = match.groupValues[2],
                alt = match.groupValues[1].trim()
            )
        )
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) {
        val tail = markdown.substring(cursor).trim()
        if (tail.isNotEmpty()) {
            blocks.addAll(splitAudioBlocks(tail))
        }
    }
    return blocks
}

private fun splitAudioBlocks(markdown: String): List<MessageBlock> {
    val matches = audioPathRegex.findAll(markdown).toList()
    if (matches.isEmpty()) {
        return listOf(MessageBlock.Markdown(markdown))
    }

    val blocks = mutableListOf<MessageBlock>()
    var cursor = 0
    for (match in matches) {
        if (match.range.first > cursor) {
            val before = markdown.substring(cursor, match.range.first).trim()
            if (before.isNotEmpty()) {
                blocks.add(MessageBlock.Markdown(before))
            }
        }
        blocks.add(MessageBlock.AudioFile(path = match.value))
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) {
        val tail = markdown.substring(cursor).trim()
        if (tail.isNotEmpty()) {
            blocks.add(MessageBlock.Markdown(tail))
        }
    }
    return blocks
}

fun parseMessageBlocks(raw: String): List<MessageBlock> {
    if (raw.isBlank()) return emptyList()
    if (!raw.contains("```")) {
        return splitMediaBlocks(raw.trim())
    }

    val blocks = mutableListOf<MessageBlock>()
    // Streaming-safe "closing fence OR the real end of the text": deliberately
    // \\z (true end of input), NOT MULTILINE's $ -- $ under MULTILINE matches
    // before ANY line break, so an unclosed fence during streaming could
    // close itself early at the next newline in the code body, not just when
    // genuinely unterminated. \\z has no such ambiguity.
    val fenceRegex = Regex("```([a-zA-Z0-9_-]*)\\s*\\n?([\\s\\S]*?)(?:```|\\z)")
    var lastIndex = 0

    val matches = fenceRegex.findAll(raw).toList()
    for (match in matches) {
        val start = match.range.first
        if (start > lastIndex) {
            val textBefore = raw.substring(lastIndex, start).trim()
            if (textBefore.isNotEmpty()) {
                blocks.addAll(splitMediaBlocks(textBefore))
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
            blocks.addAll(splitMediaBlocks(remaining))
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
                is MessageBlock.Image -> {
                    MessageImageBlock(source = block.source, alt = block.alt)
                }
                is MessageBlock.AudioFile -> {
                    MessageAudioChip(path = block.path)
                }
            }
        }
    }
}
