package com.example.ui.components.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.example.ui.theme.DarkSyntaxAnnotation
import com.example.ui.theme.DarkSyntaxAttribute
import com.example.ui.theme.DarkSyntaxComment
import com.example.ui.theme.DarkSyntaxFunction
import com.example.ui.theme.DarkSyntaxKeyword
import com.example.ui.theme.DarkSyntaxNumber
import com.example.ui.theme.DarkSyntaxOperator
import com.example.ui.theme.DarkSyntaxProperty
import com.example.ui.theme.DarkSyntaxString
import com.example.ui.theme.DarkSyntaxTag
import com.example.ui.theme.DarkSyntaxType
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

/**
 * Regex/rule-based syntax highlighting for chat code blocks.
 *
 * [tokenizeCode] scans source into non-overlapping [SyntaxToken]s (plain gaps
 * stay untokenized), and [highlightCode] turns those tokens into an
 * [AnnotatedString] with one [SpanStyle] color per token. Colors come from the
 * shared "Syntax Highlighting Tokens" in
 * `com.example.ui.theme.Color` -- light vs dark is chosen by the caller via
 * [FestoTheme.colors.isDark][com.example.ui.theme.FestoExtendedColors.isDark],
 * exactly like `voiceOrbBrush()` in Theme.kt. No third-party library.
 *
 * Unknown/unsupported language names return `null` from [tokenizeCode] and an
 * untouched `AnnotatedString(code)` from [highlightCode], so rendering falls
 * back to the previous plain monospace text and never crashes or mangles
 * output.
 */
enum class SyntaxTokenType {
    KEYWORD, STRING, COMMENT, NUMBER, FUNCTION, TYPE, OPERATOR, ANNOTATION, TAG, ATTRIBUTE, PROPERTY, PLAIN
}

/** Half-open range [start, end) into the highlighted source. */
data class SyntaxToken(val start: Int, val end: Int, val type: SyntaxTokenType)

/**
 * Maps a token type to its theme color. [PLAIN] is never styled (inherits the
 * surrounding Text color) and maps to [Color.Unspecified].
 */
fun syntaxTokenColor(type: SyntaxTokenType, isDark: Boolean): Color = when (type) {
    SyntaxTokenType.KEYWORD -> if (isDark) DarkSyntaxKeyword else LightSyntaxKeyword
    SyntaxTokenType.STRING -> if (isDark) DarkSyntaxString else LightSyntaxString
    SyntaxTokenType.COMMENT -> if (isDark) DarkSyntaxComment else LightSyntaxComment
    SyntaxTokenType.NUMBER -> if (isDark) DarkSyntaxNumber else LightSyntaxNumber
    SyntaxTokenType.FUNCTION -> if (isDark) DarkSyntaxFunction else LightSyntaxFunction
    SyntaxTokenType.TYPE -> if (isDark) DarkSyntaxType else LightSyntaxType
    SyntaxTokenType.OPERATOR -> if (isDark) DarkSyntaxOperator else LightSyntaxOperator
    SyntaxTokenType.ANNOTATION -> if (isDark) DarkSyntaxAnnotation else LightSyntaxAnnotation
    SyntaxTokenType.TAG -> if (isDark) DarkSyntaxTag else LightSyntaxTag
    SyntaxTokenType.ATTRIBUTE -> if (isDark) DarkSyntaxAttribute else LightSyntaxAttribute
    SyntaxTokenType.PROPERTY -> if (isDark) DarkSyntaxProperty else LightSyntaxProperty
    SyntaxTokenType.PLAIN -> Color.Unspecified
}

/**
 * Tokenizes [code] as [language], or returns null when the language is not
 * supported (unknown names, "text", blank...). Tokens are sorted and
 * non-overlapping; unstyled regions are simply absent.
 */
fun tokenizeCode(code: String, language: String): List<SyntaxToken>? = when (
    normalizeLanguage(language) ?: return null
) {
    "kotlin" -> scanCLike(code, kotlinSpec)
    "java" -> scanCLike(code, javaSpec)
    "python" -> scanCLike(code, pythonSpec)
    "javascript" -> scanCLike(code, javascriptSpec)
    "typescript" -> scanCLike(code, typescriptSpec)
    "bash" -> scanCLike(code, bashSpec)
    "sql" -> scanCLike(code, sqlSpec)
    "json" -> scanJson(code)
    "yaml" -> scanYaml(code)
    "xml" -> scanXml(code)
    "markdown" -> scanMarkdown(code)
    else -> null
}

/**
 * Builds the highlighted [AnnotatedString] for a code block. Unsupported
 * languages fall back to `AnnotatedString(code)` with zero spans -- the exact
 * pre-highlighting rendering.
 */
fun highlightCode(code: String, language: String, isDark: Boolean): AnnotatedString {
    val tokens = tokenizeCode(code, language) ?: return AnnotatedString(code)
    val builder = AnnotatedString.Builder()
    var cursor = 0
    for (token in tokens) {
        if (token.type == SyntaxTokenType.PLAIN) continue
        if (token.start > cursor) builder.append(code.substring(cursor, token.start))
        val spanStart = builder.length
        builder.append(code.substring(token.start, token.end))
        builder.addStyle(SpanStyle(color = syntaxTokenColor(token.type, isDark)), spanStart, builder.length)
        cursor = token.end
    }
    if (cursor < code.length) builder.append(code.substring(cursor))
    return builder.toAnnotatedString()
}

// ---------------------------------------------------------------------------
// Language registry
// ---------------------------------------------------------------------------

private fun normalizeLanguage(language: String): String? {
    val name = language.trim().lowercase()
    return languageAliases[name]
}

private val languageAliases: Map<String, String> = mapOf(
    "kotlin" to "kotlin", "kt" to "kotlin", "kts" to "kotlin",
    "java" to "java",
    "python" to "python", "py" to "python", "python3" to "python",
    "javascript" to "javascript", "js" to "javascript", "jsx" to "javascript",
    "node" to "javascript", "nodejs" to "javascript", "mjs" to "javascript",
    "typescript" to "typescript", "ts" to "typescript", "tsx" to "typescript",
    "json" to "json", "jsonc" to "json", "json5" to "json",
    "bash" to "bash", "sh" to "bash", "shell" to "bash", "zsh" to "bash",
    "sql" to "sql", "mysql" to "sql", "postgresql" to "sql", "postgres" to "sql",
    "sqlite" to "sql", "plsql" to "sql", "tsql" to "sql",
    "yaml" to "yaml", "yml" to "yaml",
    "xml" to "xml", "html" to "xml", "xhtml" to "xml", "svg" to "xml",
    "markdown" to "markdown", "md" to "markdown", "mdx" to "markdown"
)

// ---------------------------------------------------------------------------
// C-family scanner (kotlin, java, python, js, ts, bash, sql)
// ---------------------------------------------------------------------------

private class CLikeSpec(
    val keywords: Set<String>,
    val typeWords: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockComment: Pair<String, String>? = null,
    /** '#' starts a line comment. */
    val hashComment: Boolean = false,
    /** '#' only opens a comment at a word boundary (bash/yaml style). */
    val hashNeedsBoundary: Boolean = false,
    val stringDelims: Set<Char> = setOf('"'),
    /** Delimiters where a raw newline may appear inside the literal. */
    val multilineDelims: Set<Char> = emptySet(),
    /** Delimiters that never process backslash escapes (bash single quotes). */
    val noEscapeDelims: Set<Char> = emptySet(),
    /** Triple-quoted strings exist (kotlin, python). */
    val tripleQuotes: Boolean = false,
    /** Triple-quoted strings are raw (kotlin """): no escape processing. */
    val rawTriple: Boolean = false,
    /** "O''Brien" style doubled-quote escape (sql). */
    val doubledQuoteEscape: Boolean = false,
    /** Identifier prefixes that glue onto the following string (python r""). */
    val rawPrefixes: Set<String> = emptySet(),
    /** Match keywords/types case-insensitively (sql). */
    val caseInsensitive: Boolean = false,
    /** '@Foo' annotations / decorators. */
    val annotations: Boolean = false,
    /** Capitalized identifiers are types (C-family/Python heuristic). */
    val uppercaseType: Boolean = true,
    /** 'ident(' colors the identifier as a function. */
    val functionCall: Boolean = true,
    /** '$VAR' / '${VAR}' shell variables. */
    val dollarVars: Boolean = false
)

private val numberRegex = Regex(
    "0[xX][0-9a-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+|" +
        "[0-9][0-9_]*(?:\\.[0-9_]*)?(?:[eE][+-]?[0-9]+)?[uUlLfFdDnN]{0,2}|" +
        "\\.[0-9][0-9_]*(?:[eE][+-]?[0-9]+)?[fFdD]{0,2}"
)

private val operatorChars = "+-*/%=<>!&|^~?:"

private fun isIdentChar(c: Char): Boolean =
    c.isLetterOrDigit() || c == '_' || c == '$'

private fun scanCLike(code: String, spec: CLikeSpec): List<SyntaxToken> {
    val tokens = mutableListOf<SyntaxToken>()
    val n = code.length
    var i = 0
    while (i < n) {
        val c = code[i]

        // Block comments.
        val block = spec.blockComment
        if (block != null && code.startsWith(block.first, i)) {
            val close = code.indexOf(block.second, i + block.first.length)
            val end = if (close == -1) n else close + block.second.length
            tokens += SyntaxToken(i, end, SyntaxTokenType.COMMENT)
            i = end
            continue
        }

        // Line comments.
        val line = spec.lineComments.firstOrNull { code.startsWith(it, i) }
        if (line != null || (spec.hashComment && c == '#' && hashAtBoundary(code, i, spec))) {
            var end = code.indexOf('\n', i)
            if (end == -1) end = n
            tokens += SyntaxToken(i, end, SyntaxTokenType.COMMENT)
            i = end
            continue
        }

        // Triple-quoted strings (must be tested before single-quote handling).
        if (spec.tripleQuotes && (c == '"' || c == '\'') &&
            i + 2 < n && code[i + 1] == c && code[i + 2] == c
        ) {
            val delim = "$c$c$c"
            val end = scanStringEnd(
                code, i + 3, c,
                allowEscape = !spec.rawTriple,
                multiline = true,
                doubled = false,
                tripleDelim = delim
            )
            tokens += SyntaxToken(i, end, SyntaxTokenType.STRING)
            i = end
            continue
        }

        // Regular strings / char literals / template literals.
        if (c in spec.stringDelims) {
            val end = scanStringEnd(
                code, i + 1, c,
                allowEscape = c !in spec.noEscapeDelims,
                multiline = c in spec.multilineDelims,
                doubled = spec.doubledQuoteEscape,
                tripleDelim = null
            )
            tokens += SyntaxToken(i, end, SyntaxTokenType.STRING)
            i = end
            continue
        }

        // Shell variables: $VAR, ${VAR}, $?, $1 ...
        if (spec.dollarVars && c == '$') {
            val end = when {
                i + 1 < n && code[i + 1] == '{' ->
                    (code.indexOf('}', i + 2) + 1).takeIf { it > 0 } ?: n
                i + 1 < n && (code[i + 1].isLetterOrDigit() || code[i + 1] == '_') -> {
                    var j = i + 1
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    j
                }
                i + 1 < n && code[i + 1] in "?@#*-$!" -> i + 2
                else -> -1
            }
            if (end != -1) {
                tokens += SyntaxToken(i, end, SyntaxTokenType.PROPERTY)
                i = end
                continue
            }
        }

        // Numbers (also ".5" float style).
        if (c.isDigit() || (c == '.' && i + 1 < n && code[i + 1].isDigit())) {
            val match = numberRegex.find(code, i)
            if (match != null && match.range.first == i) {
                tokens += SyntaxToken(i, match.range.last + 1, SyntaxTokenType.NUMBER)
                i = match.range.last + 1
                continue
            }
        }

        // Annotations / decorators: @Foo
        if (spec.annotations && c == '@') {
            var j = i + 1
            while (j < n && isIdentChar(code[j])) j++
            if (j > i + 1) {
                tokens += SyntaxToken(i, j, SyntaxTokenType.ANNOTATION)
                i = j
                continue
            }
        }

        // Identifiers and keywords.
        if (c.isLetter() || c == '_') {
            val wordStart = i
            while (i < n && isIdentChar(code[i])) i++
            val word = code.substring(wordStart, i)
            val lookup = if (spec.caseInsensitive) word.lowercase() else word
            when {
                lookup in spec.keywords ->
                    tokens += SyntaxToken(wordStart, i, SyntaxTokenType.KEYWORD)
                lookup in spec.typeWords ->
                    tokens += SyntaxToken(wordStart, i, SyntaxTokenType.TYPE)
                spec.rawPrefixes.isNotEmpty() && lookup in spec.rawPrefixes &&
                    i < n && (code[i] == '"' || code[i] == '\'') -> {
                    // Python string prefixes (r"", f'', rb""...) color with the string.
                    val delim = code[i]
                    val triple = spec.tripleQuotes && i + 2 < n &&
                        code[i + 1] == delim && code[i + 2] == delim
                    val end = scanStringEnd(
                        code,
                        if (triple) i + 3 else i + 1, delim,
                        allowEscape = true, // raw strings still can't close early on \"
                        multiline = triple,
                        doubled = false,
                        tripleDelim = if (triple) "$delim$delim$delim" else null
                    )
                    tokens += SyntaxToken(wordStart, end, SyntaxTokenType.STRING)
                    i = end
                }
                spec.functionCall && nextNonSpace(code, i) == '(' ->
                    tokens += SyntaxToken(wordStart, i, SyntaxTokenType.FUNCTION)
                spec.uppercaseType && word.first().isUpperCase() ->
                    tokens += SyntaxToken(wordStart, i, SyntaxTokenType.TYPE)
            }
            continue
        }

        // Operators (punctuation like (){},; stays neutral).
        if (c in operatorChars) {
            var j = i
            while (j < n && code[j] in operatorChars) j++
            tokens += SyntaxToken(i, j, SyntaxTokenType.OPERATOR)
            i = j
            continue
        }

        i++
    }
    return tokens
}

private fun hashAtBoundary(code: String, i: Int, spec: CLikeSpec): Boolean =
    !spec.hashNeedsBoundary || i == 0 || code[i - 1].isWhitespace()

private fun nextNonSpace(code: String, from: Int): Char? {
    var i = from
    while (i < code.length && (code[i] == ' ' || code[i] == '\t')) i++
    return code.getOrNull(i)
}

/**
 * Walks a string literal starting just after its opening quote(s) and returns
 * the exclusive end index. Handles backslash escapes (so `\"` never
 * terminates the string early), doubled-quote escapes, and unterminated
 * literals (stop at end of line, or end of input).
 */
private fun scanStringEnd(
    code: String,
    openIndex: Int,
    delim: Char,
    allowEscape: Boolean,
    multiline: Boolean,
    doubled: Boolean,
    tripleDelim: String?
): Int {
    var i = openIndex
    val n = code.length
    while (i < n) {
        val ch = code[i]
        if (ch == '\\' && allowEscape && i + 1 < n) {
            i += 2
            continue
        }
        if (tripleDelim != null) {
            if (code.startsWith(tripleDelim, i)) return i + tripleDelim.length
            i++
            continue
        }
        if (ch == delim) {
            if (doubled && i + 1 < n && code[i + 1] == delim) {
                i += 2
                continue
            }
            return i + 1
        }
        if (ch == '\n' && !multiline) return i
        i++
    }
    return n
}

// ---------------------------------------------------------------------------
// JSON
// ---------------------------------------------------------------------------

private fun scanJson(code: String): List<SyntaxToken> {
    val tokens = mutableListOf<SyntaxToken>()
    val n = code.length
    var i = 0
    while (i < n) {
        val c = code[i]
        when {
            c == '"' -> {
                val end = scanStringEnd(code, i + 1, '"', allowEscape = true, multiline = false, doubled = false, tripleDelim = null)
                // A string followed by ':' is an object key.
                var k = end
                while (k < n && (code[k] == ' ' || code[k] == '\t' || code[k] == '\n' || code[k] == '\r')) k++
                val type = if (k < n && code[k] == ':') SyntaxTokenType.PROPERTY else SyntaxTokenType.STRING
                tokens += SyntaxToken(i, end, type)
                i = end
            }
            c.isDigit() || (c == '-' && i + 1 < n && code[i + 1].isDigit()) -> {
                val match = numberRegex.find(code, if (c == '-') i + 1 else i)
                val end = match?.range?.last?.plus(1) ?: (i + 1)
                tokens += SyntaxToken(i, end, SyntaxTokenType.NUMBER)
                i = end
            }
            c.isLetter() -> {
                val start = i
                while (i < n && code[i].isLetter()) i++
                val word = code.substring(start, i)
                if (word in setOf("true", "false", "null")) {
                    tokens += SyntaxToken(start, i, SyntaxTokenType.KEYWORD)
                }
            }
            else -> i++
        }
    }
    return tokens
}

// ---------------------------------------------------------------------------
// YAML
// ---------------------------------------------------------------------------

private val yamlScalars = setOf("true", "false", "null", "yes", "no", "on", "off", "nan", ".inf", "-.inf")

private fun scanYaml(code: String): List<SyntaxToken> {
    val tokens = mutableListOf<SyntaxToken>()
    val n = code.length
    var lineStart = 0
    while (lineStart < n) {
        val lineEnd = code.indexOf('\n', lineStart).let { if (it == -1) n else it }
        var i = lineStart
        while (i < lineEnd && (code[i] == ' ' || code[i] == '\t')) i++
        if (i >= lineEnd) {
            lineStart = lineEnd + 1
            continue
        }
        // Full-line comment.
        if (code[i] == '#') {
            tokens += SyntaxToken(i, lineEnd, SyntaxTokenType.COMMENT)
            lineStart = lineEnd + 1
            continue
        }
        // Sequence dash.
        if (code[i] == '-' && (i + 1 >= lineEnd || code[i + 1] == ' ' || code[i + 1] == '\t')) {
            tokens += SyntaxToken(i, i + 1, SyntaxTokenType.OPERATOR)
            i++
            while (i < lineEnd && (code[i] == ' ' || code[i] == '\t')) i++
        }
        // Key: scalar/quoted followed by ':' + space/EOL.
        val keyColon = yamlKeyColon(code, i, lineEnd)
        if (keyColon != -1) {
            tokens += SyntaxToken(i, keyColon, SyntaxTokenType.PROPERTY)
            i = keyColon + 1
        }
        // Value region.
        while (i < lineEnd) {
            val c = code[i]
            when {
                c == '#' && (i == lineStart || code[i - 1] == ' ' || code[i - 1] == '\t') -> {
                    tokens += SyntaxToken(i, lineEnd, SyntaxTokenType.COMMENT)
                    i = lineEnd
                }
                c == '"' || c == '\'' -> {
                    val end = scanStringEnd(
                        code, i + 1, c,
                        allowEscape = c == '"',
                        multiline = false,
                        doubled = c == '\'',
                        tripleDelim = null
                    )
                    tokens += SyntaxToken(i, end, SyntaxTokenType.STRING)
                    i = end
                }
                c == '&' || c == '*' || c == '!' -> {
                    var j = i + 1
                    while (j < lineEnd && !code[j].isWhitespace() && code[j] != ',') j++
                    tokens += SyntaxToken(i, j, SyntaxTokenType.ANNOTATION)
                    i = j
                }
                c.isDigit() || (c == '-' && i + 1 < lineEnd && code[i + 1].isDigit()) ||
                    (c == '.' && i + 1 < lineEnd && code[i + 1].isDigit()) -> {
                    val match = numberRegex.find(code, i)
                    if (match != null && match.range.first == i) {
                        tokens += SyntaxToken(i, match.range.last + 1, SyntaxTokenType.NUMBER)
                        i = match.range.last + 1
                    } else {
                        i++
                    }
                }
                c.isLetter() -> {
                    val start = i
                    while (i < lineEnd && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '.' || code[i] == '-')) i++
                    if (code.substring(start, i).lowercase() in yamlScalars) {
                        tokens += SyntaxToken(start, i, SyntaxTokenType.KEYWORD)
                    }
                }
                else -> i++
            }
        }
        lineStart = lineEnd + 1
    }
    return tokens
}

/** Returns the index of the ':' terminating a YAML mapping key, or -1. */
private fun yamlKeyColon(code: String, from: Int, lineEnd: Int): Int {
    var i = from
    if (i < lineEnd && (code[i] == '"' || code[i] == '\'')) {
        val end = scanStringEnd(
            code, i + 1, code[i],
            allowEscape = code[i] == '"', multiline = false, doubled = code[i] == '\'', tripleDelim = null
        )
        i = end
    } else {
        while (i < lineEnd && code[i] != ':' && code[i] != '#') i++
    }
    while (i < lineEnd && code[i] == ' ') i++
    return if (i < lineEnd && code[i] == ':') {
        if (i + 1 >= lineEnd || code[i + 1] == ' ' || code[i + 1] == '\t') i else -1
    } else {
        -1
    }
}

// ---------------------------------------------------------------------------
// XML / HTML
// ---------------------------------------------------------------------------

private fun isXmlNameChar(c: Char): Boolean = c.isLetterOrDigit() || c in "_.:-"

private fun scanXml(code: String): List<SyntaxToken> {
    val tokens = mutableListOf<SyntaxToken>()
    val n = code.length
    var i = 0
    var inTag = false
    while (i < n) {
        if (!inTag) {
            when {
                code.startsWith("<!--", i) -> {
                    val close = code.indexOf("-->", i + 4)
                    val end = if (close == -1) n else close + 3
                    tokens += SyntaxToken(i, end, SyntaxTokenType.COMMENT)
                    i = end
                }
                code.startsWith("<!", i) || code.startsWith("<?", i) -> {
                    // DOCTYPE / CDATA / processing instructions.
                    val close = code.indexOf('>', i)
                    val end = if (close == -1) n else close + 1
                    tokens += SyntaxToken(i, end, SyntaxTokenType.TAG)
                    i = end
                }
                code.startsWith("</", i) || (code[i] == '<' && i + 1 < n && code[i + 1].isLetter()) -> {
                    var j = i + 1
                    val closing = code[j] == '/'
                    if (closing) j++
                    var k = j
                    while (k < n && isXmlNameChar(code[k])) k++
                    if (k == j) {
                        i++
                        continue
                    }
                    tokens += SyntaxToken(i, k, SyntaxTokenType.TAG)
                    i = k
                    inTag = !closing
                }
                else -> i++ // text content (stray '<' stays plain)
            }
        } else {
            val c = code[i]
            when {
                c == '"' || c == '\'' -> {
                    val end = scanStringEnd(code, i + 1, c, allowEscape = false, multiline = false, doubled = false, tripleDelim = null)
                    tokens += SyntaxToken(i, end, SyntaxTokenType.STRING)
                    i = end
                }
                c.isLetter() || c == '_' || c == ':' -> {
                    var j = i
                    while (j < n && isXmlNameChar(code[j])) j++
                    tokens += SyntaxToken(i, j, SyntaxTokenType.ATTRIBUTE)
                    i = j
                }
                c == '=' -> {
                    tokens += SyntaxToken(i, i + 1, SyntaxTokenType.OPERATOR)
                    i++
                }
                c == '>' -> {
                    inTag = false
                    i++
                }
                else -> i++
            }
        }
    }
    return tokens
}

// ---------------------------------------------------------------------------
// Markdown
// ---------------------------------------------------------------------------

private val orderedListRegex = Regex("^\\d{1,9}[.)](?=\\s)")

private fun scanMarkdown(code: String): List<SyntaxToken> {
    val tokens = mutableListOf<SyntaxToken>()
    val n = code.length
    var lineStart = 0
    while (lineStart < n) {
        val lineEnd = code.indexOf('\n', lineStart).let { if (it == -1) n else it }
        var i = lineStart
        while (i < lineEnd && (code[i] == ' ' || code[i] == '\t')) i++

        val rest = lineEnd - i
        val isFence = rest >= 3 && code[i] == '`' && code[i + 1] == '`' && code[i + 2] == '`'
        val headingDepth = markdownHeadingDepth(code, i, lineEnd)
        val isQuote = code[i] == '>'
        val bullet = i < lineEnd && code[i] in "-*+" &&
            (i + 1 >= lineEnd || code[i + 1] == ' ' || code[i + 1] == '\t')
        val ordered = orderedListRegex.matchAt(code, i) != null

        when {
            isFence || headingDepth > 0 -> {
                tokens += SyntaxToken(i, lineEnd, SyntaxTokenType.KEYWORD)
                lineStart = lineEnd + 1
                continue
            }
            isQuote -> {
                tokens += SyntaxToken(i, lineEnd, SyntaxTokenType.COMMENT)
                lineStart = lineEnd + 1
                continue
            }
            bullet || ordered -> {
                tokens += SyntaxToken(i, i + 1, SyntaxTokenType.OPERATOR)
                i++
            }
        }

        // Inline pass: `code`, **bold**, _bold_, bare URLs.
        while (i < lineEnd) {
            val c = code[i]
            when {
                c == '`' -> {
                    var runEnd = i
                    while (runEnd < lineEnd && code[runEnd] == '`') runEnd++
                    val close = code.indexOf(code.substring(i, runEnd), runEnd)
                    val end = if (close == -1) lineEnd else close + (runEnd - i)
                    tokens += SyntaxToken(i, end, SyntaxTokenType.STRING)
                    i = end
                }
                (c == '*' || c == '_') && i + 1 < lineEnd && code[i + 1] == c -> {
                    val marker = code.substring(i, i + 2)
                    val close = code.indexOf(marker, i + 2)
                    val end = if (close == -1) lineEnd else close + 2
                    tokens += SyntaxToken(i, end, SyntaxTokenType.PROPERTY)
                    i = end
                }
                c == 'h' && isUrlAt(code, i, lineEnd) -> {
                    var j = i
                    while (j < lineEnd && !code[j].isWhitespace()) j++
                    tokens += SyntaxToken(i, j, SyntaxTokenType.ANNOTATION)
                    i = j
                }
                else -> i++
            }
        }
        lineStart = lineEnd + 1
    }
    return tokens
}

private fun markdownHeadingDepth(code: String, i: Int, lineEnd: Int): Int {
    var depth = 0
    var j = i
    while (j < lineEnd && code[j] == '#' && depth < 7) {
        depth++
        j++
    }
    return if (depth in 1..6 && (j >= lineEnd || code[j] == ' ' || code[j] == '\t')) depth else 0
}

private fun isUrlAt(code: String, i: Int, lineEnd: Int): Boolean {
    val http = "http://"
    val https = "https://"
    return (i + https.length <= lineEnd && code.regionMatches(i, https, 0, https.length)) ||
        (i + http.length <= lineEnd && code.regionMatches(i, http, 0, http.length))
}

// ---------------------------------------------------------------------------
// Language specs
// ---------------------------------------------------------------------------

private val kotlinSpec = CLikeSpec(
    keywords = setOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
        "companion", "const", "constructor", "continue", "crossinline", "data",
        "do", "dynamic", "else", "enum", "expect", "external", "false", "field",
        "final", "finally", "for", "fun", "get", "if", "import", "in", "infix",
        "init", "inline", "inner", "interface", "internal", "is", "lateinit",
        "noinline", "null", "object", "open", "operator", "out", "override",
        "package", "param", "private", "property", "protected", "public",
        "reified", "return", "sealed", "set", "super", "suspend", "tailrec",
        "this", "throw", "true", "try", "typealias", "val", "var", "vararg",
        "when", "where", "while"
    ),
    typeWords = setOf(
        "Any", "Boolean", "BooleanArray", "Byte", "ByteArray", "Char",
        "CharArray", "Double", "DoubleArray", "Float", "FloatArray", "Int",
        "IntArray", "List", "Long", "LongArray", "Map", "MutableList",
        "MutableMap", "MutableSet", "Nothing", "Number", "Pair", "Sequence",
        "Set", "Short", "ShortArray", "String", "Triple", "Unit"
    ),
    lineComments = listOf("//"),
    blockComment = "/*" to "*/",
    tripleQuotes = true,
    rawTriple = true,
    annotations = true,
    stringDelims = setOf('"', '\'') // char literals too
)

private val javaSpec = CLikeSpec(
    keywords = setOf(
        "abstract", "assert", "break", "case", "catch", "class", "const",
        "continue", "default", "do", "else", "enum", "extends", "false",
        "final", "finally", "for", "goto", "if", "implements", "import",
        "instanceof", "interface", "native", "new", "null", "package",
        "permits", "private", "protected", "public", "record", "return",
        "sealed", "static", "strictfp", "super", "switch", "synchronized",
        "this", "throw", "throws", "transient", "true", "try", "var",
        "volatile", "while", "yield"
    ),
    typeWords = setOf(
        "ArrayList", "Boolean", "Byte", "Character", "CharSequence", "Class",
        "Collection", "Double", "Float", "HashMap", "Integer", "List", "Long",
        "Map", "Object", "Optional", "Set", "Short", "Stream", "String", "Void",
        // Primitive types color as types, not keywords.
        "boolean", "byte", "char", "double", "float", "int", "long", "short", "void"
    ),
    lineComments = listOf("//"),
    blockComment = "/*" to "*/",
    annotations = true,
    stringDelims = setOf('"', '\'') // char literals too
)

private val pythonSpec = CLikeSpec(
    keywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "case", "class", "continue", "def", "del", "elif", "else",
        "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "match", "nonlocal", "not", "or", "pass", "raise",
        "return", "try", "while", "with", "yield"
    ),
    typeWords = setOf(
        "bool", "bytearray", "bytes", "complex", "dict", "float", "frozenset",
        "int", "list", "object", "set", "str", "tuple", "type"
    ),
    hashComment = true,
    tripleQuotes = true,
    annotations = true,
    stringDelims = setOf('"', '\''),
    rawPrefixes = setOf("r", "u", "b", "f", "rb", "br", "rf", "fr", "bf", "fb")
)

private val javascriptSpec = CLikeSpec(
    keywords = setOf(
        "async", "await", "break", "case", "catch", "class", "const",
        "continue", "debugger", "default", "delete", "do", "else", "enum",
        "export", "extends", "false", "finally", "for", "from", "function",
        "get", "if", "import", "in", "instanceof", "let", "new", "null", "of",
        "return", "set", "static", "super", "switch", "this", "throw", "true",
        "typeof", "undefined", "var", "void", "while", "with", "yield"
    ),
    typeWords = setOf(
        "Array", "Boolean", "Date", "Error", "JSON", "Map", "Math", "Number",
        "Object", "Promise", "RegExp", "Set", "String", "Symbol"
    ),
    lineComments = listOf("//"),
    blockComment = "/*" to "*/",
    stringDelims = setOf('"', '\'', '`'),
    multilineDelims = setOf('`')
)

private val typescriptSpec = CLikeSpec(
    keywords = javascriptSpec.keywords + setOf(
        "abstract", "any", "as", "asserts", "declare", "implements", "infer",
        "interface", "is", "keyof", "namespace", "never", "override",
        "private", "protected", "public", "readonly", "satisfies", "type",
        "unknown"
    ),
    typeWords = javascriptSpec.typeWords + setOf(
        "bigint", "boolean", "number", "object", "string", "symbol",
        "Omit", "Partial", "Pick", "Record", "Readonly"
    ),
    lineComments = listOf("//"),
    blockComment = "/*" to "*/",
    stringDelims = setOf('"', '\'', '`'),
    multilineDelims = setOf('`'),
    annotations = true
)

private val bashSpec = CLikeSpec(
    keywords = setOf(
        "alias", "bg", "break", "case", "cd", "continue", "declare", "dirs",
        "do", "done", "echo", "elif", "else", "esac", "eval", "exec", "exit",
        "export", "false", "fg", "fi", "for", "function", "getopts", "help",
        "if", "in", "jobs", "kill", "local", "popd", "printf", "pushd", "pwd",
        "read", "readonly", "return", "select", "set", "shift", "shopt",
        "source", "test", "then", "time", "trap", "true", "type", "typeset",
        "ulimit", "umask", "unalias", "unset", "until", "wait", "while"
    ),
    hashComment = true,
    hashNeedsBoundary = true,
    stringDelims = setOf('"', '\''),
    multilineDelims = setOf('"', '\''),
    noEscapeDelims = setOf('\''),
    uppercaseType = false,
    dollarVars = true
)

private val sqlSpec = CLikeSpec(
    keywords = setOf(
        "add", "all", "alter", "and", "any", "as", "asc", "begin", "between",
        "by", "case", "cast", "check", "column", "commit", "constraint",
        "convert", "create", "cross", "current_date", "current_time",
        "current_timestamp", "default", "delete", "desc", "distinct", "drop",
        "else", "end", "exists", "false", "fetch", "first", "foreign", "from",
        "full", "function", "grant", "group", "having", "ilike", "in", "index",
        "inner", "insert", "into", "is", "join", "key", "left", "like", "limit",
        "next", "not", "null", "offset", "on", "only", "or", "order", "outer",
        "over", "partition", "procedure", "references", "revoke", "rollback",
        "row", "rows", "select", "set", "similar", "some", "table", "then",
        "to", "transaction", "trigger", "union", "unique", "update", "values",
        "view", "when", "where", "window", "with"
    ),
    typeWords = setOf(
        "array", "bigint", "bigserial", "binary", "bit", "blob", "bool",
        "boolean", "bytea", "char", "clob", "date", "datetime", "decimal",
        "double", "float", "int", "integer", "interval", "json", "jsonb",
        "money", "nvarchar", "precision", "real", "serial", "smallint",
        "text", "time", "timestamp", "timestamptz", "tinyint", "uuid",
        "varbinary", "varchar", "xml"
    ),
    lineComments = listOf("--"),
    blockComment = "/*" to "*/",
    stringDelims = setOf('\'', '"'),
    doubledQuoteEscape = true,
    caseInsensitive = true,
    uppercaseType = false
)
