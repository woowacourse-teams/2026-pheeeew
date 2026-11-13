package com.pheeeew.feature.screens.map.record.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.appTextSecondary

@Composable
internal fun RecordInputSupportingText(
    text: String,
    color: Color? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    val resolvedColor = color ?: MaterialTheme.colorScheme.appTextSecondary
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs.copy(value = 2.dp).dp, end = AppSpacing.xs.dp),
        color = resolvedColor,
        fontSize = 12.sp,
        textAlign = textAlign,
        minLines = 1,
    )
}
