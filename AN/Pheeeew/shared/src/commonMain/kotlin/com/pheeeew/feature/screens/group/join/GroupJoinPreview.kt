package com.pheeeew.feature.screens.group.join

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import com.pheeeew.feature.screens.group.preview.HomeFixtures

@Preview(name = "그룹 참여 - 코드 입력")
@Composable
private fun GroupJoinInputPreview() {
    GroupJoinSheet(
        uiState = GroupJoinUiState(),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 조회 결과")
@Composable
private fun GroupJoinFoundPreview() {
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "H1Y226",
                lookup = GroupLookupState.Found("H1Y226", HomeFixtures.groups.first()),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 조회 중")
@Composable
private fun GroupJoinSearchingPreview() {
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "H1Y226",
                lookup = GroupLookupState.Loading(requestId = 1, requestedCode = "H1Y226"),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 그룹 없음")
@Composable
private fun GroupJoinNotFoundPreview() {
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "NOPE00",
                hasAttemptedSearch = true,
                lookup = GroupLookupState.NotFound("NOPE00"),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 조회 오류")
@Composable
private fun GroupJoinLookupFailurePreview() {
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "H1Y226",
                lookup = GroupLookupState.Failed("H1Y226"),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 조회 요청 제한")
@Composable
private fun GroupJoinLookupRateLimitedPreview() {
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "H1Y226",
                lookup = GroupLookupState.RateLimited("H1Y226"),
                rateLimit = GroupJoinRateLimit(GroupJoinRateLimitOperation.Lookup),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 오류")
@Composable
private fun GroupJoinFailurePreview() {
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "H1Y226",
                lookup = GroupLookupState.Found("H1Y226", HomeFixtures.groups.first()),
                submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.Unavailable),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 이미 참여한 그룹")
@Composable
private fun GroupJoinAlreadyMemberPreview() {
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "H1Y226",
                lookup = GroupLookupState.Found("H1Y226", HomeFixtures.groups.first()),
                submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.AlreadyMember),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 참여 중")
@Composable
private fun GroupJoinSubmittingPreview() {
    val group = HomeFixtures.groups.first()
    GroupJoinSheet(
        uiState =
            GroupJoinUiState(
                input = "H1Y226",
                lookup = GroupLookupState.Found("H1Y226", group),
                submission = GroupJoinSubmissionState.Submitting(GroupOperationKey("preview", 1L), group.id),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 코드가 짧음")
@Composable
private fun GroupJoinCodeTooShortPreview() {
    GroupJoinSheet(
        uiState = GroupJoinUiState(input = "ABC", hasAttemptedSearch = true),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 코드가 김")
@Composable
private fun GroupJoinCodeTooLongPreview() {
    GroupJoinSheet(
        uiState = GroupJoinUiState(input = "H1Y2260", hasAttemptedSearch = true),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}

@Preview(name = "그룹 참여 - 허용되지 않는 문자")
@Composable
private fun GroupJoinInvalidCharactersPreview() {
    GroupJoinSheet(
        uiState = GroupJoinUiState(input = "HIYU-6", hasAttemptedSearch = true),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}
