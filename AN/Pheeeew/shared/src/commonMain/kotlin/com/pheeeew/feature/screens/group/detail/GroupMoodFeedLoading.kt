package com.pheeeew.feature.screens.group.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Composable
internal fun rememberDelayedLoadingVisibility(isLoading: Boolean): Boolean {
    var isVisible by remember { mutableStateOf(false) }
    var visibleSince by remember { mutableStateOf<TimeMark?>(null) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(LOADING_INDICATOR_DELAY_MILLIS)
            visibleSince = TimeSource.Monotonic.markNow()
            isVisible = true
        } else if (isVisible) {
            val elapsed = visibleSince?.elapsedNow() ?: MINIMUM_LOADING_INDICATOR_DURATION
            val remaining = (MINIMUM_LOADING_INDICATOR_DURATION - elapsed).coerceAtLeast(Duration.ZERO)
            val remainingMillis = remaining.inWholeMilliseconds
            if (remainingMillis > 0) delay(remainingMillis)
            isVisible = false
            visibleSince = null
        }
    }

    return isVisible
}

@Composable
internal fun GroupMoodFeedLoadingSpinner(
    modifier: Modifier,
    indicatorSize: Dp,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularLoadingIndicator(Modifier.size(indicatorSize), color = AppColors.GroupInk)
    }
}

@Preview(name = "그룹 감정 목록 · 로딩", widthDp = 360, heightDp = 180, showBackground = true)
@Composable
private fun GroupMoodFeedLoadingPreview() {
    AppTheme {
        GroupMoodFeedLoadingSpinner(Modifier.fillMaxSize(), indicatorSize = 32.dp)
    }
}

private const val LOADING_INDICATOR_DELAY_MILLIS = 150L
private val MINIMUM_LOADING_INDICATOR_DURATION = 300.milliseconds
