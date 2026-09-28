package com.pheeeew.feature.screens.settings.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.BasicTopBar
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily

@Composable
internal fun LegalDocumentScreen(
    document: LegalDocument,
    uiState: LegalDocumentUiState,
    onAction: (LegalDocumentAction) -> Unit,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding(),
    ) {
        BasicTopBar(
            title = document.title,
            onBack = { onAction(LegalDocumentAction.Back) },
            titleColor = AppColors.GroupInk,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            content()

            when (uiState) {
                LegalDocumentUiState.Loading -> {
                    LegalDocumentLoading()
                }

                is LegalDocumentUiState.Error -> {
                    LegalDocumentErrorContent(
                        error = uiState.reason,
                        onRetry = { onAction(LegalDocumentAction.Retry) },
                    )
                }

                is LegalDocumentUiState.Content -> {
                    if (uiState.isBlockedNavigationNoticeVisible) {
                        LegalDocumentBlockedNavigationNotice(
                            onDismiss = {
                                onAction(LegalDocumentAction.DismissBlockedNavigationNotice)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalDocumentLoading(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularLoadingIndicator(color = AppColors.GroupInk)
            Text(
                text = "문서를 불러오는 중입니다.",
                fontFamily = notoSansKrFontFamily(),
                fontSize = 14.sp,
                color = AppColors.GroupInk,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun LegalDocumentErrorContent(
    error: LegalDocumentError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "문서를 불러오지 못했습니다.",
            fontFamily = notoSansKrFontFamily(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.GroupInk,
            textAlign = TextAlign.Center,
        )
        Text(
            text = error.message(),
            fontFamily = notoSansKrFontFamily(),
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "다시 시도",
                fontFamily = notoSansKrFontFamily(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.GroupInk,
            )
        }
    }
}

@Composable
private fun BoxScope.LegalDocumentBlockedNavigationNotice(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        color = AppColors.Surface,
        contentColor = AppColors.GroupInk,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "이 화면에서는 다른 페이지를 열 수 없습니다.",
                fontFamily = notoSansKrFontFamily(),
                fontSize = 14.sp,
                color = AppColors.GroupInk,
                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = "확인",
                    fontFamily = notoSansKrFontFamily(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.GroupInk,
                )
            }
        }
    }
}

private fun LegalDocumentError.message(): String =
    when (this) {
        LegalDocumentError.Network -> "네트워크 연결을 확인한 뒤 다시 시도해 주세요."
        LegalDocumentError.Tls -> "안전한 연결을 확인할 수 없습니다."
        LegalDocumentError.InvalidInitialUrl -> "문서 주소가 올바르지 않습니다."
        LegalDocumentError.Unknown -> "잠시 후 다시 시도해 주세요."
    }

@Preview
@Composable
private fun LegalDocumentLoadingPreview() {
    LegalDocumentScreen(
        document = LegalDocument.PrivacyPolicy,
        uiState = LegalDocumentUiState.Loading,
        onAction = {},
        content = {},
    )
}

@Preview
@Composable
private fun LegalDocumentErrorPreview() {
    LegalDocumentScreen(
        document = LegalDocument.OpenSourceLicenses,
        uiState = LegalDocumentUiState.Error(LegalDocumentError.Network),
        onAction = {},
        content = {},
    )
}
