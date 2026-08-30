package com.example.ui.components.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the chart/LaTeX paths of [parseMessageBlocks]:
 * `parseChartSpec` reads org.json, which is stubbed out in plain local
 * unit tests, so these run under Robolectric (same setup as
 * ExampleRobolectricTest -- note it needs a JDK 21 toolchain, see
 * build.gradle.kts / the audit report).
 *
 * Purpose: the Hermes media-rendering work inserted splitMediaBlocks()
 * into parseMessageBlocks' non-fence segments. These tests pin that a
 * ```chart fence still becomes a rendered Chart block (not code, not
 * markdown), that invalid chart JSON falls back to a Code block, and
 * that LaTeX-heavy markdown passes through untouched -- i.e. the media
 * extraction introduced no regression for Wendy's charts and math.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RichMessageChartTest {

    @Test
    fun `chart fence still becomes a Chart block`() {
        val json = """
            {"type":"bar","title":"Tokens per model",
             "series":[{"label":"haiku","data":[120.0,340.0]}],
             "x_labels":["Mon","Tue"]}
        """.trimIndent()
        val blocks = parseMessageBlocks("Here is your chart:\n```chart\n$json\n```\n")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Markdown)
        val chart = blocks[1] as MessageBlock.Chart
        assertEquals("bar", chart.spec.type)
        assertEquals("Tokens per model", chart.spec.title)
        assertEquals(1, chart.spec.series.size)
        assertEquals("haiku", chart.spec.series[0].label)
        assertEquals(listOf(120.0, 340.0), chart.spec.series[0].data)
        assertEquals(listOf("Mon", "Tue"), chart.spec.xLabels)
        assertEquals(json, chart.fallbackRaw)
    }

    @Test
    fun `invalid chart json falls back to a Code block`() {
        val blocks = parseMessageBlocks("```chart\nnot json at all\n```")
        assertEquals(1, blocks.size)
        val code = blocks[0] as MessageBlock.Code
        assertEquals("chart", code.language)
        assertEquals("not json at all", code.code)
    }

    @Test
    fun `latex math in markdown passes through untouched`() {
        val latex = "When \\(x=3\\), then \$\$y = x^2 + \\frac{a}{b}\$\$ holds."
        val blocks = parseMessageBlocks(latex)
        assertEquals(1, blocks.size)
        assertEquals(latex, (blocks[0] as MessageBlock.Markdown).content)
    }
}
