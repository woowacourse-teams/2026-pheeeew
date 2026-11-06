package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.settings.SettingsTheme
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_nickname_cancel
import pheeeew.shared.generated.resources.settings_nickname_confirm
import pheeeew.shared.generated.resources.settings_nickname_dialog_title

@Composable
internal fun NicknameDialogContent(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onDismiss: (() -> Unit)?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
    confirmEnabled: Boolean = false,
    isLoadingIndicatorVisible: Boolean = false,
    isBusy: Boolean = false,
    inputEnabled: Boolean = true,
) {
    val shape = AppShapes.LargeCard
    Column(
        modifier =
            modifier
                .widthIn(max = 356.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(AppColors.White)
                .padding(horizontal = AppSpacing.xxl.dp, vertical = AppSpacing.xxl.dp),
    ) {
        Text(
            text = title ?: stringResource(Res.string.settings_nickname_dialog_title),
            color = SettingsColors.Ink,
            fontFamily = notoSansKrFontFamily(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(AppSpacing.lg.copy(value = 18.dp).dp))
        NicknameInputField(
            value = nickname,
            onValueChange = onNicknameChange,
            onImeDone = { if (confirmEnabled && !isBusy) onConfirm() },
            enabled = inputEnabled && !isBusy,
            isError = message != null,
        )
        if (message != null) {
            Spacer(Modifier.height(AppSpacing.sm.dp))
            Text(
                text = message,
                color = SettingsColors.Error,
                fontFamily = notoSansKrFontFamily(),
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(AppSpacing.xxxl.copy(value = 30.dp).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.cardGap.dp),
        ) {
            if (onDismiss != null) {
                NicknameDialogButton(
                    text = stringResource(Res.string.settings_nickname_cancel),
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                    onClick = onDismiss,
                )
            }
            NicknameDialogButton(
                text = stringResource(Res.string.settings_nickname_confirm),
                modifier = if (onDismiss == null) Modifier.fillMaxWidth() else Modifier.weight(1f),
                primary = true,
                enabled = confirmEnabled && !isBusy,
                isLoading = isLoadingIndicatorVisible,
                onClick = onConfirm,
            )
        }
    }
}

@Preview
@Composable
private fun NicknameDialogContentPreview() {
    SettingsTheme {
        NicknameDialogContent(
            nickname = "",
            onNicknameChange = {},
            onDismiss = {},
            onConfirm = {},
        )
    }
}
