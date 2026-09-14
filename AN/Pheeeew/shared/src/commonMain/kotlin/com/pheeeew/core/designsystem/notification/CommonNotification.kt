package com.pheeeew.core.designsystem.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppTheme

private val NotificationBackground = Color(0xFF11121A)
private val NotificationText = Color(0xFFF5F2EA)
private val NotificationDanger = Color(0xFFEC3C3C)

@Composable
fun CommonNotification(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(340.dp).height(172.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = NotificationBackground,
        contentColor = NotificationText,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = NotificationText,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = NotificationText,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        color = NotificationDanger,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun CommonNotificationPreview() {
    AppTheme {
        CommonNotification(
            title = "기기 등록에 실패했어요",
            message = "네트워크 연결을 확인한 뒤 다시 시도해주세요.",
            actionLabel = "다시 시도",
            onAction = {},
        )
    }
}
