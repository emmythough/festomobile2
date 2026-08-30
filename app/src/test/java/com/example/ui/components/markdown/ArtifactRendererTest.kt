package com.example.ui.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the HTML assembly in [buildArtifactHtml] -- the two input
 * branches of the ```artifact lane (see [MessageBlock.Artifact]):
 *
 * 1. Full-document detection: a payload containing "<html" (case-
 *    insensitive) is treated as a complete document and gets the bootstrap
 *    script spliced before its LAST "</body>" (else last "</html>", else
 *    appended at the very end) -- never silently dropped.
 * 2. Fragment wrapping: anything else is wrapped in a shell carrying the
 *    theme palette as CSS custom properties (the same MermaidPalette
 *    values MermaidRenderer bakes in) plus the bootstrap script.
 *
 * Plus the escape-hatch edge case: a plain-looking fragment that smuggles
 * a literal "</body>" or "</html>" must route to full-document handling
 * instead of being wrapped into HTML whose shell is closed before the
 * bootstrap script.
 */
class ArtifactRendererTest {

    /** Dark palette built by hand -- MermaidPalette is internal data class,
     * constructible here; values only need to be recognizable hex. */
    private val palette = MermaidPalette(
        background = "#1A1B1E",
        text = "#E6E6E6",
        line = "#4A4B50",
        nodeFill = "#26272B",
        nodeBorder = "#3A3B40",
        clusterFill = "#202124",
        clusterBorder = "#33343A",
        dark = true
    )

    // ---- full-document branch ----

    @Test
    fun `complete document gets the bootstrap before its last body close`() {
        val payload = "<!DOCTYPE html><html><head><title>T</title></head>" +
            "<body><p>one</p><div>two</div></body></html>"
        val result = buildArtifactHtml(payload, palette)
        // Exactly the payload with the bootstrap spliced before the LAST
        // </body> -- nothing else added, nothing removed.
        val expected = payload.replaceRange(
            payload.lastIndexOf("</body>"),
            payload.lastIndexOf("</body>"),
            buildArtifactBootstrapScript()
        )
        assertEquals(expected, result)
        assertEquals(1, countOccurrences(result, ARTIFACT_BOOTSTRAP_SCRIPT_ID))
    }

    @Test
    fun `complete document without a body close falls back to the html close`() {
        val payload = "<html><head><title>T</title></head><p>body-less</p></html>"
        val result = buildArtifactHtml(payload, palette)
        val expected = payload.replaceRange(
            payload.lastIndexOf("</html>"),
            payload.lastIndexOf("</html>"),
            buildArtifactBootstrapScript()
        )
        assertEquals(expected, result)
    }

    @Test
    fun `complete document with neither close gets the bootstrap appended`() {
        // Last resort: append at the very end -- the bootstrap is never
        // silently dropped.
        val payload = "<html><body><p>unclosed"
        val result = buildArtifactHtml(payload, palette)
        assertTrue(result.startsWith(payload))
        assertTrue(result.endsWith(buildArtifactBootstrapScript()))
        assertEquals(1, countOccurrences(result, ARTIFACT_BOOTSTRAP_SCRIPT_ID))
    }

    @Test
    fun `html tag detection is case-insensitive`() {
        val payload = "<!DOCTYPE HTML><HTML><BODY><p>x</p></BODY></HTML>"
        val result = buildArtifactHtml(payload, palette)
        // Routed as a complete document (spliced before </BODY>), NOT
        // wrapped in the fragment shell.
        assertFalse(result.contains("<!DOCTYPE html>\n<html>"))
        val expected = payload.replaceRange(
            payload.lastIndexOf("</BODY>"),
            payload.lastIndexOf("</BODY>"),
            buildArtifactBootstrapScript()
        )
        assertEquals(expected, result)
    }

    // ---- fragment branch ----

    @Test
    fun `plain fragment is wrapped in the themed shell with the bootstrap`() {
        val fragment = "<div class=\"widget\">\n  <input type=\"range\" min=\"0\" max=\"100\">\n</div>"
        val result = buildArtifactHtml(fragment, palette)

        // The shell: doctype, charset, viewport -- in that document shape.
        // (trimIndent strips the shell literal's common indent but keeps
        // its leading newline, exactly like MermaidRenderer's shell -- so
        // the doctype is the first non-blank content, not byte 0.)
        assertTrue(result.trimStart().startsWith("<!DOCTYPE html>"))
        assertTrue(result.contains("<meta charset=\"utf-8\">"))
        assertTrue(result.contains("name=\"viewport\""))

        // The theme palette arrives as CSS custom properties -- the SAME
        // values MermaidPalette carries (background == surfaceDialog etc.).
        assertTrue(result.contains("--festo-bg: #1A1B1E;"))
        assertTrue(result.contains("--festo-text: #E6E6E6;"))
        assertTrue(result.contains("--festo-line: #4A4B50;"))
        assertTrue(result.contains("--festo-node-fill: #26272B;"))
        assertTrue(result.contains("--festo-node-border: #3A3B40;"))
        assertTrue(result.contains("--festo-cluster-fill: #202124;"))
        assertTrue(result.contains("--festo-cluster-border: #33343A;"))

        // The fragment is inserted verbatim, then the bootstrap, inside a
        // well-formed body.
        assertTrue(result.contains(fragment))
        val fragmentIdx = result.indexOf(fragment)
        val bootstrapIdx = result.indexOf(ARTIFACT_BOOTSTRAP_SCRIPT_ID)
        val bodyCloseIdx = result.lastIndexOf("</body>")
        assertTrue(fragmentIdx >= 0)
        assertTrue(bootstrapIdx > fragmentIdx)
        assertTrue(bodyCloseIdx > bootstrapIdx)
        assertTrue(result.trimEnd().endsWith("</html>"))
        assertEquals(1, countOccurrences(result, ARTIFACT_BOOTSTRAP_SCRIPT_ID))
    }

    @Test
    fun `fragment shell exposes color-scheme matching the palette darkness`() {
        val dark = buildArtifactHtml("<p>x</p>", palette)
        assertTrue(dark.contains("color-scheme: dark;"))
        val light = buildArtifactHtml("<p>x</p>", palette.copy(dark = false))
        assertTrue(light.contains("color-scheme: light;"))
    }

    // ---- the premature-close escape hatch ----

    @Test
    fun `fragment smuggling a body close routes to document handling`() {
        // A fragment containing a literal "</body>" would end the shell's
        // body BEFORE the bootstrap if it were naively wrapped. It must be
        // handled as a complete document instead: no shell (no doctype, no
        // viewport meta), bootstrap spliced before the premature close.
        val payload = "<p>a</p></body><p>b</p>"
        val result = buildArtifactHtml(payload, palette)

        assertFalse(result.contains("<!DOCTYPE html>"))
        assertFalse(result.contains("name=\"viewport\""))
        assertEquals(1, countOccurrences(result, ARTIFACT_BOOTSTRAP_SCRIPT_ID))
        val bootstrapIdx = result.indexOf(ARTIFACT_BOOTSTRAP_SCRIPT_ID)
        val bodyCloseIdx = result.lastIndexOf("</body>")
        assertTrue(bootstrapIdx >= 0)
        assertTrue(bootstrapIdx < bodyCloseIdx)
    }

    @Test
    fun `fragment smuggling an html close routes to document handling`() {
        val payload = "<p>tail</p></html><p>after</p>"
        val result = buildArtifactHtml(payload, palette)

        assertFalse(result.contains("<!DOCTYPE html>"))
        assertEquals(1, countOccurrences(result, ARTIFACT_BOOTSTRAP_SCRIPT_ID))
        val bootstrapIdx = result.indexOf(ARTIFACT_BOOTSTRAP_SCRIPT_ID)
        val htmlCloseIdx = result.lastIndexOf("</html>")
        assertTrue(bootstrapIdx >= 0)
        assertTrue(bootstrapIdx < htmlCloseIdx)
    }

    // ---- the bootstrap script itself ----

    @Test
    fun `bootstrap wires window onerror to the Android bridge`() {
        val bootstrap = buildArtifactBootstrapScript()
        assertTrue(bootstrap.contains("window.onerror"))
        assertTrue(bootstrap.contains("AndroidBridge"))
        assertTrue(bootstrap.contains("onError"))
        assertTrue(bootstrap.contains("onRendered"))
        assertTrue(bootstrap.contains("return true;"))
        assertTrue(bootstrap.startsWith("<script"))
        assertTrue(bootstrap.trimEnd().endsWith("</script>"))
    }

    @Test
    fun `bootstrap carries no secrets or tokens`() {
        val bootstrap = buildArtifactBootstrapScript()
        val forbidden = listOf(
            "api_key", "apikey", "api-key", "bearer ", "secret", "password",
            "passwd", "sk-", "akia", "authorization:", "token", "credential"
        )
        for (needle in forbidden) {
            assertFalse(
                "bootstrap must not contain '$needle'",
                bootstrap.contains(needle, ignoreCase = true)
            )
        }
    }

    @Test
    fun `bootstrap is the same fixed script for every payload`() {
        // The payload is markup inserted as markup -- no payload content is
        // ever interpolated into the bootstrap, so the exact same script
        // must appear verbatim inside any two assembled documents.
        val bootstrap = buildArtifactBootstrapScript()
        val doc1 = buildArtifactHtml("<p>one</p>", palette)
        val doc2 = buildArtifactHtml("<div>two <b>bold</b></div>", palette)
        assertTrue(doc1.contains(bootstrap))
        assertTrue(doc2.contains(bootstrap))
        // The measurement reads the body exactly once (the
        // body ?: documentElement ternary references document.body twice,
        // so anchor on the actual measurement call).
        assertEquals(1, countOccurrences(doc1, "document.body.scrollHeight"))
    }

    // ---- helpers ----

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            count++
            idx = haystack.indexOf(needle, idx + needle.length)
        }
        return count
    }
}
