package com.pheeeew.feature.screens.report.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.report_prompt_message
import pheeeew.shared.generated.resources.report_prompt_title
import pheeeew.shared.generated.resources.report_stamp_accessibility
import pheeeew.shared.generated.resources.ic_emotion_angry

@Composable
fun ReportStampPrompt(
    emotionStamp: DrawableResource,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(emotionStamp),
            contentDescription = stringResource(Res.string.report_stamp_accessibility),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.report_prompt_title),
                color = AppColors.GroupInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.report_prompt_message),
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Preview(name = "신고 감정 스탬프")
@Composable
private fun ReportStampPromptPreview() {
    ReportStampPrompt(emotionStamp = Res.drawable.ic_emotion_angry)
}
