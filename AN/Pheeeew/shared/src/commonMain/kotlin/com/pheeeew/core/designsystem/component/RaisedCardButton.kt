package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RaisedCardButton(
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnClick by rememberUpdatedState(onClick)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier =
            modifier
                .drawBehind {
                    val shadowOffset = 3.dp.toPx()
                    drawRoundRect(
                        color = AppColors.GroupInk,
                        topLeft = Offset(0f, shadowOffset),
                        size = Size(size.width, (size.height - shadowOffset).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }.padding(bottom = 3.dp)
                .raisedPressEffect(
                    interactionSource = interactionSource,
                    pressedOffset = 3.dp,
                    pressDurationMillis = 60,
                    releaseDurationMillis = 120,
                ).background(backgroundColor, shape)
                .border(AppBorders.Standard, AppColors.GroupInk, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        coroutineScope.launch {
                            delay(120L)
                            if (currentEnabled) currentOnClick()
                        }
                    },
                ),
        content = content,
    )
}

@Preview(name = "공통 눌림 카드 버튼")
@Composable
private fun RaisedCardButtonPreview() {
    AppTheme {
        RaisedCardButton(
            backgroundColor = AppColors.Primary,
            onClick = {},
            modifier = Modifier.padding(16.dp),
        ) {
            Text("버튼 내용", Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        }
    }
}
