package com.pheeeew.feature.screens.report

fun interface ReportAction {
    suspend fun report(
        emotionId: Long,
        reason: String,
    ): ReportResult
}

sealed interface ReportResult {
    data object Reported : ReportResult

    data object OwnEmotion : ReportResult

    data object Failed : ReportResult
}
