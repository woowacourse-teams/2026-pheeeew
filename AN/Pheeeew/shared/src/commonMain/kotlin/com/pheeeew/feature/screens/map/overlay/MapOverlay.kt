package com.pheeeew.feature.screens.map.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.map.record.EmotionBubbleCluster
import com.pheeeew.feature.screens.map.record.EmotionPromptLabel
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_menu
import pheeeew.shared.generated.resources.ic_my_location

private const val EMOTION_PROMPT_DAMPING_RATIO = 0.8205f
private const val EMOTION_PROMPT_STIFFNESS = 380f

@Composable
fun MapOverlay(
    onListClick: () -> Unit,
    onSettingClick: () -> Unit,
    isEmotionSelectorExpanded: Boolean,
    onEmotionSelectorToggle: () -> Unit,
    onEmotionBubbleClick: (EmotionTypeUiModel) -> Unit,
    onMyLocationClick: () -> Unit,
    isRequestingLocation: Boolean,
    isMapError: Boolean,
    modifier: Modifier = Modifier,
) {
    val promptTranslationY by
    animateFloatAsState(
        targetValue = if (isEmotionSelectorExpanded) -125f else 0f,
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
                    .padding(16.dp),
        ) {
            EmotionPromptLabel(
                isExpanded = isEmotionSelectorExpanded,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 190.dp)
                        .graphicsLayer { translationY = promptTranslationY.dp.toPx() },
            )

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
                            .background(AppColors.Surface)
                            .border(width = 1.dp, color = AppColors.Border, shape = RoundedCornerShape(100.dp))
                            .clickable(onClick = onListClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_menu),
                        contentDescription = "목록 열기",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "주변 목록",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                SettingsIconButton(
                    onClick = onSettingClick,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }

            if (!isMapError) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .clip(CircleShape)
                            .shadow(elevation = 4.dp, shape = CircleShape)
                            .background(AppColors.Surface)
                            .clickable(
                                enabled = !isRequestingLocation,
                                onClick = onMyLocationClick,
                            ).padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_my_location),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = Color(0xff2670F8),
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
                            onClick = onEmotionSelectorToggle,
                        ),
            )
        }

        if (!isMapError) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp),
            ) {
                EmotionBubbleCluster(
                    isExpanded = isEmotionSelectorExpanded,
                    onToggle = onEmotionSelectorToggle,
                    onEmotionClick = onEmotionBubbleClick,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
                )
            }
        }
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
                .background(Color(0xFFECEAE5)),
    ) {
        MapOverlay(
            onListClick = {},
            onSettingClick = {},
            isEmotionSelectorExpanded = isEmotionSelectorExpanded,
            onEmotionSelectorToggle = {},
            onEmotionBubbleClick = {},
            onMyLocationClick = {},
            isRequestingLocation = isRequestingLocation,
            isMapError = isMapError,
        )
    }
}
