package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.settings.SettingsTheme
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.settings_nickname_cancel
import pheeeew.shared.generated.resources.settings_nickname_confirm
import pheeeew.shared.generated.resources.settings_nickname_dialog_description
import pheeeew.shared.generated.resources.settings_nickname_dialog_title

@Composable
internal fun NicknameDialogContent(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier =
            modifier
                .widthIn(max = 356.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White)
                .border(AppBorders.Standard, SettingsColors.Ink, shape)
                .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_nickname_dialog_title),
            color = SettingsColors.Ink,
            fontFamily = notoSansKrFontFamily(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.settings_nickname_dialog_description),
            color = SettingsColors.Secondary,
            fontFamily = notoSansKrFontFamily(),
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(18.dp))
        NicknameInputField(
            value = nickname,
            onValueChange = onNicknameChange,
            onImeDone = onConfirm,
        )
        Spacer(Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NicknameDialogButton(
                text = stringResource(Res.string.settings_nickname_cancel),
                modifier = Modifier.weight(1f),
                onClick = onDismiss,
            )
            NicknameDialogButton(
                text = stringResource(Res.string.settings_nickname_confirm),
                modifier = Modifier.weight(1f),
                primary = true,
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
