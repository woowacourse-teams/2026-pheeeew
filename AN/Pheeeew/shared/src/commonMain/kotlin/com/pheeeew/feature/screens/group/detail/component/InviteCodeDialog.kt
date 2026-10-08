package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_close
import pheeeew.shared.generated.resources.group_detail_copy_icon
import pheeeew.shared.generated.resources.group_detail_invite_copy_hint
import pheeeew.shared.generated.resources.group_detail_invite_title

@Composable
internal fun InviteCodeDialog(
    code: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
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
                        .border(AppBorders.Standard, AppColors.GroupInk, RoundedCornerShape(20.dp))
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) }
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
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(role = Role.Button, onClick = onShare),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
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
                        Spacer(Modifier.width(12.dp))
                        Image(
                            painter = painterResource(Res.drawable.group_detail_copy_icon),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .padding(10.dp),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier =
                            Modifier
                                .width(154.dp)
                                .height(1.5.dp)
                                .background(AppColors.GroupInk),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(Res.string.group_detail_close),
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                    color = Color(0xFF747A71),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Preview(name = "초대코드 공유", widthDp = 402, heightDp = 815, showBackground = true)
@Composable
private fun InviteCodeDialogPreview() {
    AppTheme {
        InviteCodeDialog(
            code = "H1Y226",
            onShare = {},
            onDismiss = {},
        )
    }
}
