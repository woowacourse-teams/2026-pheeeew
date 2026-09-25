package com.pheeeew.feature.screens.group.join

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pheeeew.feature.screens.group.preview.HomeFixtures

@Preview(name = "그룹 참여 - 코드 입력")
@Composable
private fun GroupJoinInputPreview() {
    GroupJoinSheet(
        uiState = GroupJoinUiState(input = "HIYU26"),
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
                input = "HIYU26",
                lookup = GroupLookupState.Found("HIYU26", HomeFixtures.groups.first()),
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
                input = "HIYU26",
                lookup = GroupLookupState.Loading(requestId = 1, requestedCode = "HIYU26"),
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
                input = "UNKNOWN",
                lookup = GroupLookupState.NotFound("UNKNOWN"),
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
                input = "HIYU26",
                lookup = GroupLookupState.Failed("HIYU26"),
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
                input = "HIYU26",
                lookup = GroupLookupState.Found("HIYU26", HomeFixtures.groups.first()),
                submission = GroupJoinSubmissionState.Failed(GroupJoinFailure.Unavailable),
            ),
        onCodeChanged = {},
        onSearchClick = {},
        onJoinClick = {},
        onDismiss = {},
    )
}
