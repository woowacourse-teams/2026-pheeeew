package com.pheeeew.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_home_error_description
import pheeeew.shared.generated.resources.group_home_error_title
import pheeeew.shared.generated.resources.group_home_refresh_error
import pheeeew.shared.generated.resources.group_home_retry
import pheeeew.shared.generated.resources.ic_emotion_irritated

@Composable
fun LoadErrorContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_emotion_irritated),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.group_home_error_title),
            color = AppColors.RankingContent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(19.dp))
        Text(
            text = stringResource(Res.string.group_home_error_description),
            color = AppColors.RankingSecondaryContent,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(29.dp))
        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier =
                Modifier
                    .width(218.dp)
                    .height(50.dp)
                    .raisedButtonBorder(shape, interactionSource = interactionSource)
                    .clip(shape)
                    .background(Color.White)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onRetry,
                    ).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.group_home_retry),
                color = AppColors.GroupInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = notoSansKrFontFamily(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RefreshErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.RankingSurface)
                .border(AppBorders.Standard, AppColors.GroupInk, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = AppColors.RankingContent,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.group_home_retry),
            color = AppColors.RankingContent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Preview(name = "공통 - 조회 실패", widthDp = 360)
@Composable
private fun LoadErrorContentPreview() {
    AppTheme {
        LoadErrorContent(
            onRetry = {},
            modifier = Modifier.background(AppColors.Background).padding(32.dp),
        )
    }
}

@Preview(name = "공통 - 그룹 새로고침 실패 배너", widthDp = 360)
@Composable
private fun GroupRefreshErrorBannerPreview() {
    AppTheme {
        RefreshErrorBanner(
            message = stringResource(Res.string.group_home_refresh_error),
            onRetry = {},
            modifier = Modifier.background(AppColors.GroupBackground).padding(24.dp),
        )
    }
}

@Preview(name = "공통 - 랭킹 새로고침 실패 배너", widthDp = 360)
@Composable
private fun RankingRefreshErrorBannerPreview() {
    AppTheme {
        RefreshErrorBanner(
            message = "랭킹을 새로고침하지 못했어요.",
            onRetry = {},
            modifier = Modifier.background(AppColors.Background).padding(24.dp),
        )
    }
}
