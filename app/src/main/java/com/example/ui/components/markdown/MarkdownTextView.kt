package com.example.ui.components.markdown

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.FestoTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

private var cachedMarkwon: Markwon? = null

fun getOrCreateMarkwon(context: Context): Markwon {
    cachedMarkwon?.let { return it }
    val markwon = Markwon.builder(context)
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(JLatexMathPlugin.create(40f) { builder ->
            builder.inlinesEnabled(true)
        })
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .build()
    cachedMarkwon = markwon
    return markwon
}

@Composable
fun MarkdownTextView(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markwon = remember { getOrCreateMarkwon(context) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = FestoTheme.colors.brandNova.toArgb()
    val containsTable = remember(markdown) { markdown.contains("|") }
    val scrollState = rememberScrollState()

    Box(
        modifier = if (containsTable) {
            modifier.fillMaxWidth().horizontalScroll(scrollState)
        } else {
            modifier.fillMaxWidth()
        }
    ) {
        AndroidView(
            factory = { ctx ->
                TextView(ctx).apply {
                    movementMethod = LinkMovementMethod.getInstance()
                    setTextColor(textColor)
                    setLinkTextColor(linkColor)
                    textSize = 14.5f
                    setLineSpacing(0f, 1.25f)
                    markwon.setMarkdown(this, markdown)
                }
            },
            update = { textView ->
                textView.setTextColor(textColor)
                textView.setLinkTextColor(linkColor)
                markwon.setMarkdown(textView, markdown)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
