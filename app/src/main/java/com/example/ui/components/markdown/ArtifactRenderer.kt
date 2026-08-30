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
 * Renders a ```artifact fence (see [MessageBlock.Artifact]) inside a real
 * [android.webkit.WebView] -- the third rendering lane next to
 * ```chart ([MessageChartBlock], simple data plots) and ```mermaid
 * ([MessageMermaidBlock], diagrams), for genuinely interactive inline
 * artifacts: sliders, live-recalculating views, small dashboards. Zero new
 * Gradle dependencies; unlike mermaid there is no CDN call -- the payload
 * is purely local markup, which is what makes the shorter timeout below
 * defensible.
 *
 * The payload contract differs fundamentally from mermaid's: the fence body
 * is HTML *markup* (possibly with its own <style>/<script> tags), inserted
 * as markup -- it is never escaped into a JS string literal, and the model's
 * code needs to know nothing about any bridge or callback contract. The
 * Android side alone decides "it loaded" and "how tall it is" (see
 * [buildArtifactBootstrapScript]).
 *
 * Input handling ([buildArtifactHtml]) has two branches:
 *  - a payload containing "<html" is treated as an already-complete
 *    document and gets the bootstrap script spliced in before its last
 *    "</body>" (else last "</html>", else appended at the very end);
 *  - anything else is a body-content fragment wrapped in a shell this
 *    file builds (DOCTYPE, charset/viewport meta, theme-color CSS
 *    variables, base widget styling), then the bootstrap script.
 *
 * Failure contract -- the same three layers as [MermaidRenderer.kt],
 * mirrored deliberately rather than extracted: sharing a WebViewClient /
 * timeout helper would require editing MermaidRenderer.kt, and that lane
 * must stay byte-for-byte untouched (this work is purely additive). Every
 * path that can leave a blank or half-broken WebView flips this block into
 * [CodeBlockView] with the raw fence body + language "html" plus a caption:
 *  1. JS side -- `window.onerror` calls the `AndroidBridge.onError` JS
 *     interface and returns true (Mermaid's exact pattern).
 *  2. WebView side -- [WebViewClient.onReceivedError] /
 *     [WebViewClient.onReceivedHttpError] catch main-frame load failures.
 *     Subresource errors (a broken relative image, say) are ignored: a
 *     404 <img> cannot blank an otherwise-working interactive artifact,
 *     and demoting it to a code block would be strictly worse.
 *  3. Catch-all -- a [ARTIFACT_LOAD_TIMEOUT_MS] timeout fires when no
 *     callback above ever ran (hung JS engine, script that never finishes,
 *     pathological payload).
 *
 * Sandboxing (this runs arbitrary model-authored JS): file/content access
 * off in every flavor WebSettings offers, geolocation untouched (default
 * off), a minimal two-method [ArtifactJsBridge] as the page's only native
 * surface, and [WebViewClient.shouldOverrideUrlLoading] refusing every
 * navigation attempt -- this is a content sandbox, not a browser; a
 * clicked link inside artifact content must never navigate the WebView
 * away from the artifact.
 */

/** Catch-all fallback trigger. MermaidRenderer.kt waits 15s because its
 * first render depends on fetching the ~3.3 MB mermaid.js bundle from a
 * CDN; an artifact renders purely local markup with no network dependency,
 * so the longest legitimate wait is HTML parse + the model's own inline
 * scripts + the [ARTIFACT_SETTLE_DELAY_MS] settle. 5s is generous headroom
 * for a slow phone and cuts the worst-case spinner by 3x versus mermaid. */
internal const val ARTIFACT_LOAD_TIMEOUT_MS = 5_000L

/** Settle delay between window load and the height measurement: long
 * enough that inline initializers which queue their own zero-timeout DOM
 * mutations (a very common pattern in generated widget code) have flushed;
 * short enough to stay imperceptible inside the 5s budget. Dead center of
 * the 150-300ms window on purpose -- it has slack on both sides. */
internal const val ARTIFACT_SETTLE_DELAY_MS = 200L

/** WebView-measured heights arrive in CSS px == dp at the default zoom;
 * clamped so no artifact can collapse to zero or flood the transcript.
 * Same 96dp floor as mermaid (a zero-height widget is as useless as a
 * zero-height diagram). The ceiling is taller than mermaid's 480dp --
 * dashboard-style artifacts (title + a few controls + stat tiles)
 * legitimately land around 500-700dp, roughly one phone screen -- while
 * still refusing to let a runaway page push the rest of the chat away. */
internal const val MIN_ARTIFACT_HEIGHT_DP = 96
internal const val MAX_ARTIFACT_HEIGHT_DP = 720
internal const val INITIAL_ARTIFACT_HEIGHT_DP = 220

/** Base URL handed to [WebView.loadDataWithBaseURL]: a real https origin
 * (so the page's JS gets a sane same-origin context and localStorage works,
 * matching mermaid's https CDN base) on a host that resolves to nothing --
 * relative subresource fetches fail fast and are ignored by the error
 * client; file:// and content:// access is off regardless. */
internal const val ARTIFACT_BASE_URL = "https://localhost/"

/** The id stamped on the injected bootstrap <script> -- also the anchor the
 * unit tests assert on. internal for tests. */
internal const val ARTIFACT_BOOTSTRAP_SCRIPT_ID = "festo-artifact-bootstrap"

/** ARGB int -> "#RRGGBB" (CSS rejects Android's #AARRGGBB order; all
 * palette colors are opaque). Same .toArgb() pattern as MarkdownTextView.
 * Private duplicate of MermaidRenderer's helper on purpose: widening the
 * original to internal would mean editing MermaidRenderer.kt. */
private fun Color.toWebHex(): String =
    String.format("#%06X", 0xFFFFFF and toArgb())

/** True when [payload] should be loaded as-is (plus the bootstrap script)
 * rather than wrapped in this file's fragment shell. "<html" marks an
 * already-complete document, case-insensitively (models write
 * <!DOCTYPE HTML> too). A "fragment" that smuggles a literal "</body>" or
 * "</html>" is routed here as well: wrapped as a fragment it would punch a
 * hole through the shell's own closing tags BEFORE the bootstrap script,
 * producing broken HTML -- handled as a document instead, where the
 * injection rules below place the script correctly. */
internal fun isCompleteDocument(payload: String): Boolean =
    payload.contains("<html", ignoreCase = true) ||
        payload.contains("</body>", ignoreCase = true) ||
        payload.contains("</html>", ignoreCase = true)

/** Splices [bootstrap] into an already-complete document: immediately
 * before the LAST "</body>" (so it runs after every element and inline
 * script of the payload), else before the LAST "</html>", else appended at
 * the very end of the string as a last resort -- the bootstrap script is
 * never silently dropped. internal for tests. */
internal fun injectBootstrap(document: String, bootstrap: String): String {
    val bodyIdx = document.lastIndexOf("</body>", ignoreCase = true)
    if (bodyIdx >= 0) {
        return document.substring(0, bodyIdx) + bootstrap + document.substring(bodyIdx)
    }
    val htmlIdx = document.lastIndexOf("</html>", ignoreCase = true)
    if (htmlIdx >= 0) {
        return document.substring(0, htmlIdx) + bootstrap + document.substring(htmlIdx)
    }
    return document + bootstrap
}

/** Wraps a body-content [fragment] in the artifact shell: DOCTYPE +
 * charset + viewport, the theme palette from [MermaidPalette] (the SAME
 * struct MermaidRenderer builds from the SAME FestoTheme.colors /
 * MaterialTheme.colorScheme reads -- no second palette exists) exposed as
 * CSS custom properties so the model's own CSS can theme against them, a
 * couple of base rules so default widgets don't fight the theme
 * (accent-color makes native sliders/checkboxes pick up the palette), the
 * fragment, then [bootstrap]. internal for tests. */
internal fun wrapFragment(fragment: String, palette: MermaidPalette, bootstrap: String): String {
    val colorScheme = if (palette.dark) "dark" else "light"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          :root {
            color-scheme: $colorScheme;
            --festo-bg: ${palette.background};
            --festo-text: ${palette.text};
            --festo-line: ${palette.line};
            --festo-node-fill: ${palette.nodeFill};
            --festo-node-border: ${palette.nodeBorder};
            --festo-cluster-fill: ${palette.clusterFill};
            --festo-cluster-border: ${palette.clusterBorder};
          }
          html, body {
            margin: 0; padding: 0;
            background: var(--festo-bg);
            color: var(--festo-text);
            font-family: Roboto, "Helvetica Neue", sans-serif;
            font-size: 14px;
          }
          input[type="range"], input[type="checkbox"], input[type="radio"],
          progress, meter, button, select {
            accent-color: var(--festo-node-border);
          }
        </style>
        </head>
        <body>
        $fragment
        $bootstrap
        </body>
        </html>
    """.trimIndent()
}

/** Builds the final document for a ```artifact payload: the two input
 * branches described on [MessageBlock.Artifact]. The payload is inserted
 * as markup in both branches -- it is never escaped into a JS string, so
 * there is no escapeForInlineScript step here. internal for tests. */
internal fun buildArtifactHtml(payload: String, palette: MermaidPalette): String {
    val bootstrap = buildArtifactBootstrapScript()
    return if (isCompleteDocument(payload)) {
        injectBootstrap(payload, bootstrap)
    } else {
        wrapFragment(payload, palette, bootstrap)
    }
}

/** The bootstrap script injected into every artifact document (no payload
 * content is ever interpolated into it -- it is the same fixed script for
 * every artifact). Contract:
 *  - `window.onerror` reports through AndroidBridge.onError and returns
 *    true, exactly like Mermaid's shell. No ready-latch: an interactive
 *    artifact keeps living long after onRendered, and the Android side
 *    only acts on errors while still Loading, so post-ready interaction
 *    errors are reported and ignored rather than blanking a working
 *    dashboard. (Caveat, documented in the class KDoc: in the
 *    full-document branch the payload's own early scripts run BEFORE this
 *    script installs the handler -- errors they throw before install time
 *    are covered by layer 3, the timeout, instead.)
 *  - On window load (or immediately if the document is somehow already
 *    complete), waits [ARTIFACT_SETTLE_DELAY_MS] for initial inline JS to
 *    finish its DOM mutations, then measures document.body.scrollHeight
 *    and reports AndroidBridge.onRendered(height). body.scrollHeight is
 *    chosen over documentElement.scrollHeight because Blink floors
 *    documentElement.scrollHeight at the viewport height: every artifact
 *    shorter than the WebView's initial placeholder height would be
 *    reported viewport-tall and padded with dead space, while
 *    body.scrollHeight grows AND shrinks with the artifact's actual
 *    content. (Known tradeoff: absolutely-positioned elements escaping
 *    body's flow are not counted -- acceptable for v1, and the 96dp floor
 *    keeps the result usable.)
 *  - The whole settle-and-measure path is try/catch'd at every level: a
 *    failing DOM read still reports SOME height (0, which the Android side
 *    clamps to the floor) rather than silently never calling onRendered
 *    and riding the timeout out.
 * internal for tests. */
internal fun buildArtifactBootstrapScript(): String = """
    <script id="$ARTIFACT_BOOTSTRAP_SCRIPT_ID">
    (function () {
      function report(height) {
        try {
          if (window.AndroidBridge && window.AndroidBridge.onRendered) {
            window.AndroidBridge.onRendered(Math.max(1, Math.round(height || 0)));
          }
        } catch (ignored) {}
      }
      window.onerror = function (message) {
        try {
          if (window.AndroidBridge && window.AndroidBridge.onError) {
            window.AndroidBridge.onError(String(message));
          }
        } catch (ignored) {}
        return true;
      };
      function measure() {
        try {
          var h = document.body
            ? document.body.scrollHeight
            : document.documentElement.scrollHeight;
          report(h);
        } catch (e) {
          report(0);
        }
      }
      try {
        var settle = function () {
          window.setTimeout(measure, $ARTIFACT_SETTLE_DELAY_MS);
        };
        if (document.readyState === 'complete') {
          settle();
        } else {
          window.addEventListener('load', settle);
        }
      } catch (e) {
        report(0);
      }
    })();
    </script>
""".trimIndent()

/** JS interface installed as window.AndroidBridge -- the page's ONLY
 * native surface, two methods, nothing else (no native capability beyond
 * ready/error/height is exposed to model-authored JS). Callbacks arrive on
 * a JS binder thread; [MessageArtifactBlock] hops them to the main thread. */
private class ArtifactJsBridge(
    private val onRendered: (heightCssPx: Int) -> Unit,
    private val onError: (message: String) -> Unit
) {
    @JavascriptInterface
    fun onRendered(height: Int) {
        onRendered(height)
    }

    @JavascriptInterface
    fun onError(message: String?) {
        onError(message ?: "unknown artifact error")
    }
}

private enum class ArtifactRenderState { Loading, Ready, Failed }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MessageArtifactBlock(
    html: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    var state by remember(html) { mutableStateOf(ArtifactRenderState.Loading) }
    var artifactHeight by remember(html) {
        mutableStateOf(INITIAL_ARTIFACT_HEIGHT_DP.dp)
    }

    // Identical reads to MermaidRenderer.MessageMermaidBlock, feeding the
    // SAME MermaidPalette struct (reused here under its original name --
    // it is the shared theme palette, just named after its first
    // consumer). Kept as a deliberate copy instead of extracting a shared
    // builder: this work must stay purely additive, and MermaidRenderer.kt
    // must remain byte-for-byte untouched. MarkdownTextView's theme
    // pattern: MaterialTheme.colorScheme for text, FestoTheme.colors for
    // the extended surfaces/acents -- resolved in composition, baked into
    // the HTML, refreshed when the theme changes.
    val palette = MermaidPalette(
        background = extendedColors.surfaceDialog.toWebHex(),
        text = MaterialTheme.colorScheme.onSurface.toWebHex(),
        line = extendedColors.inkTertiary.toWebHex(),
        nodeFill = extendedColors.surfaceContainer.toWebHex(),
        nodeBorder = extendedColors.borderMedium.toWebHex(),
        clusterFill = extendedColors.surfaceSubtle.toWebHex(),
        clusterBorder = extendedColors.borderHairline.toWebHex(),
        dark = extendedColors.isDark
    )
    val document = remember(html, palette) { buildArtifactHtml(html, palette) }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Layer 3 -- catch-all fallback: if none of the error callbacks above
    // ran within the timeout (hung JS, a script that never finishes), fail
    // visibly. Shorter than mermaid's 15s (see ARTIFACT_LOAD_TIMEOUT_MS).
    LaunchedEffect(document) {
        delay(ARTIFACT_LOAD_TIMEOUT_MS)
        if (state == ArtifactRenderState.Loading) state = ArtifactRenderState.Failed
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (state == ArtifactRenderState.Failed) {
            // The real, visible fallback: the raw fence body as a plain
            // code block, exactly like any other fence, plus a caption.
            CodeBlockView(code = html, language = "html")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Couldn't render this.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = extendedColors.inkTertiary,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(start = 4.dp)
            )
        } else {
            // No card chrome by design: no fill, no border -- the artifact
            // floats directly on the chat background instead of reading as
            // a boxed component embedded in the message. No scrollbars
            // ever, a fully transparent WebView background so the app
            // surface shows through seamlessly, and a height that tracks
            // the REAL measured content instead of a fixed-size box.
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
                            // Sandboxing for arbitrary model-authored JS.
                            // All four are real WebSettings methods on this
                            // project's compileSdk 36.1 android.jar
                            // (verified via javap); geolocation is
                            // deliberately left untouched -- the default is
                            // off and setGeolocationEnabled is not called.
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            webViewClient = object : WebViewClient() {
                                // Content sandbox, not a browser: EVERY
                                // navigation attempt after the initial load
                                // (link click, location change, form
                                // submit, window.open) is refused. The
                                // initial loadDataWithBaseURL does not route
                                // through here, so the artifact itself
                                // always loads. The WebResourceRequest
                                // overload is API 24+ == this project's
                                // minSdk, so the deprecated String overload
                                // can never be reached.
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean = true

                                // Layer 2 -- WebView-side failures. Main
                                // frame only: a subresource 404 cannot
                                // blank a working artifact (see class KDoc).
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (!request.isForMainFrame) return
                                    mainHandler.post {
                                        if (state == ArtifactRenderState.Loading) {
                                            state = ArtifactRenderState.Failed
                                        }
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    errorResponse: WebResourceResponse
                                ) {
                                    if (!request.isForMainFrame) return
                                    mainHandler.post {
                                        if (state == ArtifactRenderState.Loading) {
                                            state = ArtifactRenderState.Failed
                                        }
                                    }
                                }
                            }
                            addJavascriptInterface(
                                ArtifactJsBridge(
                                    onRendered = { heightCssPx ->
                                        mainHandler.post {
                                            if (state == ArtifactRenderState.Loading) {
                                                artifactHeight = heightCssPx
                                                    .coerceIn(
                                                        MIN_ARTIFACT_HEIGHT_DP,
                                                        MAX_ARTIFACT_HEIGHT_DP
                                                    )
                                                    .dp
                                                state = ArtifactRenderState.Ready
                                            }
                                        }
                                    },
                                    onError = { _ ->
                                        mainHandler.post {
                                            if (state == ArtifactRenderState.Loading) {
                                                state = ArtifactRenderState.Failed
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
                        // load once per (payload, theme) HTML revision; the
                        // view survives recomposition, so diff via tag --
                        // same pattern as MermaidRenderer.
                        if (webView.tag as? String != document) {
                            webView.tag = document
                            webView.loadDataWithBaseURL(
                                ARTIFACT_BASE_URL,
                                document,
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artifactHeight)
                )

                if (state == ArtifactRenderState.Loading) {
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
                            // Not mermaid's "Rendering diagram..." -- this
                            // lane can be anything the model authored.
                            text = "Loading...",
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
    // Failed flag so Loading -> Ready does NOT tear the view down -- same
    // lifecycle contract as MermaidRenderer.
    DisposableEffect(state == ArtifactRenderState.Failed) {
        onDispose {
            webViewRef.value?.let { webView ->
                webView.stopLoading()
                webView.destroy()
            }
            webViewRef.value = null
        }
    }
}
