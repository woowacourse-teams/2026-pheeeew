package com.pheeeew.feature.screens.map.nearby.mock

import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReaction
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.domain.model.group.Group
import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.repository.emotion.EmotionFailure
import com.pheeeew.domain.repository.emotion.EmotionRepository
import com.pheeeew.domain.repository.emotion.EmotionResult
import com.pheeeew.domain.repository.group.GroupListLoadResult
import com.pheeeew.domain.repository.group.GroupListRepository
import com.pheeeew.feature.screens.map.nearby.NearbyEmotionViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Temporary design data. Revert the factory import/call in App.kt to remove this preview. */
fun createNearbyEmotionMockViewModel(): NearbyEmotionViewModel =
    NearbyEmotionViewModel(
        MockEmotionRepository(),
        GroupListRepository { GroupListLoadResult.Loaded(mockGroups) },
    )

private val mockGroups =
    listOf(
        mockGroup("11111111-1111-4111-8111-111111111111", "히유 모임", "히유", 0xFF279C87, GroupStampFrame.CIRCLE),
        mockGroup("22222222-2222-4222-8222-222222222222", "작은 쉼표", "쉼표", 0xFFFFD875, GroupStampFrame.SCALLOP),
    )

private fun mockGroup(
    id: String,
    name: String,
    label: String,
    color: Long,
    frame: GroupStampFrame,
) = Group(
    id = requireNotNull(GroupId.parse(id)),
    name = name,
    description = "디자인 확인용 그룹",
    inviteCode = "PREVIEW",
    role = GroupRole.MEMBER,
    memberCount = 5,
    stamp =
        GroupStamp(
            label,
            requireNotNull(StampColor.fromArgb(0xFF252826)),
            requireNotNull(StampColor.fromArgb(color)),
            frame,
        ),
)

private class MockEmotionRepository : EmotionRepository {
    private val now = Clock.System.now()
    private val records =
        mutableListOf(
            item(-1, EmotionState.FRUSTRATED, "나른한 구름", true, EmotionContentType.NONE),
            item(-2, EmotionState.EXHAUSTED, "느긋한 고양이", false, EmotionContentType.MEMO, "오늘은 잠깐 쉬어가도 괜찮겠죠?"),
            item(-3, EmotionState.IRRITATED, "나른한 구름", true, EmotionContentType.AUDIO),
            item(-4, EmotionState.DISCOURAGED, "작은 파도", false, EmotionContentType.AUDIO, group = mockGroups[0]),
            item(
                -5,
                EmotionState.ANGRY,
                "나른한 구름",
                true,
                EmotionContentType.MEMO,
                "잘 안 풀리는 날도 있지만,\n오늘 할 일 하나는 끝냈다!",
                mockGroups[1],
            ),
            item(-6, EmotionState.FRUSTRATED, "둥근 바람", false, EmotionContentType.NONE, group = mockGroups[0]),
            item(-7, EmotionState.EXHAUSTED, "나른한 구름", true, EmotionContentType.MEMO, "따뜻한 차 한 잔 마시면서 쉬는 중이에요."),
            item(
                -8,
                EmotionState.IRRITATED,
                "초록 잎새",
                false,
                EmotionContentType.MEMO,
                "계획한 대로 되지 않아서 조금 답답했어요. 그래도 산책하고 나니 마음이 한결 가벼워졌네요. 모두 오늘도 수고했어요!",
                mockGroups[1],
            ),
        )

    private fun item(
        id: Long,
        state: EmotionState,
        nickname: String,
        mine: Boolean,
        contentType: EmotionContentType,
        memo: String? = null,
        group: Group? = null,
    ) = Emotion(
        id = id,
        state = state,
        nickname = nickname,
        createdAt = now - (-id).toInt().minutes,
        isMine = mine,
        contentType = contentType,
        memo = memo,
        reactions =
            EmotionReaction.entries.mapIndexed { index, reaction ->
                ReactionCount(reaction, if (index < 3) ((-id + index) % 5) else 0L, mine && index == 0)
            },
        groupStamp = group?.stamp,
        audio = null, // Display only: no recording is uploaded or remote audio requested.
    )

    override suspend fun firstPage(
        bounds: EmotionBounds,
        groupId: String?,
    ): EmotionResult<EmotionPage> {
        val stamp = mockGroups.find { it.id.value == groupId }?.stamp
        return EmotionResult.Success(EmotionPage(records.filter { groupId == null || it.groupStamp == stamp }, null))
    }

    override suspend fun nextPage(cursor: String) = EmotionResult.Success(EmotionPage(emptyList(), null))

    override suspend fun detail(id: Long): EmotionResult<Emotion> =
        records.find { it.id == id }?.let { EmotionResult.Success(it) }
            ?: EmotionResult.Failure(EmotionFailure.NOT_FOUND)

    override suspend fun react(
        id: Long,
        type: EmotionReaction,
        selected: Boolean,
    ): EmotionResult<Unit> {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return EmotionResult.Failure(EmotionFailure.NOT_FOUND)
        val record = records[index]
        records[index] =
            record.copy(
                reactions =
                    record.reactions.map {
                        if (it.type == type && it.selected != selected) {
                            it.copy(selected = selected, count = (it.count + if (selected) 1 else -1).coerceAtLeast(0))
                        } else {
                            it
                        }
                    },
            )
        return EmotionResult.Success(Unit)
    }

    override suspend fun block(id: Long): EmotionResult<Unit> {
        records.removeAll { it.id == id }
        return EmotionResult.Success(Unit)
    }
}
