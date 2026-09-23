package com.pheeeew.legacy.feature.setting.legal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateHasUnknownRoot
import platform.Foundation.NSURLErrorServerCertificateNotYetValid
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebpagePreferences
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun LegalWebContent(
    document: LegalDocument,
    reloadToken: Int,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadFailed: (LegalDocumentError) -> Unit,
    onNavigationBlocked: () -> Unit,
    modifier: Modifier,
) {
    val initialUrl = remember(document) { trustedInitialUrl(document) }
    if (initialUrl == null) {
        LaunchedEffect(document) {
            onLoadFailed(LegalDocumentError.InvalidInitialUrl)
        }
        return
    }

    val currentOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
    val currentOnNavigationBlocked by rememberUpdatedState(onNavigationBlocked)
    val webViewState = remember { IosLegalWebViewState() }
    val navigationDelegate =
        remember(document) {
            LegalDocumentNavigationDelegate(
                document = document,
                onLoadingChanged = { currentOnLoadingChanged(it) },
                onLoadFailed = { currentOnLoadFailed(it) },
                onNavigationBlocked = { currentOnNavigationBlocked() },
            )
        }
    val configuration =
        remember {
            WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                defaultWebpagePreferences =
                    WKWebpagePreferences().apply {
                        allowsContentJavaScript = false
                    }
            }
        }

    UIKitView(
        modifier = modifier,
        factory = {
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = configuration,
            ).apply {
                webViewState.webView = this
                webViewState.lastDocument = document
                webViewState.lastReloadToken = reloadToken
                this.navigationDelegate = navigationDelegate
                loadRequest(NSURLRequest(uRL = initialUrl))
            }
        },
        update = { webView ->
            val shouldLoad =
                webViewState.lastDocument != document ||
                    webViewState.lastReloadToken != reloadToken
            if (shouldLoad) {
                webViewState.lastDocument = document
                webViewState.lastReloadToken = reloadToken
                webView.navigationDelegate = navigationDelegate
                webView.loadRequest(NSURLRequest(uRL = initialUrl))
            }
        },
    )

    DisposableEffect(navigationDelegate, webViewState) {
        onDispose {
            webViewState.webView?.let { webView ->
                webView.stopLoading()
                webView.navigationDelegate = null
            }
            webViewState.webView = null
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosLegalWebViewState {
    var webView: WKWebView? = null
    var lastDocument: LegalDocument? = null
    var lastReloadToken: Int? = null
}

@OptIn(ExperimentalForeignApi::class)
private class LegalDocumentNavigationDelegate(
    private val document: LegalDocument,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onLoadFailed: (LegalDocumentError) -> Unit,
    private val onNavigationBlocked: () -> Unit,
) : NSObject(),
    WKNavigationDelegateProtocol {
    private var policyCancellationPending = false

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val targetFrame = decidePolicyForNavigationAction.targetFrame
        val url = decidePolicyForNavigationAction.request.URL
        val isAllowed =
            targetFrame != null &&
                url?.isAllowedLegalDocumentUrl(document) == true

        if (isAllowed) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            markPolicyNavigationBlocked()
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: WKNavigationResponse,
        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationResponse.response.URL
        val isAllowed =
            decidePolicyForNavigationResponse.canShowMIMEType &&
                url?.isAllowedLegalDocumentUrl(document) == true

        if (isAllowed) {
            decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
        } else {
            decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel)
            markPolicyNavigationBlocked()
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didStartProvisionalNavigation: WKNavigation?,
    ) {
        onLoadingChanged(true)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?,
    ) {
        if (webView.URL?.isAllowedLegalDocumentUrl(document) == true) {
            onLoadingChanged(false)
        } else {
            webView.stopLoading()
            onNavigationBlocked()
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        if (!ignorePolicyCancellation(withError)) {
            onLoadFailed(withError.toLegalDocumentError())
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError,
    ) {
        if (!ignorePolicyCancellation(withError)) {
            onLoadFailed(withError.toLegalDocumentError())
        }
    }

    private fun markPolicyNavigationBlocked() {
        policyCancellationPending = true
        onNavigationBlocked()
    }

    private fun ignorePolicyCancellation(error: NSError): Boolean {
        if (!policyCancellationPending) return false

        policyCancellationPending = false
        return (error.domain == NSURLErrorDomain && error.code == POLICY_CANCELLED_CODE) ||
            (error.domain == WEB_KIT_ERROR_DOMAIN && error.code == FRAME_LOAD_INTERRUPTED_CODE)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun trustedInitialUrl(document: LegalDocument): NSURL? =
    NSURL(string = document.url)
        .takeIf { it.isAllowedLegalDocumentUrl(document) }

@OptIn(ExperimentalForeignApi::class)
private fun NSURL.isAllowedLegalDocumentUrl(document: LegalDocument): Boolean {
    val target =
        LegalNavigationTarget(
            scheme = scheme,
            host = host,
            port = port?.intValue,
            hasUserInfo = user != null || password != null,
            path = path.orEmpty(),
            query = query,
        )
    return isAllowedLegalNavigation(document, target)
}

private fun NSError.toLegalDocumentError(): LegalDocumentError =
    if (domain == NSURLErrorDomain && code in tlsErrorCodes) {
        LegalDocumentError.Tls
    } else {
        LegalDocumentError.Network
    }

private val tlsErrorCodes =
    setOf(
        NSURLErrorSecureConnectionFailed,
        NSURLErrorServerCertificateUntrusted,
        NSURLErrorServerCertificateHasBadDate,
        NSURLErrorServerCertificateHasUnknownRoot,
        NSURLErrorServerCertificateNotYetValid,
    )

private const val POLICY_CANCELLED_CODE = -999L
private const val WEB_KIT_ERROR_DOMAIN = "WebKitErrorDomain"
private const val FRAME_LOAD_INTERRUPTED_CODE = 102L
