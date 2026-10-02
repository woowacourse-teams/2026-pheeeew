package com.pheeeew.feature.screens.ranking.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.BasicTopBar
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme

internal val RankingTopBarHeight = 56.dp

@Stable
internal class RankingTopBarScrollBehavior {
    var isVisible by mutableStateOf(true)
        private set

    val nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                when {
                    available.y < -1f -> isVisible = false
                    available.y > 1f -> isVisible = true
                }
                return Offset.Zero
            }
        }

    fun show() {
        isVisible = true
    }
}

@Composable
internal fun rememberRankingTopBarScrollBehavior(): RankingTopBarScrollBehavior =
    remember {
        RankingTopBarScrollBehavior()
    }

@Composable
internal fun RankingTopBarOverlay(
    title: String,
    behavior: RankingTopBarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val offsetY by
        animateDpAsState(
            targetValue = if (behavior.isVisible) 0.dp else -RankingTopBarHeight,
            animationSpec = tween(durationMillis = 200),
            label = "rankingTopBarOffset",
        )

    Box(
        modifier = modifier.fillMaxWidth().height(RankingTopBarHeight).clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        BasicTopBar(
            title = title,
            titleColor = AppColors.RankingContent,
            modifier = Modifier.offset(y = offsetY).background(AppColors.Background, RectangleShape),
        )
    }
}

@Preview(name = "스크롤 랭킹 상단바")
@Composable
private fun RankingTopBarPreview() {
    AppTheme {
        RankingTopBarOverlay(
            title = "주간 랭킹",
            behavior = rememberRankingTopBarScrollBehavior(),
        )
    }
}
