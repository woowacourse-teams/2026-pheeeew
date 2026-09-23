package com.pheeeew.legacy.feature.setting.legal

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@Composable
internal actual fun LegalWebContent(
    document: LegalDocument,
    reloadToken: Int,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadFailed: (LegalDocumentError) -> Unit,
    onNavigationBlocked: () -> Unit,
    modifier: Modifier,
) {
    val initialUri = remember(document) { trustedInitialUri(document) }
    if (initialUri == null) {
        LaunchedEffect(document) {
            onLoadFailed(LegalDocumentError.InvalidInitialUrl)
        }
        return
    }

    val context = LocalContext.current
    val currentOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
    val currentOnNavigationBlocked by rememberUpdatedState(onNavigationBlocked)
    val webViewState = remember { AndroidLegalWebViewState() }
    val webViewClient =
        remember(document) {
            LegalDocumentWebViewClient(
                document = document,
                onLoadingChanged = { currentOnLoadingChanged(it) },
                onLoadFailed = { currentOnLoadFailed(it) },
                onNavigationBlocked = { currentOnNavigationBlocked() },
            )
        }

    AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                webViewState.webView = this
                webViewState.lastDocument = document
                webViewState.lastReloadToken = reloadToken
                configureLegalDocumentWebView(this)
                this.webViewClient = webViewClient
                loadUrl(initialUri.toString())
            }
        },
        update = { webView ->
            val shouldLoad =
                webViewState.lastDocument != document ||
                    webViewState.lastReloadToken != reloadToken
            if (shouldLoad) {
                webViewState.lastDocument = document
                webViewState.lastReloadToken = reloadToken
                webView.webViewClient = webViewClient
                webView.loadUrl(initialUri.toString())
            }
        },
    )

    DisposableEffect(webViewState) {
        onDispose {
            webViewState.webView?.let { webView ->
                webView.stopLoading()
                webView.webViewClient = WebViewClient()
                webView.webChromeClient = null
                webView.destroy()
            }
            webViewState.webView = null
        }
    }
}

private class AndroidLegalWebViewState {
    var webView: WebView? = null
    var lastDocument: LegalDocument? = null
    var lastReloadToken: Int? = null
}

private class LegalDocumentWebViewClient(
    private val document: LegalDocument,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onLoadFailed: (LegalDocumentError) -> Unit,
    private val onNavigationBlocked: () -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean =
        if (request.url.isAllowedLegalDocumentUrl(document)) {
            false
        } else {
            onNavigationBlocked()
            true
        }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val isPostNavigation = request.method.equals("POST", ignoreCase = true)
        val isNavigationLikeRequest = request.isForMainFrame || isPostNavigation
        if (isNavigationLikeRequest && !request.url.isAllowedLegalDocumentUrl(document)) {
            view.post { onNavigationBlocked() }
            return blockedNavigationResponse()
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(
        view: WebView,
        url: String?,
        favicon: android.graphics.Bitmap?,
    ) {
        if (url?.isAllowedLegalDocumentUrl(document) == true) {
            onLoadingChanged(true)
        } else {
            view.stopLoading()
            onNavigationBlocked()
        }
    }

    override fun onPageFinished(
        view: WebView,
        url: String?,
    ) {
        if (url?.isAllowedLegalDocumentUrl(document) == true) {
            onLoadingChanged(false)
        } else {
            view.stopLoading()
            onNavigationBlocked()
        }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onLoadFailed(
                if (error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE) {
                    LegalDocumentError.Tls
                } else {
                    LegalDocumentError.Network
                },
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame && errorResponse.statusCode >= HTTP_ERROR_STATUS) {
            onLoadFailed(LegalDocumentError.Unknown)
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: android.net.http.SslError,
    ) {
        handler.cancel()
        onLoadFailed(LegalDocumentError.Tls)
    }

    private fun blockedNavigationResponse() =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            HTTP_FORBIDDEN_STATUS,
            "Forbidden",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
}

private fun configureLegalDocumentWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = false
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(false)
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
}

private fun trustedInitialUri(document: LegalDocument): Uri? =
    runCatching { Uri.parse(document.url) }
        .getOrNull()
        ?.takeIf { it.isAllowedLegalDocumentUrl(document) }

private fun String.isAllowedLegalDocumentUrl(document: LegalDocument): Boolean =
    runCatching { Uri.parse(this).isAllowedLegalDocumentUrl(document) }.getOrDefault(false)

private fun Uri.isAllowedLegalDocumentUrl(document: LegalDocument): Boolean {
    val target =
        LegalNavigationTarget(
            scheme = scheme,
            host = host,
            port = port.takeUnless { it == NO_EXPLICIT_PORT },
            hasUserInfo = userInfo != null,
            path = path.orEmpty(),
            query = query,
        )
    return isAllowedLegalNavigation(document, target)
}

private const val NO_EXPLICIT_PORT = -1
private const val HTTP_ERROR_STATUS = 400
private const val HTTP_FORBIDDEN_STATUS = 403
