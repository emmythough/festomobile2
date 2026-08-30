package com.example.ui.components.markdown

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.FestoTheme
import kotlinx.coroutines.delay

/**
 * Renders a ```mermaid fence (see [MessageBlock.Mermaid]) inside a real
 * [android.webkit.WebView] -- zero new Gradle dependencies, mermaid.js is
 * pulled from a pinned jsDelivr CDN URL by a minimal HTML shell built in
 * [buildMermaidHtml].
 *
 * Failure contract (the fallback is never optional): every path that can
 * leave a blank or half-broken WebView is wired to flip this block into
 * [CodeBlockView] with the raw source + language "mermaid" plus a caption:
 *  1. JS side -- `window.onerror`, the `typeof mermaid` check (CDN script
 *     never executed), and the `mermaid.render` promise rejection (bad
 *     diagram syntax) all call the `AndroidBridge.onError` JS interface.
 *  2. WebView side -- [WebViewClient.onReceivedError] /
 *     [WebViewClient.onReceivedHttpError] catch network/HTTP failures.
 *  3. Catch-all -- a [MERMAID_LOAD_TIMEOUT_MS] timeout fires when no
 *     callback above ever ran (offline before the WebView could even
 *     report, hung JS engine, pathologically slow CDN).
 */
private const val MERMAID_CDN_URL =
    "https://cdn.jsdelivr.net/npm/mermaid@10.9.3/dist/mermaid.min.js"

/** Base URL handed to [WebView.loadDataWithBaseURL] so the page has an
 * https origin; the script tag itself uses the absolute URL above. */
private const val MERMAID_BASE_URL = "https://cdn.jsdelivr.net/"

/** Catch-all fallback trigger. Long enough for a cold CDN fetch of the
 * ~3.3 MB bundle on a slow phone, short enough that the user is never
 * stuck staring at a spinner for a diagram that will never come. */
private const val MERMAID_LOAD_TIMEOUT_MS = 15_000L

/** WebView-measured heights arrive in CSS px == dp at the default zoom;
 * clamped so no diagram can collapse to zero or flood the transcript. */
private const val MIN_DIAGRAM_HEIGHT_DP = 96
private const val MAX_DIAGRAM_HEIGHT_DP = 480
private const val INITIAL_DIAGRAM_HEIGHT_DP = 220

/** Why mermaid@10.9.3: the final v10 release (v11 ships ESM-first, whose
 * dist files aren't plain UMD script tags), and this exact file was
 * verified to serve HTTP 200 from jsDelivr with a 1-year immutable cache.
 * A pinned version, never "@latest": a silent upstream release must not
 * change what the chat renders. */
private enum class MermaidRenderState { Loading, Ready, Failed }

/** The app theme, flattened to web colors for the HTML shell. Every value
 * is a real [com.example.ui.theme.FestoExtendedColors] /
 * MaterialTheme.colorScheme color -- the same palette CodeBlockView,
 * MessageChartBlock and MarkdownTextView paint with, in both modes. */
internal data class MermaidPalette(
    val background: String,
    val text: String,
    val line: String,
    val nodeFill: String,
    val nodeBorder: String,
    val clusterFill: String,
    val clusterBorder: String,
    val dark: Boolean
)

/** ARGB int -> "#RRGGBB" (CSS rejects Android's #AARRGGBB order; all
 * palette colors are opaque). Same .toArgb() pattern as MarkdownTextView. */
private fun Color.toWebHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())

/** Embeds [raw] as a JavaScript double-quoted string literal. Escapes
 * backslash, quote, line breaks and control chars, and rewrites every
 * "<" to the \u003c escape so diagram content can never terminate the
 * inline <script> block (a literal "</script>" inside the source would
 * otherwise end the script early and silently kill rendering). */
internal fun escapeForInlineScript(raw: String): String {
    val sb = StringBuilder(raw.length + 16)
    for (ch in raw) {
        when {
            ch == '\\' -> sb.append("\\\\")
            ch == '"' -> sb.append("\\\"")
            ch == '\n' -> sb.append("\\n")
            ch == '\r' -> sb.append("\\r")
            ch == '\t' -> sb.append("\\t")
            ch == '<' -> sb.append("\\u003c")
            ch < ' ' -> sb.append("\\u%04x".format(ch.code))
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/** The minimal HTML shell: CDN <script> + mermaid.initialize/render +
 * size reporting through the AndroidBridge JS interface injected by
 * [MessageMermaidBlock]. internal for tests. */
internal fun buildMermaidHtml(source: String, palette: MermaidPalette): String {
    val embeddedSource = escapeForInlineScript(source)
    val theme = if (palette.dark) "dark" else "neutral"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html, body { margin: 0; padding: 0; background: ${palette.background}; }
          #diagram { padding: 10px; text-align: center; }
          #diagram svg { max-width: 100%; height: auto; }
        </style>
        </head>
        <body>
        <div id="diagram"></div>
        <script src="$MERMAID_CDN_URL"></script>
        <script>
        (function () {
          var source = "$embeddedSource";
          var done = false;
          function fail(message) {
            if (done) return;
            done = true;
            if (window.AndroidBridge) window.AndroidBridge.onError(String(message));
          }
          function ready(height) {
            if (done) return;
            done = true;
            if (window.AndroidBridge) window.AndroidBridge.onRendered(height);
          }
          window.onerror = function (message) { fail(message); return true; };
          if (typeof mermaid === 'undefined') {
            fail('mermaid.js did not load from the CDN');
            return;
          }
          try {
            mermaid.initialize({
              startOnLoad: false,
              theme: '$theme',
              securityLevel: 'strict',
              suppressErrorRendering: true,
              themeVariables: {
                fontFamily: 'Roboto, "Helvetica Neue", sans-serif',
                fontSize: '14px',
                background: '${palette.background}',
                primaryColor: '${palette.nodeFill}',
                primaryTextColor: '${palette.text}',
                primaryBorderColor: '${palette.nodeBorder}',
                lineColor: '${palette.line}',
                secondaryColor: '${palette.clusterFill}',
                tertiaryColor: '${palette.clusterFill}',
                clusterBkg: '${palette.clusterFill}',
                clusterBorder: '${palette.clusterBorder}',
                edgeLabelBackground: '${palette.background}',
                noteBkgColor: '${palette.clusterFill}',
                noteTextColor: '${palette.text}',
                noteBorderColor: '${palette.nodeBorder}',
                titleColor: '${palette.text}'
              }
            });
            mermaid.render('mmd-' + Date.now(), source).then(function (result) {
              var container = document.getElementById('diagram');
              container.innerHTML = result.svg;
              if (result.bindFunctions) result.bindFunctions(container);
              var svg = container.querySelector('svg');
              if (!svg) { fail('mermaid produced no SVG'); return; }
              var rect = svg.getBoundingClientRect();
              var height = Math.ceil(rect && rect.height ? rect.height : 0) + 20;
              ready(height);
            }).catch(function (error) {
              fail((error && (error.message || error.str)) || String(error));
            });
          } catch (error) {
            fail((error && error.message) || String(error));
          }
        })();
        </script>
        </body>
        </html>
    """.trimIndent()
}

/** JS interface installed as window.AndroidBridge. Callbacks arrive on a
 * JS binder thread; [MessageMermaidBlock] hops them to the main thread. */
private class MermaidJsBridge(
    private val onRendered: (heightCssPx: Int) -> Unit,
    private val onError: (message: String) -> Unit
) {
    @JavascriptInterface
    fun onRendered(height: Int) {
        onRendered(height)
    }

    @JavascriptInterface
    fun onError(message: String?) {
        onError(message ?: "unknown mermaid error")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MessageMermaidBlock(
    source: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    var state by remember(source) { mutableStateOf(MermaidRenderState.Loading) }
    var diagramHeight by remember(source) {
        mutableStateOf(INITIAL_DIAGRAM_HEIGHT_DP.dp)
    }

    // MarkdownTextView's theme pattern: MaterialTheme.colorScheme for text,
    // FestoTheme.colors for the extended surfaces/acents -- resolved in
    // composition, baked into the HTML, refreshed when the theme changes.
    val palette = MermaidPalette(
        // Transparent, not a real surface color -- the outer Box below no
        // longer paints a card background of its own, so the diagram's
        // page/canvas backdrop needs to be transparent too or it would show
        // as a mismatched color patch floating on the chat background
        // instead of genuinely floating. Node/cluster fills are separate
        // fields (nodeFill, clusterFill below) and stay real solid colors --
        // this only affects the empty page area around the diagram.
        background = "transparent",
        text = MaterialTheme.colorScheme.onSurface.toWebHex(),
        line = extendedColors.inkTertiary.toWebHex(),
        nodeFill = extendedColors.surfaceContainer.toWebHex(),
        nodeBorder = extendedColors.borderMedium.toWebHex(),
        clusterFill = extendedColors.surfaceSubtle.toWebHex(),
        clusterBorder = extendedColors.borderHairline.toWebHex(),
        dark = extendedColors.isDark
    )
    val html = remember(source, palette) { buildMermaidHtml(source, palette) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Catch-all fallback: if none of the error callbacks above ran within
    // the timeout (offline, hung JS, unreachable CDN), fail visibly.
    LaunchedEffect(source, html) {
        delay(MERMAID_LOAD_TIMEOUT_MS)
        if (state == MermaidRenderState.Loading) state = MermaidRenderState.Failed
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (state == MermaidRenderState.Failed) {
            // The real, visible fallback: the raw source as a plain code
            // block, exactly like any other fence, plus a caption.
            CodeBlockView(code = source, language = "mermaid")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Couldn't render this diagram.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = extendedColors.inkTertiary,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            // No card chrome by design: no fill, no border -- the diagram
            // floats directly on the chat background (see the palette's
            // "transparent" background above for why that's paired with
            // this removal, not just this Box in isolation).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isVerticalScrollBarEnabled = false
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    mainHandler.post {
                                        if (state == MermaidRenderState.Loading) {
                                            state = MermaidRenderState.Failed
                                        }
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: WebResourceResponse
                                ) {
                                    mainHandler.post {
                                        if (state == MermaidRenderState.Loading) {
                                            state = MermaidRenderState.Failed
                                        }
                                    }
                                }
                            }
                            addJavascriptInterface(
                                MermaidJsBridge(
                                    onRendered = { heightCssPx ->
                                        mainHandler.post {
                                            if (state == MermaidRenderState.Loading) {
                                                diagramHeight = heightCssPx
                                                    .coerceIn(MIN_DIAGRAM_HEIGHT_DP, MAX_DIAGRAM_HEIGHT_DP)
                                                    .dp
                                                state = MermaidRenderState.Ready
                                            }
                                        }
                                    },
                                    onError = { _ ->
                                        mainHandler.post {
                                            if (state == MermaidRenderState.Loading) {
                                                state = MermaidRenderState.Failed
                                            }
                                        }
                                    }
                                ),
                                "AndroidBridge"
                            )
                        }.also { webViewRef.value = it }
                    },
                    update = { webView ->
                        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        // load once per (source, theme) HTML revision; the
                        // view survives recomposition, so diff via tag.
                        if (webView.tag as? String != html) {
                            webView.tag = html
                            webView.loadDataWithBaseURL(
                                MERMAID_BASE_URL,
                                html,
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(diagramHeight)
                )

                if (state == MermaidRenderState.Loading) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Rendering diagram...",
                            style = MaterialTheme.typography.labelMedium,
                            color = extendedColors.inkTertiary
                        )
                    }
                }
            }
        }
    }

    // Destroy the WebView when the block leaves the transcript or flips to
    // the fallback (the AndroidView branch is removed there). Keyed on the
    // Failed flag so Loading -> Ready does NOT tear the view down.
    DisposableEffect(state == MermaidRenderState.Failed) {
        onDispose {
            webViewRef.value?.let { webView ->
                webView.stopLoading()
                webView.destroy()
            }
            webViewRef.value = null
        }
    }
}
