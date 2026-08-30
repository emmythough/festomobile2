package com.example.ui.components.markdown

import com.example.ui.theme.DarkSyntaxComment
import com.example.ui.theme.DarkSyntaxKeyword
import com.example.ui.theme.DarkSyntaxNumber
import com.example.ui.theme.DarkSyntaxString
import com.example.ui.theme.LightSyntaxAnnotation
import com.example.ui.theme.LightSyntaxAttribute
import com.example.ui.theme.LightSyntaxComment
import com.example.ui.theme.LightSyntaxFunction
import com.example.ui.theme.LightSyntaxKeyword
import com.example.ui.theme.LightSyntaxNumber
import com.example.ui.theme.LightSyntaxOperator
import com.example.ui.theme.LightSyntaxProperty
import com.example.ui.theme.LightSyntaxString
import com.example.ui.theme.LightSyntaxTag
import com.example.ui.theme.LightSyntaxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [tokenizeCode] / [highlightCode] -- the tokenizer behind
 * CodeBlockView's syntax highlighting.
 *
 * Contracts pinned here:
 * 1. Every supported language gets real token spans (keywords, strings with
 *    escape handling, comments, numbers, ...) from the shared theme palette.
 * 2. Unknown languages fall back to the exact plain rendering -- no spans,
 *    no crash, no mangled text.
 * 3. Highlighting is lossless: the AnnotatedString text always equals the
 *    original code, byte for byte.
 */
class CodeHighlighterTest {

    private fun tokens(code: String, language: String): List<SyntaxToken> =
        requireNotNull(tokenizeCode(code, language)) { "language $language should be supported" }

    private fun typeOf(code: String, language: String, literal: String): SyntaxTokenType? =
        tokens(code, language)
            .firstOrNull { code.substring(it.start, it.end) == literal }
            ?.type

    private fun textOfFirst(code: String, language: String, type: SyntaxTokenType): String {
        val token = tokens(code, language).first { it.type == type }
        return code.substring(token.start, token.end)
    }

    // ---- Kotlin ----

    @Test
    fun `kotlin gets keywords types comments numbers and function calls`() {
        val code = "fun greet(name: String): Int {\n" +
            "    // build greeting\n" +
            "    val msg = \"hi\"\n" +
            "    return 42\n" +
            "}"
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "kotlin", "fun"))
        assertEquals(SyntaxTokenType.FUNCTION, typeOf(code, "kotlin", "greet"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "kotlin", "String"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "kotlin", "Int"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "kotlin", "val"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "kotlin", "return"))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "kotlin", "42"))
        assertEquals("// build greeting", textOfFirst(code, "kotlin", SyntaxTokenType.COMMENT))
    }

    @Test
    fun `kotlin string with escaped quotes does not terminate early`() {
        val code = "val msg = \"hi \\\"you\\\"\""
        assertEquals("\"hi \\\"you\\\"\"", textOfFirst(code, "kotlin", SyntaxTokenType.STRING))
    }

    // ---- Java ----

    @Test
    fun `java gets keywords block comments primitives and hex numbers`() {
        val code = "public class FestoService {\n" +
            "    /* retry state */\n" +
            "    private int retries = 0xFF;\n" +
            "}"
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "java", "public"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "java", "class"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "java", "FestoService"))
        assertEquals("/* retry state */", textOfFirst(code, "java", SyntaxTokenType.COMMENT))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "java", "private"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "java", "int"))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "java", "0xFF"))
    }

    // ---- Python ----

    @Test
    fun `python gets decorators hash comments and single-quote escapes`() {
        val code = "@app.route(\"/health\")\n" +
            "def run(count):\n" +
            "    # loop forever\n" +
            "    label = 'it\\'s fine'\n" +
            "    return count * 1.5"
        assertEquals(SyntaxTokenType.ANNOTATION, typeOf(code, "python", "@app"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "python", "def"))
        assertEquals(SyntaxTokenType.FUNCTION, typeOf(code, "python", "run"))
        assertEquals("# loop forever", textOfFirst(code, "python", SyntaxTokenType.COMMENT))
        assertEquals("'it\\'s fine'", textOfLast(code, "python", SyntaxTokenType.STRING))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "python", "return"))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "python", "1.5"))
    }

    // ---- JavaScript ----

    @Test
    fun `javascript gets strings template literals comments and numbers`() {
        val code = "const url = \"a \\\"b\\\"\"; // fetch it\n" +
            "const tpl = `count \${1 + 2}`;\n" +
            "let n = 3;"
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "javascript", "const"))
        assertEquals("\"a \\\"b\\\"\"", textOfFirst(code, "javascript", SyntaxTokenType.STRING))
        assertEquals("// fetch it", textOfFirst(code, "javascript", SyntaxTokenType.COMMENT))
        assertEquals("`count \${1 + 2}`", textOfLast(code, "javascript", SyntaxTokenType.STRING))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "javascript", "let"))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "javascript", "3"))
    }

    private fun textOfLast(code: String, language: String, type: SyntaxTokenType): String {
        val token = tokens(code, language).last { it.type == type }
        return code.substring(token.start, token.end)
    }

    // ---- TypeScript ----

    @Test
    fun `typescript gets interface keywords and builtin types`() {
        val code = "interface User {\n" +
            "  id: number;\n" +
            "  name: string;\n" +
            "}"
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "typescript", "interface"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "typescript", "User"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "typescript", "number"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "typescript", "string"))
    }

    // ---- JSON ----

    @Test
    fun `json distinguishes property keys from string values`() {
        val code = "{\"name\": \"Festo\", \"ok\": true, \"count\": -3.5, \"tags\": [\"a\"]}"
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "json", "\"name\""))
        assertEquals(SyntaxTokenType.STRING, typeOf(code, "json", "\"Festo\""))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "json", "\"ok\""))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "json", "true"))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "json", "\"count\""))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "json", "-3.5"))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "json", "\"tags\""))
        assertEquals(SyntaxTokenType.STRING, typeOf(code, "json", "\"a\""))
    }

    // ---- Bash ----

    @Test
    fun `bash gets shebang keywords variables and comments`() {
        val code = "#!/bin/bash\n" +
            "NAME=\"festo\"\n" +
            "echo \"hi \$NAME\"\n" +
            "cd \$HOME\n" +
            "if [ -f \"\$NAME\" ]; then\n" +
            "  # not reached\n" +
            "  exit 1\n" +
            "fi"
        assertEquals("#!/bin/bash", textOfFirst(code, "bash", SyntaxTokenType.COMMENT))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "bash", "echo"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "bash", "if"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "bash", "then"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "bash", "fi"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "bash", "exit"))
        assertEquals("\"festo\"", textOfFirst(code, "bash", SyntaxTokenType.STRING))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "bash", "\$HOME"))
        assertEquals("# not reached", textOfLast(code, "bash", SyntaxTokenType.COMMENT))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "bash", "1"))
    }

    @Test
    fun `sh and shell are aliases of bash`() {
        val code = "for f in *.txt; do echo \"\$f\"; done"
        assertEquals(tokens(code, "bash"), tokens(code, "sh"))
        assertEquals(tokens(code, "bash"), tokens(code, "shell"))
    }

    // ---- SQL ----

    @Test
    fun `sql is case insensitive and handles doubled quote escapes`() {
        val code = "SELECT id, name FROM users\n" +
            "WHERE age > 21 AND name LIKE 'O''Brien'; -- filter\n" +
            "SELECT CAST(age AS VARCHAR) FROM users;"
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "sql", "SELECT"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "sql", "FROM"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "sql", "WHERE"))
        assertEquals(SyntaxTokenType.OPERATOR, typeOf(code, "sql", ">"))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "sql", "21"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "sql", "AND"))
        assertEquals("'O''Brien'", textOfFirst(code, "sql", SyntaxTokenType.STRING))
        assertEquals("-- filter", textOfFirst(code, "sql", SyntaxTokenType.COMMENT))

        val lowercase = "select * from users"
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(lowercase, "sql", "select"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(lowercase, "sql", "from"))
        assertEquals(SyntaxTokenType.TYPE, typeOf(code, "sql", "VARCHAR"))
    }

    // ---- YAML ----

    @Test
    fun `yaml gets keys scalars numbers booleans and comments`() {
        val code = "name: festo        # inline note\n" +
            "port: 8080\n" +
            "debug: true\n" +
            "items:\n" +
            "  - \"a b\"\n" +
            "  - 2"
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "yaml", "name"))
        assertEquals("# inline note", textOfFirst(code, "yaml", SyntaxTokenType.COMMENT))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "yaml", "port"))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "yaml", "8080"))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "yaml", "debug"))
        assertEquals(SyntaxTokenType.KEYWORD, typeOf(code, "yaml", "true"))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "yaml", "items"))
        assertEquals(SyntaxTokenType.OPERATOR, typeOf(code, "yaml", "-"))
        assertEquals("\"a b\"", textOfFirst(code, "yaml", SyntaxTokenType.STRING))
        assertEquals(SyntaxTokenType.NUMBER, typeOf(code, "yaml", "2"))
    }

    @Test
    fun `yml is an alias of yaml`() {
        val code = "key: value"
        assertEquals(tokens(code, "yaml"), tokens(code, "yml"))
    }

    // ---- XML / HTML ----

    @Test
    fun `xml gets tags attributes comments and declarations`() {
        val code = "<?xml version=\"1.0\"?>\n" +
            "<!-- config -->\n" +
            "<Button android:id=\"@+id/ok\">\n" +
            "  <TextView android:text=\"Hi\" />\n" +
            "</Button>"
        assertEquals(SyntaxTokenType.TAG, typeOf(code, "xml", "<?xml version=\"1.0\"?>"))
        assertEquals("<!-- config -->", textOfFirst(code, "xml", SyntaxTokenType.COMMENT))
        assertEquals(SyntaxTokenType.TAG, typeOf(code, "xml", "<Button"))
        assertEquals(SyntaxTokenType.ATTRIBUTE, typeOf(code, "xml", "android:id"))
        assertEquals("\"@+id/ok\"", textOfFirst(code, "xml", SyntaxTokenType.STRING))
        assertEquals(SyntaxTokenType.TAG, typeOf(code, "xml", "<TextView"))
        assertEquals(SyntaxTokenType.TAG, typeOf(code, "xml", "</Button"))
    }

    @Test
    fun `html is an alias of xml`() {
        val code = "<div class=\"a\">text</div>"
        assertEquals(tokens(code, "xml"), tokens(code, "html"))
    }

    // ---- Markdown ----

    @Test
    fun `markdown gets headings bullets bold code spans and links`() {
        val code = "# Release notes\n" +
            "- **festo** with `code` and https://example.com/x\n" +
            "> quoted line"
        assertEquals("# Release notes", textOfFirst(code, "markdown", SyntaxTokenType.KEYWORD))
        assertEquals(SyntaxTokenType.OPERATOR, typeOf(code, "markdown", "-"))
        assertEquals(SyntaxTokenType.PROPERTY, typeOf(code, "markdown", "**festo**"))
        assertEquals(SyntaxTokenType.STRING, typeOf(code, "markdown", "`code`"))
        assertEquals(SyntaxTokenType.ANNOTATION, typeOf(code, "markdown", "https://example.com/x"))
        assertEquals("> quoted line", textOfFirst(code, "markdown", SyntaxTokenType.COMMENT))
    }

    // ---- Fallback ----

    @Test
    fun `unknown language falls back cleanly to plain rendering`() {
        val code = "val x = ??? weird <> stuff"
        assertNull(tokenizeCode(code, "fortran"))
        assertNull(tokenizeCode(code, "text"))
        assertNull(tokenizeCode(code, ""))
        assertNull(tokenizeCode(code, "brainfuck"))

        for (lang in listOf("fortran", "text", "", "brainfuck")) {
            val plain = highlightCode(code, lang, isDark = true)
            assertEquals(code, plain.text)
            assertTrue("no spans expected for '$lang'", plain.spanStyles.isEmpty())
        }
    }

    // ---- Theme colors ----

    @Test
    fun `token colors come from the shared light and dark syntax palette`() {
        assertEquals(LightSyntaxKeyword, syntaxTokenColor(SyntaxTokenType.KEYWORD, isDark = false))
        assertEquals(DarkSyntaxKeyword, syntaxTokenColor(SyntaxTokenType.KEYWORD, isDark = true))
        assertEquals(LightSyntaxString, syntaxTokenColor(SyntaxTokenType.STRING, isDark = false))
        assertEquals(DarkSyntaxString, syntaxTokenColor(SyntaxTokenType.STRING, isDark = true))
        assertEquals(LightSyntaxComment, syntaxTokenColor(SyntaxTokenType.COMMENT, isDark = false))
        assertEquals(DarkSyntaxComment, syntaxTokenColor(SyntaxTokenType.COMMENT, isDark = true))
        assertEquals(LightSyntaxNumber, syntaxTokenColor(SyntaxTokenType.NUMBER, isDark = false))
        assertEquals(DarkSyntaxNumber, syntaxTokenColor(SyntaxTokenType.NUMBER, isDark = true))
        assertEquals(LightSyntaxFunction, syntaxTokenColor(SyntaxTokenType.FUNCTION, isDark = false))
        assertEquals(LightSyntaxType, syntaxTokenColor(SyntaxTokenType.TYPE, isDark = false))
        assertEquals(LightSyntaxOperator, syntaxTokenColor(SyntaxTokenType.OPERATOR, isDark = false))
        assertEquals(LightSyntaxAnnotation, syntaxTokenColor(SyntaxTokenType.ANNOTATION, isDark = false))
        assertEquals(LightSyntaxTag, syntaxTokenColor(SyntaxTokenType.TAG, isDark = false))
        assertEquals(LightSyntaxAttribute, syntaxTokenColor(SyntaxTokenType.ATTRIBUTE, isDark = false))
        assertEquals(LightSyntaxProperty, syntaxTokenColor(SyntaxTokenType.PROPERTY, isDark = false))
    }

    @Test
    fun `highlightCode produces one styled span per token with theme colors`() {
        val code = "val x = 1"
        val highlighted = highlightCode(code, "kotlin", isDark = false)
        assertEquals(code, highlighted.text)
        val spans = highlighted.spanStyles
        assertEquals(3, spans.size)

        assertEquals(0, spans[0].start)
        assertEquals(3, spans[0].end)
        assertEquals(LightSyntaxKeyword, spans[0].item.color)

        assertEquals(6, spans[1].start)
        assertEquals(7, spans[1].end)
        assertEquals(LightSyntaxOperator, spans[1].item.color)

        assertEquals(8, spans[2].start)
        assertEquals(9, spans[2].end)
        assertEquals(LightSyntaxNumber, spans[2].item.color)
    }

    // ---- Robustness / losslessness ----

    @Test
    fun `unterminated literals and comments degrade to end of input without crashing`() {
        val unterminatedString = "val s = \"streaming partial"
        assertEquals("\"streaming partial", textOfFirst(unterminatedString, "kotlin", SyntaxTokenType.STRING))

        val unterminatedComment = "/* never closed"
        assertEquals("/* never closed", textOfFirst(unterminatedComment, "java", SyntaxTokenType.COMMENT))
    }

    @Test
    fun `every supported language round-trips the original code exactly`() {
        val samples = mapOf(
            "kotlin" to "fun main() { val s = \"a\\\"b\" /* c */ }",
            "kt" to "val x = 0x1F",
            "java" to "class A { int x = 1; // done\n}",
            "python" to "def f(x):\n    return f'{x!r}'  # tail",
            "py" to "x = 1_000",
            "javascript" to "const f = (x) => `\${x}`; // end",
            "js" to "let re = 0b101;",
            "typescript" to "const a: Array<string> = [];",
            "ts" to "type A = Record<string, number>;",
            "json" to "{\"k\": [1, 2.5, true, null]}",
            "bash" to "for i in 1 2; do echo \"\$i\"; done",
            "sh" to "#!/bin/sh\nexport PATH=\$PATH:/bin",
            "shell" to "x=\$(date) # now",
            "sql" to "SELECT a FROM t WHERE b = 'it''s';",
            "yaml" to "root:\n  child: \"v\"\n  n: -1.5",
            "yml" to "a: true",
            "xml" to "<a b=\"c\"><!-- d --></a>",
            "html" to "<p class=\"x\">Hi &amp; bye</p>",
            "markdown" to "# T\n- a **b** `c`\n> q",
            "md" to "see https://example.com"
        )
        for ((language, sample) in samples) {
            for (isDark in listOf(false, true)) {
                assertEquals(
                    "text must survive highlight for language '$language'",
                    sample,
                    highlightCode(sample, language, isDark).text
                )
            }
        }
    }
}
