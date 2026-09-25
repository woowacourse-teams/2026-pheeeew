package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(0.dp))
                .background(AppColors.GroupInk)
                .clickable(onClick = onDismiss)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (kind == GroupDetailNoticeKind.CopySucceeded) {
            CopySuccessMark()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = message,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CopySuccessMark() {
    Canvas(Modifier.width(20.dp).height(20.dp)) {
        drawCircle(color = Color(0xFFA5EAD6))
        val check =
            Path().apply {
                moveTo(size.width * 0.25f, size.height * 0.52f)
                lineTo(size.width * 0.43f, size.height * 0.69f)
                lineTo(size.width * 0.76f, size.height * 0.34f)
            }
        drawPath(
            path = check,
            color = AppColors.GroupInk,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
