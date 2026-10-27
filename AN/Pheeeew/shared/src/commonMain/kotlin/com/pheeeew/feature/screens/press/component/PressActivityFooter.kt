package com.pheeeew.feature.screens.press.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.feature.emotion.component.EmotionFeedbackCatalog
import com.pheeeew.feature.emotion.component.formatCount
import com.pheeeew.feature.emotion.component.rememberTapReducedMotion
import com.pheeeew.feature.emotion.model.EmotionKind
import com.pheeeew.feature.screens.press.PressActivityStatus
import com.pheeeew.feature.screens.press.PressActivityUiState
import com.pheeeew.feature.screens.press.PressRaceNotice
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_activity_count
import pheeeew.shared.generated.resources.press_activity_loading
import pheeeew.shared.generated.resources.press_activity_reconnecting
import pheeeew.shared.generated.resources.press_activity_unknown
import pheeeew.shared.generated.resources.press_race_entered
import pheeeew.shared.generated.resources.press_race_entered_compact
import pheeeew.shared.generated.resources.press_race_overtaken
import pheeeew.shared.generated.resources.press_race_overtaken_by
import pheeeew.shared.generated.resources.press_race_passed_by_compact
import pheeeew.shared.generated.resources.press_race_passed_compact

@Composable
internal fun PressActivityFooter(
    state: PressActivityUiState,
    modifier: Modifier = Modifier,
    reducedMotionOverride: Boolean? = null,
) {
    val focused = LocalInspectionMode.current || LocalWindowInfo.current.isWindowFocused
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    var suppressedId by remember { mutableStateOf<Long?>(null) }
    var motionDisabled by remember { mutableStateOf(false) }
    val reduced = reducedMotionOverride ?: rememberTapReducedMotion()
    LaunchedEffect(Unit) {
        motionDisabled = currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f
    }
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(focused, resumed, state.notice?.displayId) {
        if (!focused || !resumed) suppressedId = state.notice?.displayId
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { suppressedId = state.notice?.displayId }
    val notice = state.notice?.takeUnless { it.isEmpty || it.displayId == suppressedId || !focused || !resumed }
    val activityText =
        when (state.status) {
            PressActivityStatus.Known, PressActivityStatus.Cached -> {
                stringResource(
                    Res.string.press_activity_count,
                    formatCount(requireNotNull(state.count)),
                )
            }

            PressActivityStatus.Loading -> {
                stringResource(Res.string.press_activity_loading)
            }

            PressActivityStatus.Unknown -> {
                stringResource(Res.string.press_activity_unknown)
            }

            PressActivityStatus.Reconnecting -> {
                stringResource(Res.string.press_activity_reconnecting)
            }
        }
    val minimumHeight = (with(LocalDensity.current) { 12.sp.toDp() } + 6.dp).coerceAtLeast(20.dp) + 20.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = minimumHeight)
            .background(Color(0xFFF0F1EB))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        val chipWidth = maxWidth * 0.52f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityPeople(
                state,
                activityText,
                Modifier.weight(1f),
                pulseEnabled = focused && resumed && !reduced && !motionDisabled,
            )
            Box(Modifier.widthIn(max = chipWidth)) {
                NoticeTransition(notice, reduced || motionDisabled || !focused || !resumed)
            }
        }
    }
}

@Composable
private fun NoticeTransition(
    notice: PressRaceNotice?,
    reduced: Boolean,
) {
    if (reduced) {
        RaceNoticeChips(notice)
    } else {
        AnimatedContent(targetState = notice, transitionSpec = {
            fadeIn() togetherWith fadeOut()
        }, label = "press-race-notice") { RaceNoticeChips(it) }
    }
}

@Composable
private fun ActivityPeople(
    state: PressActivityUiState,
    activityText: String,
    modifier: Modifier = Modifier,
    pulseEnabled: Boolean,
) {
    val active = state.status == PressActivityStatus.Known && (state.count ?: 0) > 0
    val overflowCount = state.avatarOverflowCount
    Row(
        modifier.clearAndSetSemantics { contentDescription = activityText },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PressActivityLiveIndicator(active, pulseEnabled)
        Row(horizontalArrangement = Arrangement.spacedBy((-5).dp), verticalAlignment = Alignment.CenterVertically) {
            val faces = listOf(EmotionKind.Blocked, EmotionKind.Annoyed, EmotionKind.Tired).take(state.avatarCount)
            faces.forEach { kind ->
                Image(
                    painterResource(EmotionFeedbackCatalog.face(kind)),
                    contentDescription = null,
                    // Same face resource as emotion registration; no extra button/background wrapper.
                    modifier = Modifier.size(20.dp),
                )
            }
            if (overflowCount != null) {
                Box(
                    Modifier
                        .requiredSize(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF202323), CircleShape)
                        .border(1.dp, Color(0xFFF0F1EB), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+${formatCount(overflowCount)}",
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        style =
                            LocalTextStyle.current.copy(
                                fontSize = 9.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                lineHeightStyle =
                                    LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                            ),
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val measurer = rememberTextMeasurer()
            val style = LocalTextStyle.current.copy(fontWeight = FontWeight.ExtraBold)
            val density = LocalDensity.current
            val availableWidth = with(density) { maxWidth.toPx() }
            val fontSize =
                (12 downTo 9).firstOrNull { size ->
                    measurer.measure(activityText, style.copy(fontSize = size.sp), maxLines = 1).size.width <=
                        availableWidth
                } ?: 9
            Text(
                activityText,
                style = style,
                fontSize = fontSize.sp,
                color = Color(0xFF202323),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RaceNoticeChips(notice: PressRaceNotice?) {
    if (notice == null) return
    val full =
        buildList {
            if (notice.enteredRanking) add(stringResource(Res.string.press_race_entered))
            if (notice.overtaken >
                0
            ) {
                add(stringResource(Res.string.press_race_overtaken, formatCount(notice.overtaken)))
            }
            if (notice.overtakenBy >
                0
            ) {
                add(stringResource(Res.string.press_race_overtaken_by, formatCount(notice.overtakenBy)))
            }
        }
    val compact = full.size > 1
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier.clearAndSetSemantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = full.joinToString(", ")
            },
    ) {
        val chipModifier = if (compact) Modifier.weight(1f, fill = false) else Modifier
        if (notice.enteredRanking) {
            RaceChip(
                stringResource(if (compact) Res.string.press_race_entered_compact else Res.string.press_race_entered),
                false,
                chipModifier,
            )
        }
        if (notice.overtaken > 0) {
            RaceChip(
                stringResource(
                    if (compact) Res.string.press_race_passed_compact else Res.string.press_race_overtaken,
                    formatCount(notice.overtaken),
                ),
                false,
                chipModifier,
            )
        }
        if (notice.overtakenBy > 0) {
            RaceChip(
                stringResource(
                    if (compact) Res.string.press_race_passed_by_compact else Res.string.press_race_overtaken_by,
                    formatCount(notice.overtakenBy),
                ),
                true,
                chipModifier,
            )
        }
    }
}

@Composable
private fun RaceChip(
    text: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (dark) Color.White else Color(0xFF202323),
        modifier =
            modifier
                .background(if (dark) Color(0xFF202323) else Color(0xFFFFE466), CircleShape)
                .border(1.dp, Color(0xFF202323), CircleShape)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
