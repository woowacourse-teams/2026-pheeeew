package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.group.create.GroupCreateFailure
import com.pheeeew.feature.screens.group.create.component.CreateConfirmationCard
import com.pheeeew.feature.screens.group.model.GroupJoinType
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_confirm
import pheeeew.shared.generated.resources.group_create_confirm_body
import pheeeew.shared.generated.resources.group_create_confirm_cancel
import pheeeew.shared.generated.resources.group_create_confirm_group_stamp_summary
import pheeeew.shared.generated.resources.group_create_confirm_join_type
import pheeeew.shared.generated.resources.group_create_confirm_title
import pheeeew.shared.generated.resources.group_create_failure_close
import pheeeew.shared.generated.resources.group_create_failure_edit
import pheeeew.shared.generated.resources.group_create_failure_invalid_input
import pheeeew.shared.generated.resources.group_create_failure_join_type_unavailable
import pheeeew.shared.generated.resources.group_create_failure_rate_limited
import pheeeew.shared.generated.resources.group_create_failure_retry
import pheeeew.shared.generated.resources.group_create_failure_title
import pheeeew.shared.generated.resources.group_create_failure_unavailable
import pheeeew.shared.generated.resources.group_create_failure_unknown
import pheeeew.shared.generated.resources.group_create_join_type_private
import pheeeew.shared.generated.resources.group_create_join_type_public
import pheeeew.shared.generated.resources.group_create_submitting
import pheeeew.shared.generated.resources.group_create_submitting_title

@Preview(name = "그룹 생성 확인 - 기본", widthDp = 360, heightDp = 640)
@Composable
private fun CreateConfirmationCardPreview() {
    CreateConfirmationPreviewFrame(groupName = "우리 동네", stampLabel = "히유")
}

@Preview(name = "그룹 생성 확인 - 좁은 화면과 긴 그룹명", widthDp = 320, heightDp = 640)
@Composable
private fun CreateConfirmationCardNarrowPreview() {
    CreateConfirmationPreviewFrame(groupName = "가나다라마바사아자차", stampLabel = "가나다라")
}

@Preview(name = "그룹 생성 확인 - 큰 글씨", widthDp = 320, heightDp = 480, fontScale = 1.5f)
@Composable
private fun CreateConfirmationCardLargeFontPreview() {
    CreateConfirmationPreviewFrame(groupName = "가나다라마바사아자차", stampLabel = "가나다라")
}

@Composable
private fun CreateConfirmationPreviewFrame(
    groupName: String,
    stampLabel: String,
) {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(AppColors.GroupBackground).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CreateConfirmationCard(
                title = stringResource(Res.string.group_create_confirm_title),
                groupName = groupName,
                stampLabel = stampLabel,
                joinType = GroupJoinType.PRIVATE,
                onDismiss = {},
                onConfirm = {},
            )
        }
    }
}
