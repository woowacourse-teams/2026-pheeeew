package com.pheeeew.feature.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.screens.settings.SettingsTheme

@Composable
internal fun NicknameDialogButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (primary) AppColors.Primary else Color.Transparent)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = SettingsColors.Ink,
            fontFamily = notoSansKrFontFamily(),
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun NicknameDialogButtonPreview() {
    SettingsTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NicknameDialogButton(text = "취소", onClick = {})
            NicknameDialogButton(text = "확인", primary = true, onClick = {})
        }
    }
}
