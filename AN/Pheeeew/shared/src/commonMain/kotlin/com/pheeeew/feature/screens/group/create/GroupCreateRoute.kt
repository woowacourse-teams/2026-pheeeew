package com.pheeeew.feature.screens.group.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKey
import kotlinx.coroutines.flow.collect

/** ViewModel 수집과 생성 성공 콜백을 연결합니다. 앱 navigation 그래프는 소유하지 않습니다. */
@Composable
fun GroupCreateRoute(
    viewModel: GroupCreateViewModel,
    isCurrentDestination: Boolean,
    onBack: () -> Unit,
    onCreated: (groupId: GroupId, operationKey: GroupOperationKey) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCreated by rememberUpdatedState(onCreated)

    LaunchedEffect(viewModel, lifecycleOwner, isCurrentDestination) {
        if (!isCurrentDestination) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.uiState.collect { state ->
                val result = state.submission as? GroupCreateSubmissionState.Succeeded ?: return@collect
                currentOnCreated(result.groupId, result.operationKey)
                viewModel.acknowledgeCreated(result.operationKey)
            }
        }
    }

    GroupCreateScreen(
        uiState = uiState,
        formRules = viewModel.formRules,
        onBack = { if (viewModel.onBackRequested()) onBack() },
        onNameChanged = viewModel::onNameChanged,
        onDescriptionChanged = viewModel::onDescriptionChanged,
        onStampLabelChanged = viewModel::onStampLabelChanged,
        onStampShapeChanged = viewModel::onStampShapeChanged,
        onStampTextColorChanged = viewModel::onStampTextColorChanged,
        onCreateClick = viewModel::onCreateClick,
        onCancelConfirmation = viewModel::onCancelConfirmation,
        onConfirmCreate = viewModel::onConfirmCreate,
        onDismissFailure = viewModel::onDismissFailure,
        onRetryFailure = viewModel::onRetryFailure,
        onCheckGroupsAfterUnknownOutcome = viewModel::onCheckGroupsAfterUnknownOutcome,
        onSelectRecoveryCandidate = viewModel::onSelectRecoveryCandidate,
        onRetryUnknownCreation = viewModel::onRetryUnknownCreation,
        onOpenColorSheet = viewModel::openColorSheet,
        onColorSelectionChanged = viewModel::onColorSelectionChanged,
        onCloseColorSheet = viewModel::closeColorSheet,
        onApplyColor = viewModel::applyColorSelection,
    )
}
