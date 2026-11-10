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
import androidx.compose.material3.MaterialTheme
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
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appSurface
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_ranking_person

@Composable
internal fun PressRankingCardSurface(
    modifier: Modifier = Modifier,
    color: Color? = null,
    shape: RoundedCornerShape = AppShapes.LargeCard,
    onClick: (() -> Unit)? = null,
    restingOffset: androidx.compose.ui.unit.Dp = 0.dp,
    pressedOffset: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val surfaceColor = color ?: MaterialTheme.colorScheme.appSurface
    val shadowColor = MaterialTheme.colorScheme.appInk
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
                            color = shadowColor,
                        )
                    }
                }.padding(bottom = AppSpacing.xs.copy(value = 3.dp).dp)
                .raisedPressEffect(
                    interactionSource,
                    enabled = onClick != null,
                    restingOffset = restingOffset,
                    pressedOffset = pressedOffset,
                ).background(surfaceColor, shape)
                .border(AppBorders.Standard, shadowColor, shape)
                .then(clickableModifier),
        content = content,
    )
}

@Preview(name = "프레스 랭킹 카드 표면")
@Composable
private fun PressRankingCardSurfacePreview() {
    AppTheme {
        PressRankingCardSurface(Modifier.padding(AppSpacing.xl.dp)) {
            Text(stringResource(Res.string.press_ranking_person), Modifier.padding(AppSpacing.xxl.dp))
        }
    }
}
