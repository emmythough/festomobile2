package com.example.ui.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the ```mermaid fence support added to
 * [parseMessageBlocks] (the same fence mechanism that routes ```chart to
 * [MessageBlock.Chart]).
 *
 * Contracts pinned here:
 * 1. A closed ```mermaid fence becomes a [MessageBlock.Mermaid] carrying
 *    the raw diagram source verbatim -- the parser validates nothing;
 *    syntax checking is mermaid.js's job at render time.
 * 2. The streaming contract holds for mermaid too: an unclosed fence
 *    mid-stream parses to true end of input (the `\z` branch), so a
 *    diagram grows as a Mermaid block instead of flickering to code.
 * 3. Only a blank fence body demotes to a Code block (an empty diagram
 *    can never render).
 * 4. [escapeForInlineScript] (the HTML-shell embedding used by
 *    MermaidRenderer) neutralizes `</script>` and friends -- a diagram
 *    containing "</script>" must not be able to terminate the inline
 *    script block early.
 */
class RichMessageMermaidTest {

    // ---- parseMessageBlocks: the mermaid fence branch ----

    @Test
    fun `mermaid fence becomes a Mermaid block with the raw source`() {
        val diagram = """
            flowchart TD
              A[Chat message] --> B{mermaid fence?}
              B -- yes --> C[Render diagram]
              B -- no --> D[Plain markdown]
        """.trimIndent()
        val blocks = parseMessageBlocks("Here is the flow:\n```mermaid\n$diagram\n```\nDone.")
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Markdown)
        assertEquals("Here is the flow:", (blocks[0] as MessageBlock.Markdown).content)
        val mermaid = blocks[1] as MessageBlock.Mermaid
        assertEquals(diagram, mermaid.source)
        assertEquals("Done.", (blocks[2] as MessageBlock.Markdown).content)
    }

    @Test
    fun `unclosed mermaid fence during streaming still parses the partial source`() {
        // The \z branch of the fence regex: a stream caught mid-diagram
        // must render as one growing Mermaid block, not markdown garbage
        // and not a prematurely demoted Code block.
        val blocks = parseMessageBlocks("```mermaid\nflowchart LR\n  Start -->")
        assertEquals(1, blocks.size)
        val mermaid = blocks[0] as MessageBlock.Mermaid
        assertEquals("flowchart LR\n  Start -->", mermaid.source)
    }

    @Test
    fun `mermaid language tag is case-insensitive like the chart branch`() {
        val blocks = parseMessageBlocks("```MERMAID\nflowchart TD\n  A --> B\n```")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Mermaid)
        assertEquals("flowchart TD\n  A --> B", (blocks[0] as MessageBlock.Mermaid).source)
    }

    @Test
    fun `blank mermaid body falls back to a Code block`() {
        val blocks = parseMessageBlocks("```mermaid\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MessageBlock.Code
        assertEquals("", code.code)
        assertEquals("mermaid", code.language)
    }

    @Test
    fun `mermaid source with special characters is preserved verbatim`() {
        // Node labels with quotes, angle brackets, ampersands and arrows
        // must reach the renderer byte-for-byte; quoting for the HTML
        // shell happens later, in escapeForInlineScript.
        val diagram = "A[\"Payload <b>bold</b> & done\"] -->|-->| B{C:\\path}"
        val blocks = parseMessageBlocks("```mermaid\n$diagram\n```")
        assertEquals(1, blocks.size)
        assertEquals(diagram, (blocks[0] as MessageBlock.Mermaid).source)
    }

    // ---- escapeForInlineScript: safe embedding into the WebView HTML ----

    @Test
    fun `script-terminating content is neutralized when embedded`() {
        // "<" -> \u003c keeps a literal "</script>" inside the diagram
        // from ending the inline <script> block the shell embeds it in.
        assertEquals("\\u003c/script>", escapeForInlineScript("</script>"))
    }

    @Test
    fun `quotes backslashes and newlines are escaped for the JS string`() {
        assertEquals("say \\\"hi\\\"", escapeForInlineScript("say \"hi\""))
        assertEquals("C:\\\\path", escapeForInlineScript("C:\\path"))
        assertEquals("line1\\nline2", escapeForInlineScript("line1\nline2"))
        assertEquals("tab\\there", escapeForInlineScript("tab\there"))
    }

    @Test
    fun `plain diagram source passes through the escaper unchanged`() {
        // Single-line on purpose: line breaks are legitimately escaped
        // (see the test above); this pins that ordinary diagram glyphs
        // survive untouched.
        val diagram = "flowchart TD :: A[Chat] --> B{Router}"
        assertEquals(diagram, escapeForInlineScript(diagram))
    }
}
