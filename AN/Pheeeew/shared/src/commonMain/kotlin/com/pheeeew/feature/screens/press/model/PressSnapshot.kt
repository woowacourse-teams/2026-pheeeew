package com.pheeeew.feature.screens.press.model

import com.pheeeew.feature.emotion.model.EmotionCountUiModel
import com.pheeeew.feature.emotion.model.EmotionKind

internal data class PressPeriodSnapshot(
    val emotionCounts: List<EmotionCountUiModel>,
    val myEmotionCounts: List<EmotionCountUiModel>,
    val myTotalCount: Long,
    val totalCount: Long,
) {
    init {
        require(emotionCounts.map { it.kind } == EmotionKind.entries) { "전체 감정 수는 다섯 감정 순서로 제공해야 합니다." }
        require(myEmotionCounts.map { it.kind } == EmotionKind.entries) { "내 감정 수는 다섯 감정 순서로 제공해야 합니다." }
        require(myTotalCount >= 0L && totalCount >= myTotalCount) { "전체 횟수는 내 횟수 이상이어야 합니다." }
        require(emotionCounts.sumOf { it.count } == totalCount) { "감정별 전체 횟수 합과 전체 횟수가 일치해야 합니다." }
        require(myEmotionCounts.sumOf { it.count } == myTotalCount) { "감정별 내 횟수 합과 내 횟수가 일치해야 합니다." }
        require(
            EmotionKind.entries.all { kind ->
                myEmotionCounts.single { it.kind == kind }.count <= emotionCounts.single { it.kind == kind }.count
            },
        ) { "내 감정 횟수는 전체 감정 횟수보다 클 수 없습니다." }
    }

    val mostPressedEmotion: EmotionKind?
        get() = emotionCounts.maxByOrNull { it.count }?.takeIf { it.count > 0L }?.kind

    fun record(emotion: EmotionKind): PressPeriodSnapshot =
        copy(
            emotionCounts = emotionCounts.increment(emotion),
            myEmotionCounts = myEmotionCounts.increment(emotion),
            myTotalCount = myTotalCount + 1L,
            totalCount = totalCount + 1L,
        )

    private fun List<EmotionCountUiModel>.increment(emotion: EmotionKind): List<EmotionCountUiModel> =
        map { count ->
            if (count.kind == emotion) count.copy(count = count.count + 1L) else count
        }
}

internal data class PressPeriodSnapshots(
    val today: PressPeriodSnapshot,
    val thisWeek: PressPeriodSnapshot,
) {
    fun record(emotion: EmotionKind): PressPeriodSnapshots =
        copy(today = today.record(emotion), thisWeek = thisWeek.record(emotion))
}
