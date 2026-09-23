package com.pheeeew.legacy.feature.setting.legal

internal sealed interface LegalDocumentUiState {
    data object Loading : LegalDocumentUiState

    data class Content(
        val isBlockedNavigationNoticeVisible: Boolean = false,
    ) : LegalDocumentUiState

    data class Error(
        val reason: LegalDocumentError,
    ) : LegalDocumentUiState
}

internal enum class LegalDocumentError {
    Network,
    Tls,
    InvalidInitialUrl,
    Unknown,
}

internal sealed interface LegalDocumentAction {
    data object Back : LegalDocumentAction

    data object Retry : LegalDocumentAction

    data object DismissBlockedNavigationNotice : LegalDocumentAction
}
