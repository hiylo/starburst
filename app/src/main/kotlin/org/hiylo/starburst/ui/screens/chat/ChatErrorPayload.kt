/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatErrorPayload.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.hiylo.starburst.domain.model.*
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.isAmoledTheme
import android.annotation.SuppressLint

private enum class HtmlErrorViewMode {
    Page,
    Code,
}

@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun ErrorPayloadContent(
    text: String,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!looksLikeHtmlPayload(text)) {
        SelectionContainer {
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                modifier = modifier,
            )
        }
        return
    }

    var mode by rememberSaveable(text) { mutableStateOf(HtmlErrorViewMode.Code) }
    val htmlForPreview = remember(text) { normalizeHtmlForEmbeddedPreview(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == HtmlErrorViewMode.Code,
                onClick = { mode = HtmlErrorViewMode.Code },
                label = { Text(stringResource(R.string.chat_error_view_code)) },
            )
            FilterChip(
                selected = mode == HtmlErrorViewMode.Page,
                onClick = { mode = HtmlErrorViewMode.Page },
                label = { Text(stringResource(R.string.chat_error_view_page)) },
            )
        }

        if (mode == HtmlErrorViewMode.Page) {
            val isAmoled = isAmoledTheme()
            val bgColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        settings.blockNetworkImage = true
                        settings.setSupportMultipleWindows(false)
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.textZoom = 85
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        webViewClient = WebViewClient()
                        setOnTouchListener { v, event ->
                            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            false
                        }
                        setBackgroundColor(bgColor.toArgb())
                    }
                },
                update = { webView ->
                    if (webView.tag != htmlForPreview) {
                        webView.tag = htmlForPreview
                        webView.loadDataWithBaseURL(
                            "https://localhost/",
                            htmlForPreview,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 360.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            SelectionContainer {
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                )
            }
        }
    }
}
