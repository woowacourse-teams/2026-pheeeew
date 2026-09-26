package com.pheeeew.feature.screens.group.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import com.pheeeew.feature.screens.group.create.model.StampColorSheetState
import com.pheeeew.feature.screens.group.create.model.StampTextColorOption
import com.pheeeew.feature.screens.group.model.GroupId
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_back
import pheeeew.shared.generated.resources.group_create_submit
import pheeeew.shared.generated.resources.group_create_title
import pheeeew.shared.generated.resources.ic_arrow_back

/** 상태를 렌더링하고 독립된 사용자 입력 이벤트를 호출자에게 전달합니다. */
@Composable
fun GroupCreateScreen(
    uiState: GroupCreateUiState,
    formRules: GroupFormRules,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onStampLabelChanged: (String) -> Unit,
    onStampShapeChanged: (StampShapeId) -> Unit,
    onStampTextColorChanged: (StampTextColorOption) -> Unit,
    onCreateClick: () -> Unit,
    onCancelConfirmation: () -> Unit,
    onConfirmCreate: () -> Unit,
    onDismissFailure: () -> Unit,
    onRetryFailure: () -> Unit,
    onCheckGroupsAfterUnknownOutcome: () -> Unit = {},
    onSelectRecoveryCandidate: (GroupId) -> Unit = {},
    onRetryUnknownCreation: () -> Unit = {},
    onOpenColorSheet: () -> Unit,
    onColorSelectionChanged: (StampColorSelection) -> Unit,
    onCloseColorSheet: () -> Unit,
    onApplyColor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = uiState.submission == GroupCreateSubmissionState.Editing
    val backDescription = stringResource(Res.string.group_create_back)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        GroupCreateTopBar(backDescription = backDescription, onBack = onBack)

        GroupCreateForm(
            uiState = uiState,
            formRules = formRules,
            enabled = enabled,
            onNameChanged = onNameChanged,
            onDescriptionChanged = onDescriptionChanged,
            onStampLabelChanged = onStampLabelChanged,
            onStampShapeChanged = onStampShapeChanged,
            onStampTextColorChanged = onStampTextColorChanged,
            onOpenColorSheet = onOpenColorSheet,
            modifier = Modifier.weight(1f),
        )

        Button(
            onClick = onCreateClick,
            enabled = enabled && uiState.colorSheet is StampColorSheetState.Closed,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.GroupInk),
        ) {
            Text(
                text = stringResource(Res.string.group_create_submit),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    GroupCreateOverlays(
        uiState = uiState,
        onCancelConfirmation = onCancelConfirmation,
        onConfirmCreate = onConfirmCreate,
        onDismissFailure = onDismissFailure,
        onRetryFailure = onRetryFailure,
        onCheckGroupsAfterUnknownOutcome = onCheckGroupsAfterUnknownOutcome,
        onSelectRecoveryCandidate = onSelectRecoveryCandidate,
        onRetryUnknownCreation = onRetryUnknownCreation,
        onColorSelectionChanged = onColorSelectionChanged,
        onCloseColorSheet = onCloseColorSheet,
        onApplyColor = onApplyColor,
    )
}

@Composable
private fun GroupCreateTopBar(
    backDescription: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.group_create_title),
            color = AppColors.GroupInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = null,
                tint = AppColors.GroupInk,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
