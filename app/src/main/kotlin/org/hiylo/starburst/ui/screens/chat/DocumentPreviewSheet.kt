/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : DocumentPreviewSheet.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.logging.AppLogger as Log

private const val PREVIEW_TAG = "DocumentPreviewSheet"

/**
 * 组装后端文档预览页地址：`{backendUrl}/doc/preview.html?src=<urlencoded 文件URL>`。
 * 后端 webui 带 X-Frame-Options: DENY，不能 iframe，必须整页打开。
 */
internal fun documentPreviewUrl(backendUrl: String, fileUrl: String): String =
    "${backendUrl.trimEnd('/')}/doc/preview.html?src=${Uri.encode(fileUrl)}"

/**
 * 文档预览弹层：ModalBottomSheet 内嵌一个 WebView 整页加载后端的
 * `doc/preview.html`，用于预览 [GeneratedDocument] 生成的 xlsx/docx/pptx。
 *
 * 加载失败会在弹层内展示错误占位，不把错误吞掉成空白页。
 *
 * @param backendUrl 后端地址（如 http://192.0.2.150:18090）
 * @param fileUrl 待预览文件的完整下载地址（如 http://192.0.2.150:18090/api/documents/5/download）
 * @param onDismiss 关闭回调
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentPreviewSheet(
    backendUrl: String,
    fileUrl: String,
    onDismiss: () -> Unit,
) {
    val previewUrl = remember(backendUrl, fileUrl) { documentPreviewUrl(backendUrl, fileUrl) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                    )
                }
                Text(
                    text = "文档预览",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            when {
                loadError != null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = loadError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            @SuppressLint("SetJavaScriptEnabled")
                            val wv = WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    allowContentAccess = true
                                    allowFileAccess = false
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?,
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            isLoading = false
                                            loadError = "预览页加载失败（${error?.errorCode ?: "网络错误"}）"
                                        }
                                    }

                                    // 站外链接交给系统浏览器，避免在预览 WebView 内加载不可信页面。
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val requestUrl = request?.url?.toString() ?: return false
                                        if (requestUrl.startsWith(backendUrl)) return false
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl)))
                                        }
                                        return true
                                    }
                                }
                                loadUrl(previewUrl)
                            }
                            Log.d(PREVIEW_TAG, "Preview WebView loaded: $previewUrl")
                            webView = wv
                            wv
                        },
                        update = { webView ->
                            if (webView.url != previewUrl) {
                                webView.loadUrl(previewUrl)
                            }
                        },
                    )
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}