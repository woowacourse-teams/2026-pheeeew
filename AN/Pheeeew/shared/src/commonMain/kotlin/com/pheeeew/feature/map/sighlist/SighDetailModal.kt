package com.pheeeew.feature.map.sighlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_more

@Composable
internal fun SighDetailModal(
    item: SighListItemUiModel,
    onDismiss: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(AppColors.Black900.copy(alpha = 0.58f))
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        )
        Surface(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(24.dp),
            color = AppColors.Navy700,
            contentColor = AppColors.Cream100,
            border = BorderStroke(1.dp, AppColors.Cream100.copy(alpha = 0.1f)),
            shadowElevation = 18.dp,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                        .padding(vertical = 20.dp, horizontal = 20.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 48.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SighStar(color = item.starColor, modifier = Modifier.size(40.dp))
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.nickname,
                                style = AppTheme.typography.menuItem.copy(fontWeight = FontWeight.Normal),
                            )
                            Text(
                                text = item.relativeTime,
                                style = AppTheme.typography.caption,
                                color = AppTheme.colors.onSurfaceVariant,
                            )
                        }
                        Icon(
                            painter = painterResource(Res.drawable.ic_more),
                            contentDescription = "더보기",
                            tint = AppTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).align(Alignment.Top).clickable(onClick = onMoreClick),
                        )
                    }
                    Text(
                        text = item.memo,
                        style = AppTheme.typography.menuItem,
                        modifier = Modifier.padding(top = 16.dp, end = 8.dp),
                    )
                }
                Text(
                    text = "닫기",
                    style = AppTheme.typography.menuItem,
                    color = AppColors.Cream100,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clickable(onClick = onDismiss),
                )
            }
        }
    }
}
