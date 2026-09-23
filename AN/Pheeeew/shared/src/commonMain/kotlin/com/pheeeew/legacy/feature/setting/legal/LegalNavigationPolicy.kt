package com.pheeeew.legacy.feature.setting.legal

internal data class LegalNavigationTarget(
    val scheme: String?,
    val host: String?,
    val port: Int?,
    val hasUserInfo: Boolean,
    val path: String,
    val query: String?,
)

internal fun isAllowedLegalNavigation(
    document: LegalDocument,
    target: LegalNavigationTarget,
): Boolean =
    target.scheme?.equals(LegalWebConfig.ALLOWED_SCHEME, ignoreCase = true) == true &&
        target.host?.equals(LegalWebConfig.ALLOWED_HOST, ignoreCase = true) == true &&
        (target.port == null || target.port == HTTPS_DEFAULT_PORT) &&
        !target.hasUserInfo &&
        target.path == document.path &&
        target.query == null

private const val HTTPS_DEFAULT_PORT = 443
