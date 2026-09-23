package com.pheeeew.legacy.feature.setting.legal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun LegalDocumentRoute(
    document: LegalDocument,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var uiState by remember(document) {
        mutableStateOf<LegalDocumentUiState>(LegalDocumentUiState.Loading)
    }
    var reloadToken by remember(document) { mutableIntStateOf(0) }

    LegalDocumentScreen(
        document = document,
        uiState = uiState,
        onAction = { action ->
            when (action) {
                LegalDocumentAction.Back -> {
                    onBack()
                }

                LegalDocumentAction.Retry -> {
                    uiState = LegalDocumentUiState.Loading
                    reloadToken += 1
                }

                LegalDocumentAction.DismissBlockedNavigationNotice -> {
                    val contentState = uiState as? LegalDocumentUiState.Content
                    if (contentState != null) {
                        uiState = contentState.copy(isBlockedNavigationNoticeVisible = false)
                    }
                }
            }
        },
        modifier = modifier,
        content = {
            LegalWebContent(
                document = document,
                reloadToken = reloadToken,
                onLoadingChanged = { isLoading ->
                    if (isLoading) {
                        uiState = LegalDocumentUiState.Loading
                    } else if (uiState is LegalDocumentUiState.Loading) {
                        uiState = LegalDocumentUiState.Content()
                    }
                },
                onLoadFailed = { error ->
                    uiState = LegalDocumentUiState.Error(error)
                },
                onNavigationBlocked = {
                    uiState =
                        LegalDocumentUiState.Content(
                            isBlockedNavigationNoticeVisible = true,
                        )
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}
