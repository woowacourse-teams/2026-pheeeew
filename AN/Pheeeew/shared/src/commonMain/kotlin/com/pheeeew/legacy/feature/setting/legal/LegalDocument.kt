package com.pheeeew.legacy.feature.setting.legal

enum class LegalDocument(
    val title: String,
    internal val path: String,
) {
    OpenSourceLicenses(
        title = "오픈소스 라이선스",
        path = "/2026-pheeeew/open-source-licenses.html",
    ),
    PrivacyPolicy(
        title = "개인정보 처리방침",
        path = "/2026-pheeeew/privacy-policy.html",
    ),
    ;

    internal val url: String
        get() = "${LegalWebConfig.ORIGIN}$path"
}

internal object LegalWebConfig {
    const val ALLOWED_SCHEME = "https"
    const val ALLOWED_HOST = "woowacourse-teams.github.io"
    const val ORIGIN = "$ALLOWED_SCHEME://$ALLOWED_HOST"
}
