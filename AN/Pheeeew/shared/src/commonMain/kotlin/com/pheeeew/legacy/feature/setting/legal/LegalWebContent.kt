package com.pheeeew.legacy.feature.setting.legal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun LegalWebContent(
    document: LegalDocument,
    reloadToken: Int,
    onLoadingChanged: (Boolean) -> Unit,
    onLoadFailed: (LegalDocumentError) -> Unit,
    onNavigationBlocked: () -> Unit,
    modifier: Modifier,
)
