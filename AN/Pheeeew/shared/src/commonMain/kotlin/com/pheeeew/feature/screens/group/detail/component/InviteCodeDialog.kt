package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import pheeeew.shared.generated.resources.group_detail_invite_copy_hint
import pheeeew.shared.generated.resources.group_detail_invite_title

@Composable
internal fun InviteCodeDialog(
    code: String,
    isCopying: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copyDescription = stringResource(Res.string.group_detail_copy_code)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.34f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.5.dp, AppColors.GroupInk, RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Text(
                    text = stringResource(Res.string.group_detail_invite_title),
                    color = AppColors.GroupInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.group_detail_invite_copy_hint),
                    color = Color(0xFF747A71),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .semantics {
                                contentDescription = copyDescription
                            }.clickable(enabled = !isCopying, role = Role.Button, onClick = onCopy)
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = code,
                        color = AppColors.GroupInk,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.width(8.dp))
                    CopyCodeIcon()
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(154.dp)
                            .height(1.5.dp)
                            .background(AppColors.GroupInk),
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(Res.string.group_detail_close),
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(role = Role.Button, onClick = onDismiss)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    color = Color(0xFF747A71),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CopyCodeIcon() {
    Canvas(Modifier.width(20.dp).height(20.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val left = size.width * 0.3f
        val top = size.height * 0.12f
        val edge = size.width * 0.58f
        drawRoundRect(
            color = AppColors.GroupInk,
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(left, top),
            size =
                androidx.compose.ui.geometry
                    .Size(edge, edge),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(2.dp.toPx()),
            style = Stroke(strokeWidth),
        )
        drawRoundRect(
            color = AppColors.GroupInk,
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(size.width * 0.1f, size.height * 0.3f),
            size =
                androidx.compose.ui.geometry
                    .Size(edge, edge),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(2.dp.toPx()),
            style = Stroke(strokeWidth),
        )
    }
}
