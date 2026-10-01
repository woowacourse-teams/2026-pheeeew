package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.raisedPressEffect
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme

@Composable
internal fun PressRankingCardSurface(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    onClick: (() -> Unit)? = null,
    restingOffset: androidx.compose.ui.unit.Dp = 0.dp,
    pressedOffset: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickableModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    val shadowOffset = 3.dp.toPx()
                    translate(top = shadowOffset) {
                        val shadowSize = Size(size.width, (size.height - shadowOffset).coerceAtLeast(0f))
                        drawOutline(
                            outline = shape.createOutline(shadowSize, layoutDirection, this),
                            color = AppColors.GroupInk,
                        )
                    }
                }.padding(bottom = 3.dp)
                .raisedPressEffect(
                    interactionSource,
                    enabled = onClick != null,
                    restingOffset = restingOffset,
                    pressedOffset = pressedOffset,
                ).background(color, shape)
                .border(AppBorders.Standard, AppColors.GroupInk, shape)
                .then(clickableModifier),
        content = content,
    )
}

@Preview(name = "프레스 랭킹 카드 표면")
@Composable
private fun PressRankingCardSurfacePreview() {
    AppTheme {
        PressRankingCardSurface(Modifier.padding(20.dp)) {
            Text("프레스 그룹", Modifier.padding(24.dp))
        }
    }
}
