package com.pheeeew.feature.screens.report.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_arrow_back

@Composable
fun ReportHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "신고",
            color = AppColors.GroupInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Image(
            painter = painterResource(Res.drawable.ic_arrow_back),
            contentDescription = "뒤로",
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onBack)
                    .padding(8.dp),
        )
    }
}

@Preview(name = "신고 헤더")
@Composable
private fun ReportHeaderPreview() {
    ReportHeader(onBack = {})
}
