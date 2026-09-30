package com.pheeeew.feature.screens.map.record.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
internal fun RecordInputSupportingText(
    text: String,
    color: Color = AppColors.TextSecondary,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp),
        color = color,
        fontSize = 12.sp,
        textAlign = textAlign,
        minLines = 1,
    )
}
