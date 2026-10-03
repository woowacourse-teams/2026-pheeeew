package com.pheeeew.feature.screens.map.record.location

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.record_drag_stamp
import pheeeew.shared.generated.resources.record_stamp_drag_guide

@Composable
internal fun RecordStampDragGuide(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(width = 116.dp, height = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.record_stamp_drag_guide),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = stringResource(Res.string.record_drag_stamp),
            modifier = Modifier.padding(bottom = 6.dp),
            color = AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
