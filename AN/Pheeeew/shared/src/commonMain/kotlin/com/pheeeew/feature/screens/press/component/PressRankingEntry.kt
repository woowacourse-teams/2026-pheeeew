package com.pheeeew.feature.screens.press.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_view_ranking

@Composable
internal fun PressRankingEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = Color(0xFF202323)
    Row(
        modifier = modifier.height(48.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(20.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val stroke = Stroke(width = 1.8f)
                drawRect(ink, Offset(2f, 10f), Size(7f, 13f), style = stroke)
                drawRect(ink, Offset(9f, 2f), Size(7f, 21f), style = stroke)
                drawRect(ink, Offset(16f, 14f), Size(6f, 9f), style = stroke)
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.press_view_ranking),
            color = ink,
            fontSize = 14.sp,
            fontFamily = notoSansKrFontFamily(),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
            drawLine(
                ink,
                Offset(size.width * 0.2f, size.height * 0.15f),
                Offset(size.width * 0.8f, size.height / 2),
                2.5.dp.toPx(),
                StrokeCap.Square,
            )
            drawLine(
                ink,
                Offset(size.width * 0.8f, size.height / 2),
                Offset(size.width * 0.2f, size.height * 0.85f),
                2.5.dp.toPx(),
                StrokeCap.Square,
            )
        }
    }
}
