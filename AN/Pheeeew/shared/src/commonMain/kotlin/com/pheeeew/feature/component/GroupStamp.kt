package com.pheeeew.feature.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
fun GroupStamp(
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .border(2.dp, AppColors.RankingContent, CircleShape)
                .padding(4.dp)
                .background(AppColors.RankingAccent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = AppColors.RankingContent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview
@Composable
private fun GroupStampPreview() {
    GroupStamp("히유", 62.dp, Modifier.padding(16.dp))
}
