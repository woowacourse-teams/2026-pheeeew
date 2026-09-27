package com.pheeeew.feature.screens.group.join

import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.screens.group.model.GroupSummaryUiModel

/** 참여 시트 입력·조회·제출의 화면 상태입니다. 시트 표시 여부는 홈이 소유합니다. */
data class GroupJoinUiState(
    val input: String = "",
    val hasAttemptedSearch: Boolean = false,
    val lookup: GroupLookupState = GroupLookupState.Idle,
    val submission: GroupJoinSubmissionState = GroupJoinSubmissionState.Idle,
    val rateLimit: GroupJoinRateLimit? = null,
) {
    val isInteractionLocked: Boolean
        get() = submission is GroupJoinSubmissionState.Submitting || submission is GroupJoinSubmissionState.Succeeded

    val isDismissBlocked: Boolean
        get() = isInteractionLocked

    val isLookingUp: Boolean
        get() = lookup is GroupLookupState.Loading

    internal val codeValidation: GroupCodeValidation
        get() = GroupCodeRules.validate(input)

    val codeLength: Int
        get() = input.trim().length

    val shouldShowCodeValidationError: Boolean
        get() =
            codeValidation is GroupCodeValidation.TooLong ||
                (hasAttemptedSearch &&
                    codeValidation !is GroupCodeValidation.Empty &&
                    codeValidation !is GroupCodeValidation.Valid)

    val canSearch: Boolean
        get() =
            !isInteractionLocked &&
                !isLookingUp &&
                !isRetryBlocked &&
                codeValidation !is GroupCodeValidation.Empty

    val isLookupRetryBlocked: Boolean
        get() = rateLimit?.let { it.operation == GroupJoinRateLimitOperation.Lookup } == true

    val isJoinRetryBlocked: Boolean
        get() = rateLimit?.let { it.operation == GroupJoinRateLimitOperation.Join } == true

    val isRetryBlocked: Boolean
        get() = rateLimit != null

    val hasJoinTarget: Boolean
        get() {
            val found = lookup as? GroupLookupState.Found ?: return false
            val validation = codeValidation as? GroupCodeValidation.Valid ?: return false
            val retryAllowed =
                when (val result = submission) {
                    GroupJoinSubmissionState.Idle -> true

                    is GroupJoinSubmissionState.Failed ->
                        result.reason != GroupJoinFailure.AlreadyMember &&
                            result.reason != GroupJoinFailure.OutcomeUnknown

                    is GroupJoinSubmissionState.Submitting,
                    is GroupJoinSubmissionState.Succeeded,
                    -> false
                }
            return retryAllowed && validation.normalizedCode == found.requestedCode
        }

    val foundGroup: GroupSummaryUiModel?
        get() = (lookup as? GroupLookupState.Found)?.group

    val needsMembershipVerification: Boolean
        get() =
            when ((submission as? GroupJoinSubmissionState.Failed)?.reason) {
                GroupJoinFailure.AlreadyMember,
                GroupJoinFailure.OutcomeUnknown,
                -> true

                else -> false
            }

    val canJoin: Boolean
        get() = hasJoinTarget && !isRetryBlocked
}

data class GroupJoinRateLimit(
    val operation: GroupJoinRateLimitOperation,
)

enum class GroupJoinRateLimitOperation {
    Lookup,
    Join,
}

sealed interface GroupLookupState {
    data object Idle : GroupLookupState

    data class Loading(
        val requestId: Long,
        val requestedCode: String,
    ) : GroupLookupState

    data class Found(
        val requestedCode: String,
        val group: GroupSummaryUiModel,
    ) : GroupLookupState

    data class NotFound(
        val requestedCode: String,
    ) : GroupLookupState

    data class RateLimited(
        val requestedCode: String,
    ) : GroupLookupState

    data class PreviewStale(
        val requestedCode: String,
    ) : GroupLookupState

    data class Failed(
        val requestedCode: String,
    ) : GroupLookupState
}

sealed interface GroupJoinSubmissionState {
    data object Idle : GroupJoinSubmissionState

    data class Submitting(
        val operationKey: GroupOperationKey,
        val groupId: GroupId,
    ) : GroupJoinSubmissionState

    data class Failed(
        val reason: GroupJoinFailure,
    ) : GroupJoinSubmissionState

    data class Succeeded(
        val operationKey: GroupOperationKey,
        val groupId: GroupId,
    ) : GroupJoinSubmissionState
}

enum class GroupJoinFailure {
    Rejected,
    Unavailable,
    AlreadyMember,
    RateLimited,
    OutcomeUnknown,
}
