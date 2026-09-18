package com.pheeeew.feature.map.sighlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.map.star.MapPinStar
import com.pheeeew.feature.map.star.StarAgeStage
import com.pheeeew.feature.map.star.toComposeStarColor
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_favorite
import pheeeew.shared.generated.resources.ic_favorite_border
import pheeeew.shared.generated.resources.ic_more

@Composable
internal fun SighDetailModal(
    item: SighListItemUiModel,
    onDismiss: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var liked by remember(item.id) { mutableStateOf(item.liked) }
    var likeCount by remember(item.id) { mutableStateOf(item.likeCount) }

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
                        MapPinStar(color = item.starStage.toComposeStarColor(), modifier = Modifier.size(40.dp))
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

                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .clickable(
                                    onClick = {
                                        liked = !liked
                                        likeCount = (likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                                    },
                                ).clip(RoundedCornerShape(20.dp))
                                .background(AppColors.Cream100.copy(alpha = 0.1f))
                                .border(
                                    width = 1.dp,
                                    color = AppColors.Cream100.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp),
                                ).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (liked) Res.drawable.ic_favorite else Res.drawable.ic_favorite_border,
                                ),
                            contentDescription = if (liked) "좋아요 취소" else "좋아요",
                            tint = if (liked) AppColors.Pink100 else AppTheme.colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = likeCount.toString(),
                            fontSize = 11.sp,
                            color = if (liked) AppColors.Pink100 else AppTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun SighDetailModalPreview() {
    SighDetailModal(
        item =
            SighListItemUiModel(
                id = 1L,
                nickname = "노래하는 고라니",
                relativeTime = "12분 전",
                memo = "아 개힘들다링 동동동동",
                starStage = StarAgeStage.Fresh,
                likeCount = 12L,
            ),
        onDismiss = {},
        onMoreClick = {},
    )
}
