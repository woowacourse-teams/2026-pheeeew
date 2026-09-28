package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.detail.GroupDetailNoticeKind

@Composable
internal fun GroupDetailNoticeSnackbar(
    message: String,
    kind: GroupDetailNoticeKind,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                .background(Color.White)
                .border(width = 1.dp, color = AppColors.GroupInk.copy(alpha = 0.76f), shape = shape)
                .clickable(onClick = onDismiss)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        NoticeMark(isSuccess = kind == GroupDetailNoticeKind.CopySucceeded)
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            color = AppColors.GroupInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NoticeMark(isSuccess: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        drawCircle(color = AppColors.Primary)
        drawCircle(
            color = AppColors.GroupInk,
            style = Stroke(width = 1.dp.toPx()),
        )
        val mark =
            Path().apply {
                if (isSuccess) {
                    moveTo(size.width * 0.25f, size.height * 0.51f)
                    lineTo(size.width * 0.43f, size.height * 0.68f)
                    lineTo(size.width * 0.75f, size.height * 0.34f)
                } else {
                    moveTo(size.width * 0.5f, size.height * 0.28f)
                    lineTo(size.width * 0.5f, size.height * 0.55f)
                    moveTo(size.width * 0.5f, size.height * 0.72f)
                    lineTo(size.width * 0.5f, size.height * 0.73f)
                }
            }
        drawPath(
            path = mark,
            color = AppColors.GroupInk,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
