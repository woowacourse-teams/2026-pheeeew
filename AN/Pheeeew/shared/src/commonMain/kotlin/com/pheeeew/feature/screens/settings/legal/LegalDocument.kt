package com.pheeeew.feature.screens.settings.legal

import org.jetbrains.compose.resources.StringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_legal_open_source
import pheeeew.shared.generated.resources.settings_legal_privacy

enum class LegalDocument(
    val title: StringResource,
    internal val path: String,
) {
    OpenSourceLicenses(
        title = Res.string.settings_legal_open_source,
        path = "/2026-pheeeew/open-source-licenses.html",
    ),
    PrivacyPolicy(
        title = Res.string.settings_legal_privacy,
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
