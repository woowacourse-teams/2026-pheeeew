package com.pheeeew.feature.screens.report.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors

private val selectedReasonColor = Color(0xFFFFF8DA)

@Composable
fun ReportReasonOption(
    reason: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(modifier = modifier.fillMaxWidth().padding(end = 3.dp, bottom = 3.dp)) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .background(AppColors.GroupInk, shape),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(if (selected) selectedReasonColor else AppColors.Background)
                    .border(2.dp, AppColors.GroupInk, shape)
                    .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
                    .semantics { this.selected = selected }
                    .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportRadioButton(selected = selected)
            Spacer(Modifier.width(12.dp))
            Text(
                text = reason,
                color = AppColors.GroupInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Preview(name = "선택된 신고 사유")
@Composable
private fun ReportReasonOptionSelectedPreview() {
    ReportReasonOption(
        reason = "명예훼손 및 허위사실 유포",
        selected = true,
        enabled = true,
        onClick = {},
    )
}

@Preview(name = "선택되지 않은 신고 사유")
@Composable
private fun ReportReasonOptionUnselectedPreview() {
    ReportReasonOption(
        reason = "사이버 괴롭힘",
        selected = false,
        enabled = true,
        onClick = {},
    )
}
