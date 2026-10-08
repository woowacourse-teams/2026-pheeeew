package com.pheeeew.feature.screens.group.detail

import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.detail.model.GroupDetailReadyUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodContentUiModel
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedLoadState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodFeedUiState
import com.pheeeew.feature.screens.group.detail.model.GroupMoodPostUiModel
import com.pheeeew.feature.screens.group.preview.HomeFixtures
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted

internal fun previewGroupDetailReadyUiModel(role: GroupRole = GroupRole.MEMBER): GroupDetailReadyUiModel {
    val baseStamp = HomeFixtures.groups.first().stamp
    return GroupDetailReadyUiModel(
        name = "우테코 8기 히유",
        role = role,
        description = "그룹 설명 문구를 여기에 넣을겁니다....\n그룹설명설명설명입니다다다다다이",
        memberCount = 14,
        stamp = baseStamp.copy(label = "히유", shape = StampShapeId.ROUNDED_RECTANGLE, fillArgb = 0xFFF4FF9FL),
        weeklyStampCount = 42,
        weeklyStampRank = 3,
        weeklyEmotionPressCount = 318,
        weeklyEmotionPressRank = 5,
        feed =
            GroupMoodFeedUiState.Available(
                posts = previewGroupMoodPosts(),
                hasMore = false,
                loadState = GroupMoodFeedLoadState.Idle,
            ),
    )
}

internal fun previewGroupMoodPosts() =
    listOf(
        GroupMoodPostUiModel(
            id = "preview-1",
            author = "잠깐 쉬는 구름",
            emotionIcon = Res.drawable.ic_emotion_discouraged,
            emotionDescription = "지침",
            timeAgo = "10분 전",
            content = GroupMoodContentUiModel.TextContent("오늘 회의가 길었지만, 집에 가는 길\n씨발.... - 허닛-"),
            stamp = null,
            reactions = listOf(ReactionCount(EmotionReactionType.HEART, 2, selected = false)),
        ),
        GroupMoodPostUiModel(
            id = "preview-2",
            author = "퇴근만 기다려",
            emotionIcon = Res.drawable.ic_emotion_angry,
            emotionDescription = "화남",
            timeAgo = "35분 전",
            content = GroupMoodContentUiModel.TextContent("일단 하나씩 해보려고요."),
            stamp = null,
        ),
        GroupMoodPostUiModel(
            id = "preview-3",
            author = "조용한 한숨",
            emotionIcon = Res.drawable.ic_emotion_exhausted,
            emotionDescription = "지침",
            timeAgo = "1시간 전",
            content = GroupMoodContentUiModel.AudioContent("0:12", audio = null),
            stamp = null,
        ),
        GroupMoodPostUiModel(
            id = "preview-4",
            author = "나 집에 갈래",
            emotionIcon = Res.drawable.ic_emotion_angry,
            emotionDescription = "화남",
            timeAgo = "2시간 전",
            isMine = true,
            content = GroupMoodContentUiModel.TextContent("가보자가보자가보자구"),
            stamp = null,
        ),
    )
