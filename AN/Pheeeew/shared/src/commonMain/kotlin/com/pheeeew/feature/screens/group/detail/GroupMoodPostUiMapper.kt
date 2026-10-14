package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.feature.component.emotion.face
import com.pheeeew.feature.component.emotion.label
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.toUiShape
import com.pheeeew.feature.screens.group.detail.model.GroupMoodContentUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodPostUiModel
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal fun Emotion.toGroupMoodPostUiModel() =
    GroupMoodPostUiModel(
        id = id.toString(),
        author = nickname,
        emotionIcon = state.face,
        emotionDescription = state.label,
        timeAgo = toTimeAgo(),
        isMine = isMine,
        content =
            when (contentType) {
                EmotionContentType.MEMO -> GroupMoodContentUiModel.TextContent(requireNotNull(memo))
                EmotionContentType.AUDIO -> GroupMoodContentUiModel.AudioContent(null, audio)
                EmotionContentType.NONE -> error("그룹 감정 목록에는 메모 또는 녹음만 포함됩니다.")
            },
        reactions = reactions,
        stamp =
            groupStamp?.let {
                StampAppearanceUiModel(
                    label = it.text,
                    shape = it.frame.toUiShape(),
                    fillArgb = it.backgroundColor.argb,
                    textArgb = it.textColor.argb,
                )
            },
    )

private fun Emotion.toTimeAgo(): String {
    val elapsed = (Clock.System.now() - createdAt).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed < 1.minutes -> "방금 전"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}분 전"
        elapsed < 1.days -> "${elapsed.inWholeHours}시간 전"
        else -> "${elapsed.inWholeDays}일 전"
    }
}
