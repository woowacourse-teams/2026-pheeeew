package com.pheeeew.feature.map.sighlist

internal const val MAX_REPORT_REASON_LENGTH = 200
internal const val DEFAULT_SIGH_REPORT_REASON = "기타"

internal val sighReportReasons =
    listOf(
        DEFAULT_SIGH_REPORT_REASON,
        "명예훼손 및 사생활 침해",
        "사이버 괴롭힘",
        "자해/극단적 선택/폭력",
        "개인정보 노출",
        "사기/상업성 광고",
    )

data class SighModerationTarget(
    val sighId: Long,
    val nickname: String,
)

data class SighModerationUiState(
    val actionTarget: SighModerationTarget? = null,
    val blockTarget: SighModerationTarget? = null,
    val reportTarget: SighModerationTarget? = null,
    val selectedReason: String? = null,
    val description: String = "",
    val blockErrorMessage: String? = null,
    val isBlocking: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val isReportVisible: Boolean
        get() = reportTarget != null

    val canSubmitReport: Boolean
        get() = reportTarget != null && selectedReason != null && !isSubmitting

    internal fun reportRequestReason(): String =
        listOfNotNull(selectedReason, description.trim().takeIf(String::isNotEmpty))
            .joinToString(" - ")
            .take(MAX_REPORT_REASON_LENGTH)
}
