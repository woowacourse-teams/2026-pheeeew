package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_close
import pheeeew.shared.generated.resources.group_detail_copy_code
import pheeeew.shared.generated.resources.group_detail_invite_title

@Composable
internal fun InviteCodeDialog(
    code: String,
    isCopying: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .border(BorderStroke(1.5.dp, AppColors.GroupInk), RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.group_detail_invite_title),
                    color = AppColors.GroupInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = code,
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.GroupInk,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                DetailDialogButton(
                    text = stringResource(Res.string.group_detail_copy_code),
                    enabled = !isCopying,
                    isPrimary = true,
                    onClick = onCopy,
                )
                Spacer(Modifier.height(10.dp))
                DetailDialogButton(
                    text = stringResource(Res.string.group_detail_close),
                    enabled = true,
                    isPrimary = false,
                    onClick = onDismiss,
                )
            }
        }
    }
}

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
                ).then(if (isPrimary) Modifier else Modifier.border(1.dp, AppColors.GroupInk, shape))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        color =
            when {
                isPrimary -> Color.White
                else -> AppColors.GroupInk
            },
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}
