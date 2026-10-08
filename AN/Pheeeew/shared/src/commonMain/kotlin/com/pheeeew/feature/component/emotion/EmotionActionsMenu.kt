package com.pheeeew.feature.component.emotion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.emotion.EmotionReactionType

@Composable
internal fun EmotionActionsMenu(
    mode: EmotionActionsMenuMode?,
    isMine: Boolean,
    busy: Boolean,
    reactions: List<Pair<EmotionReactionType, Boolean>>,
    onDismiss: () -> Unit,
    onReact: ((EmotionReactionType) -> Unit)?,
    onBlock: (() -> Unit)?,
    onReport: (() -> Unit)?,
) {
    val expandedMode = mode ?: return
    val cardShape = RoundedCornerShape(12.dp)
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(296.dp),
        shape = RoundedCornerShape(0.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(8.dp)) {
            if (expandedMode == EmotionActionsMenuMode.Actions && !isMine) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(5.dp, cardShape)
                        .clip(cardShape)
                        .background(Color.White),
                ) {
                    EmotionMenuAction("차단하기", busy || onBlock == null) { onBlock?.invoke() }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFD9D9D9))
                    EmotionMenuAction("신고하기", busy || onReport == null) { onReport?.invoke() }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(5.dp, cardShape)
                    .clip(cardShape)
                    .background(Color.White)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmotionReactionType.entries.forEach { type ->
                    val selected = reactions.any { it.first == type && it.second }
                    val actionLabel = if (selected) "공감 완료" else "공감"
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = !busy && onReact != null,
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 22.dp),
                            ) {
                                onDismiss()
                                if (!selected) onReact?.invoke(type)
                            }.semantics { contentDescription = "${type.label} $actionLabel" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(type.glyph, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmotionMenuAction(
    label: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(enabled = !disabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFFF1717), fontSize = 16.sp)
        EmotionWarningIcon()
    }
}

@Composable
private fun EmotionWarningIcon() {
    val red = Color(0xFFFF1717)
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val outline =
            Path().apply {
                moveTo(w * 0.3f, h * 0.08f)
                lineTo(w * 0.7f, h * 0.08f)
                lineTo(w * 0.92f, h * 0.3f)
                lineTo(w * 0.92f, h * 0.7f)
                lineTo(w * 0.7f, h * 0.92f)
                lineTo(w * 0.3f, h * 0.92f)
                lineTo(w * 0.08f, h * 0.7f)
                lineTo(w * 0.08f, h * 0.3f)
                close()
            }
        drawPath(outline, red, style = Stroke(width = 1.8.dp.toPx()))
        drawLine(
            red,
            start = Offset(w * 0.5f, h * 0.3f),
            end = Offset(w * 0.5f, h * 0.56f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(red, radius = 1.dp.toPx(), center = Offset(w * 0.5f, h * 0.71f))
    }
}

@Preview(name = "Actions 메뉴", widthDp = 360, showBackground = true)
@Composable
private fun EmotionActionsMenuPreview() {
    AppTheme {
        Box(Modifier.padding(32.dp)) {
            EmotionActionsMenu(
                mode = EmotionActionsMenuMode.Actions,
                isMine = false,
                busy = false,
                reactions = listOf(EmotionReactionType.HEART to true),
                onDismiss = {},
                onReact = {},
                onBlock = {},
                onReport = {},
            )
        }
    }
}

@Preview(name = "내 게시물 메뉴", widthDp = 360, showBackground = true)
@Composable
private fun EmotionActionsMenuMinePreview() {
    AppTheme {
        Box(Modifier.padding(32.dp)) {
            EmotionActionsMenu(
                mode = EmotionActionsMenuMode.Actions,
                isMine = true,
                busy = false,
                reactions = emptyList(),
                onDismiss = {},
                onReact = null,
                onBlock = null,
                onReport = null,
            )
        }
    }
}

@Preview(name = "동작 미연결 / 요청 중 메뉴", widthDp = 360, showBackground = true)
@Composable
private fun EmotionActionsMenuDisabledPreview() {
    AppTheme {
        Box(Modifier.padding(32.dp)) {
            EmotionActionsMenu(
                mode = EmotionActionsMenuMode.Actions,
                isMine = false,
                busy = true,
                reactions = emptyList(),
                onDismiss = {},
                onReact = null,
                onBlock = null,
                onReport = null,
            )
        }
    }
}

@Preview(name = "공감 선택 메뉴", widthDp = 360, showBackground = true)
@Composable
private fun EmotionReactionsMenuPreview() {
    AppTheme {
        Box(Modifier.padding(32.dp)) {
            EmotionActionsMenu(
                mode = EmotionActionsMenuMode.Reactions,
                isMine = false,
                busy = false,
                reactions = emptyList(),
                onDismiss = {},
                onReact = {},
                onBlock = null,
                onReport = null,
            )
        }
    }
}
