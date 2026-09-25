package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
internal fun DetailDialogButton(
    text: String,
    enabled: Boolean,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Text(
        text = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(shape)
                .background(
                    when {
                        isPrimary && enabled -> AppColors.GroupInk
                        isPrimary -> Color(0xFF858A89)
                        else -> Color.White
                    },
                ).then(if (isPrimary) Modifier else Modifier.border(BorderStroke(1.dp, AppColors.GroupInk), shape))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        color = if (isPrimary) Color.White else AppColors.GroupInk,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}
