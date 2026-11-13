package com.pheeeew.feature.screens.map.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.appBorder
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.appSurface
import com.pheeeew.core.designsystem.theme.appTextPrimary
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import com.pheeeew.feature.guide.reportFeatureGuideAnchor
import com.pheeeew.feature.screens.map.record.EmotionBubbleCluster
import com.pheeeew.feature.screens.map.record.EmotionPromptLabel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_map_filter
import pheeeew.shared.generated.resources.ic_map_filter_active
import pheeeew.shared.generated.resources.ic_menu
import pheeeew.shared.generated.resources.ic_my_location
import pheeeew.shared.generated.resources.ic_refresh
import pheeeew.shared.generated.resources.map_current_location
import pheeeew.shared.generated.resources.map_filter
import pheeeew.shared.generated.resources.map_filter_applied
import pheeeew.shared.generated.resources.map_filter_default
import pheeeew.shared.generated.resources.map_nearby_emotions
import pheeeew.shared.generated.resources.map_open_list
import pheeeew.shared.generated.resources.map_refresh

private const val EMOTION_PROMPT_DAMPING_RATIO = 0.8205f
private const val EMOTION_PROMPT_STIFFNESS = 380f
private val MAP_CONTROL_BOTTOM_SPACE = AppBottomNavigationBarOverlaySpace + 24.dp
private val MAP_BOTTOM_BAR_LIFT = MAP_CONTROL_BOTTOM_SPACE - 56.dp

@Composable
fun MapOverlay(
    onListClick: () -> Unit,
    onSettingClick: () -> Unit,
    onFilterClick: () -> Unit,
    isEmotionSelectorExpanded: Boolean,
    onEmotionSelectorToggle: () -> Unit,
    onEmotionBubbleClick: (EmotionTypeUiModel) -> Unit,
    onRefreshClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    isRequestingLocation: Boolean,
    isMapError: Boolean,
    modifier: Modifier = Modifier,
    showNavigationButtons: Boolean = true,
    nearbyMapControlHeight: Dp = 0.dp,
    isMapCameraMoving: Boolean = false,
    isFilterApplied: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val promptVisibility = remember(scope) { MapPromptVisibilityState(scope) }
    LaunchedEffect(isMapCameraMoving) {
        promptVisibility.onCameraMovingChanged(isMapCameraMoving)
    }
    LaunchedEffect(isEmotionSelectorExpanded) {
        if (isEmotionSelectorExpanded) promptVisibility.reset()
    }
    val toggleEmotionSelector = {
        promptVisibility.reset()
        onEmotionSelectorToggle()
    }
    val promptBottomPadding = 190.dp + MAP_BOTTOM_BAR_LIFT
    // The 112.dp bubble starts at 140.dp inside its 260.dp cluster, leaving 8.dp below it.
    val locationBottomPadding = MAP_CONTROL_BOTTOM_SPACE + 8.dp
    val promptTranslationY by
        animateFloatAsState(
            targetValue = if (isEmotionSelectorExpanded) -90f else 0f,
            animationSpec =
                spring(
                    dampingRatio = EMOTION_PROMPT_DAMPING_RATIO,
                    stiffness = EMOTION_PROMPT_STIFFNESS,
                    visibilityThreshold = 0.001f,
                ),
            label = "emotionPromptTranslationY",
        )

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(AppSpacing.lg.dp),
        ) {
            AnimatedVisibility(
                visible = isEmotionSelectorExpanded || (!isMapCameraMoving && !promptVisibility.isHidden),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(250)),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = promptBottomPadding)
                        .graphicsLayer { translationY = promptTranslationY.dp.toPx() },
                label = "emotionPromptVisibility",
            ) {
                EmotionPromptLabel(
                    isExpanded = isEmotionSelectorExpanded,
                )
            }

            if (!isMapError && !isEmotionSelectorExpanded) {
                MapControlButton(
                    icon = Res.drawable.ic_my_location,
                    contentDescription = stringResource(Res.string.map_current_location),
                    enabled = !isRequestingLocation,
                    onClick = onMyLocationClick,
                    modifier =
                        Modifier.align(Alignment.BottomEnd).padding(
                            bottom =
                                maxOf(
                                    locationBottomPadding,
                                    nearbyMapControlHeight,
                                ),
                        ),
                )
            }

            if (showNavigationButtons) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(MaterialTheme.colorScheme.appSurface)
                                .border(
                                    width = AppBorders.Standard,
                                    color = MaterialTheme.colorScheme.appBorder,
                                    shape = RoundedCornerShape(100.dp),
                                ).clickable(onClick = onListClick)
                                .reportFeatureGuideAnchor("map", 1)
                                .padding(
                                    horizontal = AppSpacing.xxl.dp,
                                    vertical = AppSpacing.sm.copy(value = 10.dp).dp,
                                ).align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_menu),
                            contentDescription = stringResource(Res.string.map_open_list),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.md.dp))
                        Text(
                            text = stringResource(Res.string.map_nearby_emotions),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    SettingsIconButton(
                        onClick = onSettingClick,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }

            if (!isMapError && !isEmotionSelectorExpanded) {
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = AppSpacing.xxxl.copy(value = 60.dp).dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.cardGap.dp),
                ) {
                    val filterStateDescription =
                        stringResource(
                            if (isFilterApplied) Res.string.map_filter_applied else Res.string.map_filter_default,
                        )
                    MapControlButton(
                        icon = if (isFilterApplied) Res.drawable.ic_map_filter_active else Res.drawable.ic_map_filter,
                        contentDescription = stringResource(Res.string.map_filter),
                        modifier =
                            Modifier
                                .semantics { stateDescription = filterStateDescription }
                                .reportFeatureGuideAnchor("map", 2),
                        onClick = onFilterClick,
                        tint = Color.Unspecified,
                    )
                    MapControlButton(
                        icon = Res.drawable.ic_refresh,
                        contentDescription = stringResource(Res.string.map_refresh),
                        onClick = onRefreshClick,
                        tint = MaterialTheme.colorScheme.appTextPrimary,
                    )
                }
            }
        }

        if (isEmotionSelectorExpanded && !isMapError) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = "감정 선택 닫기",
                            onClick = toggleEmotionSelector,
                        ),
            )
        }

        if (!isMapError) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(AppSpacing.lg.dp),
            ) {
                EmotionBubbleCluster(
                    isExpanded = isEmotionSelectorExpanded,
                    onToggle = toggleEmotionSelector,
                    onEmotionClick = onEmotionBubbleClick,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = MAP_CONTROL_BOTTOM_SPACE)
                            .reportFeatureGuideAnchor("map", 0),
                )
            }
        }
    }
}

@Composable
internal fun MapControlButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val resolvedTint = tint ?: MaterialTheme.appColors.map.controlTint
    Box(
        modifier =
            modifier
                .size(44.dp)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.appSurface)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = resolvedTint,
        )
    }
}

@Preview(name = "접힌 지도 오버레이", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
fun MapOverlayPreview() {
    MapOverlayPreviewContent(isEmotionSelectorExpanded = false, isRequestingLocation = false, isMapError = false)
}

@Preview(name = "펼친 지도 오버레이", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
fun MapOverlayExpandedPreview() {
    MapOverlayPreviewContent(isEmotionSelectorExpanded = true, isRequestingLocation = false, isMapError = false)
}

@Preview(name = "위치 요청 중 오버레이", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
fun MapOverlayRequestingLocationPreview() {
    MapOverlayPreviewContent(isEmotionSelectorExpanded = false, isRequestingLocation = true, isMapError = false)
}

@Preview(name = "지도 오류 오버레이", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
fun MapOverlayErrorPreview() {
    MapOverlayPreviewContent(isEmotionSelectorExpanded = false, isRequestingLocation = false, isMapError = true)
}

@Composable
private fun MapOverlayPreviewContent(
    isEmotionSelectorExpanded: Boolean,
    isRequestingLocation: Boolean,
    isMapError: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.appColors.map.background),
    ) {
        MapOverlay(
            onListClick = {},
            onSettingClick = {},
            onFilterClick = {},
            isEmotionSelectorExpanded = isEmotionSelectorExpanded,
            onEmotionSelectorToggle = {},
            onEmotionBubbleClick = {},
            onRefreshClick = {},
            onMyLocationClick = {},
            isRequestingLocation = isRequestingLocation,
            isMapError = isMapError,
        )
    }
}
