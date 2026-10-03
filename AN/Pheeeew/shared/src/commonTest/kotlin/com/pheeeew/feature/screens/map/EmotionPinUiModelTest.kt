package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.emotion.EmotionMapPin
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.feature.screens.map.record.EmotionTypeUiModel
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_emotion_angry
import pheeeew.shared.generated.resources.group_detail_emotion_annoyed
import pheeeew.shared.generated.resources.group_detail_emotion_blocked
import pheeeew.shared.generated.resources.group_detail_emotion_defeated
import pheeeew.shared.generated.resources.group_detail_emotion_tired
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated
import kotlin.test.Test
import kotlin.test.assertEquals

class EmotionPinUiModelTest {
    @Test
    fun `서버 감정 상태를 동일 이름의 UI enum과 기존 아이콘 의미에 매핑한다`() {
        val expected =
            mapOf(
                EmotionState.FRUSTRATED to
                    Triple(
                        EmotionTypeUiModel.FRUSTRATED,
                        Res.string.group_detail_emotion_blocked,
                        Res.drawable.ic_emotion_frustrated,
                    ),
                EmotionState.IRRITATED to
                    Triple(
                        EmotionTypeUiModel.IRRITATED,
                        Res.string.group_detail_emotion_annoyed,
                        Res.drawable.ic_emotion_irritated,
                    ),
                EmotionState.EXHAUSTED to
                    Triple(
                        EmotionTypeUiModel.EXHAUSTED,
                        Res.string.group_detail_emotion_tired,
                        Res.drawable.ic_emotion_exhausted,
                    ),
                EmotionState.DISCOURAGED to
                    Triple(
                        EmotionTypeUiModel.DISCOURAGED,
                        Res.string.group_detail_emotion_defeated,
                        Res.drawable.ic_emotion_discouraged,
                    ),
                EmotionState.ANGRY to
                    Triple(
                        EmotionTypeUiModel.ANGRY,
                        Res.string.group_detail_emotion_angry,
                        Res.drawable.ic_emotion_angry,
                    ),
            )

        expected.forEach { (state, mapping) ->
            val pin =
                EmotionMapPin(
                    id = 1,
                    longitude = 127.0,
                    latitude = 37.0,
                    createdAt = "2026-09-24T12:00:00Z",
                    state = state,
                    rotationDegrees = 0.0,
                    groupStamp = null,
                ).toUiModel()
            assertEquals(mapping.first, pin.emotion)
            assertEquals(mapping.second, pin.emotion.label)
            assertEquals(mapping.third, pin.emotion.icon)
            assertEquals(null, pin.stamp)
        }
    }
}
