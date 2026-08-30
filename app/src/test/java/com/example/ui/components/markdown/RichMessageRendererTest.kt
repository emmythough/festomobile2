package com.example.ui.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [parseMessageBlocks] -- the parser the chat transcript and
 * the Hermes memory browser both render through.
 *
 * Two contracts are pinned here:
 * 1. The pre-Hermes behavior must not regress: plain markdown stays one
 *    block, closed fences become Code, an unclosed fence during streaming
 *    runs to the true end of input (the `\z` contract).
 * 2. The Hermes media blocks split out exactly where intended -- and
 *    crucially NOT inside URLs: `audioPathRegex`'s doc promises markdown
 *    links and URLs don't half-match, which the 1-char lookbehind alone
 *    could not deliver (see the `audio url` tests below -- the first of
 *    them failed before the lookbehind fix).
 */
class RichMessageRendererTest {

    // ---- Gen 1 markdown/Code behavior (must not regress) ----

    @Test
    fun `plain markdown without fences stays a single block`() {
        val blocks = parseMessageBlocks("Hello **world**, see *this*.")
        assertEquals(1, blocks.size)
        val markdown = blocks[0] as MessageBlock.Markdown
        assertEquals("Hello **world**, see *this*.", markdown.content)
    }

    @Test
    fun `closed code fence becomes a Code block between markdown`() {
        val blocks = parseMessageBlocks("before\n```kotlin\nval x = 1\n```\nafter")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Markdown)
        val code = blocks[1] as MessageBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
        assertTrue(blocks[2] is MessageBlock.Markdown)
    }

    @Test
    fun `unclosed fence during streaming runs to end of input`() {
        // The \z branch of the fence regex: a stream caught mid-code-block
        // must render as one growing Code block, not markdown garbage.
        val blocks = parseMessageBlocks("```python\nprint(\"partial")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MessageBlock.Code
        assertEquals("python", code.language)
        assertEquals("print(\"partial", code.code)
    }

    @Test
    fun `blank input yields no blocks`() {
        assertTrue(parseMessageBlocks("   \n  ").isEmpty())
    }

    // ---- Hermes media blocks ----

    @Test
    fun `markdown image with data url becomes an Image block`() {
        val blocks = parseMessageBlocks("![attacking formation](data:image/png;base64,iVBORw0KGgo=)")
        assertEquals(1, blocks.size)
        val image = blocks[0] as MessageBlock.Image
        assertEquals("data:image/png;base64,iVBORw0KGgo=", image.source)
        assertEquals("attacking formation", image.alt)
    }

    @Test
    fun `bare audio path becomes an AudioFile block`() {
        val blocks = parseMessageBlocks("Here you go: outbox/song.mp3 -- enjoy.")
        val audio = blocks.filterIsInstance<MessageBlock.AudioFile>()
        assertEquals(1, audio.size)
        assertEquals("outbox/song.mp3", audio[0].path)
        assertTrue(blocks.any { it is MessageBlock.Markdown })
    }

    @Test
    fun `sentence words and emails do not become audio chips`() {
        val blocks = parseMessageBlocks("contact first.last@example.com about the .wav plans")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Markdown)
    }

    @Test
    fun `audio path followed by a sentence period stays plain text`() {
        // Documented tradeoff of the trailing-period guard: the whole
        // token declines to match rather than eating the period.
        val blocks = parseMessageBlocks("Play outbox/song.mp3.")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Markdown)
    }

    // ---- audioPathRegex vs URLs (the half-match regression) ----

    @Test
    fun `audio url inside a markdown link is not half-matched`() {
        val text = "[listen](https://example.com/audio/song.mp3)"
        val blocks = parseMessageBlocks(text)
        assertEquals(1, blocks.size)
        val markdown = blocks[0] as MessageBlock.Markdown
        assertEquals(text, markdown.content)
    }

    @Test
    fun `bare https audio url is not split into a chip`() {
        val blocks = parseMessageBlocks("https://example.com/song.mp3")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Markdown)
    }

    @Test
    fun `sentence keeps its words when a path-like token appears`() {
        val blocks = parseMessageBlocks("Then outbox/song.mp3 played.")
        assertEquals(3, blocks.size)
        assertEquals("Then", (blocks[0] as MessageBlock.Markdown).content)
        assertEquals("outbox/song.mp3", (blocks[1] as MessageBlock.AudioFile).path)
        assertEquals("played.", (blocks[2] as MessageBlock.Markdown).content)
    }
}
