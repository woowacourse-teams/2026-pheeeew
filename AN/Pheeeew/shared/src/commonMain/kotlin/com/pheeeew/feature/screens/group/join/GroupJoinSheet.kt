package com.pheeeew.feature.screens.group.join

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.screens.group.join.component.GroupJoinPreviewCard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_join_close
import pheeeew.shared.generated.resources.group_join_code_placeholder
import pheeeew.shared.generated.resources.group_join_description
import pheeeew.shared.generated.resources.group_join_failure_outcome_unknown
import pheeeew.shared.generated.resources.group_join_failure_rejected
import pheeeew.shared.generated.resources.group_join_failure_unavailable
import pheeeew.shared.generated.resources.group_join_hint
import pheeeew.shared.generated.resources.group_join_join
import pheeeew.shared.generated.resources.group_join_joining
import pheeeew.shared.generated.resources.group_join_lookup_failure
import pheeeew.shared.generated.resources.group_join_not_found
import pheeeew.shared.generated.resources.group_join_search
import pheeeew.shared.generated.resources.group_join_searching
import pheeeew.shared.generated.resources.group_join_title
import pheeeew.shared.generated.resources.group_join_verify_membership

private val JoinButtonShape = RoundedCornerShape(24.dp)
private val JoinInputShape = RoundedCornerShape(10.dp)
private val JoinErrorColor = Color(0xFFC94D43)

/** 코드 입력 → 명시적 조회 → 그룹 확인 → 참여의 전체 바텀시트입니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupJoinSheet(
    uiState: GroupJoinUiState,
    onCodeChanged: (String) -> Unit,
    onSearchClick: () -> Unit,
    onJoinClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestDismissBlocked by rememberUpdatedState(uiState.isDismissBlocked)
    val keyboardController = LocalSoftwareKeyboardController.current
    val closeDescription = stringResource(Res.string.group_join_close)
    var isDismissing by remember { mutableStateOf(false) }
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue ->
                !latestDismissBlocked || targetValue != androidx.compose.material3.SheetValue.Hidden
            },
        )

    fun dismissSheet() {
        if (uiState.isDismissBlocked || isDismissing) return
        isDismissing = true
        scope.launch {
            sheetState.hide()
            latestDismiss()
        }
    }

    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = ::dismissSheet,
        sheetState = sheetState,
        containerColor = Color.White,
        contentColor = AppColors.GroupInk,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.group_join_title),
                        color = AppColors.GroupInk,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.group_join_description),
                        modifier = Modifier.padding(top = 5.dp),
                        color = AppColors.RankingSecondaryContent,
                        fontSize = 13.sp,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF3F5F4))
                            .clickable(
                                enabled = !uiState.isDismissBlocked && !isDismissing,
                                role = Role.Button,
                                onClick = ::dismissSheet,
                            ).semantics { contentDescription = closeDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", color = AppColors.GroupInk, fontSize = 24.sp, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = uiState.input,
                onValueChange = onCodeChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isInteractionLocked && !isDismissing,
                placeholder = { Text(stringResource(Res.string.group_join_code_placeholder)) },
                singleLine = true,
                shape = JoinInputShape,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onSearchClick()
                        },
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.GroupInk,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color(0xFFF2F4F3),
                        unfocusedContainerColor = Color(0xFFF2F4F3),
                        disabledContainerColor = Color(0xFFF2F4F3),
                        focusedTextColor = AppColors.GroupInk,
                        unfocusedTextColor = AppColors.GroupInk,
                        disabledTextColor = AppColors.GroupInk,
                        cursorColor = AppColors.GroupInk,
                    ),
            )

            LookupMessage(lookup = uiState.lookup)
            uiState.foundGroup?.let { group ->
                Spacer(Modifier.height(12.dp))
                GroupJoinPreviewCard(group = group)
            }
            SubmissionMessage(submission = uiState.submission)

            Spacer(Modifier.height(18.dp))
            val joining = uiState.submission is GroupJoinSubmissionState.Submitting
            val unknownOutcome =
                (uiState.submission as? GroupJoinSubmissionState.Failed)?.reason == GroupJoinFailure.OutcomeUnknown
            val showJoinAction = uiState.canJoin
            val buttonText =
                when {
                    joining -> stringResource(Res.string.group_join_joining)
                    unknownOutcome -> stringResource(Res.string.group_join_verify_membership)
                    showJoinAction -> stringResource(Res.string.group_join_join)
                    uiState.isLookingUp -> stringResource(Res.string.group_join_searching)
                    else -> stringResource(Res.string.group_join_search)
                }
            val buttonEnabled =
                !isDismissing &&
                    when {
                        joining -> false
                        unknownOutcome -> true
                        showJoinAction -> uiState.canJoin
                        else -> uiState.canSearch
                    }
            JoinPrimaryButton(
                text = buttonText,
                enabled = buttonEnabled,
                isLoading = joining || uiState.isLookingUp,
                onClick = {
                    when {
                        unknownOutcome -> {
                            dismissSheet()
                        }

                        showJoinAction -> {
                            keyboardController?.hide()
                            onJoinClick()
                        }

                        else -> {
                            keyboardController?.hide()
                            onSearchClick()
                        }
                    }
                },
            )
            Text(
                text = stringResource(Res.string.group_join_hint),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = AppColors.RankingSecondaryContent,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LookupMessage(lookup: GroupLookupState) {
    val message =
        when (lookup) {
            is GroupLookupState.NotFound -> stringResource(Res.string.group_join_not_found)

            is GroupLookupState.Failed -> stringResource(Res.string.group_join_lookup_failure)

            GroupLookupState.Idle,
            is GroupLookupState.Found,
            is GroupLookupState.Loading,
            -> null
        }
    if (message != null) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = JoinErrorColor,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SubmissionMessage(submission: GroupJoinSubmissionState) {
    val message =
        when (submission) {
            is GroupJoinSubmissionState.Failed -> {
                when (submission.reason) {
                    GroupJoinFailure.Rejected -> stringResource(Res.string.group_join_failure_rejected)
                    GroupJoinFailure.Unavailable -> stringResource(Res.string.group_join_failure_unavailable)
                    GroupJoinFailure.OutcomeUnknown -> stringResource(Res.string.group_join_failure_outcome_unknown)
                }
            }

            GroupJoinSubmissionState.Idle,
            is GroupJoinSubmissionState.Submitting,
            is GroupJoinSubmissionState.Succeeded,
            -> {
                null
            }
        }
    if (message != null) {
        Text(
            text = message,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = JoinErrorColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JoinPrimaryButton(
    text: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(JoinButtonShape)
                .background(if (enabled) AppColors.GroupInk else Color(0xFFE3E8E5))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    color = if (enabled) Color.White else AppColors.GroupInk,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else AppColors.RankingSecondaryContent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
