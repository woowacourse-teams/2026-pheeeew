package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.pheeeew.core.designsystem.component.AppPopup
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.DetailTopBar
import com.pheeeew.core.designsystem.component.LoadErrorContent
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.detail.GroupDetailActions
import com.pheeeew.feature.screens.group.detail.GroupDetailContent
import com.pheeeew.feature.screens.group.detail.GroupDetailScreen
import com.pheeeew.feature.screens.group.detail.GroupDetailUiState
import com.pheeeew.feature.screens.group.detail.component.GroupDetailNoticeSnackbar
import com.pheeeew.feature.screens.group.detail.component.InviteCodeDialog
import com.pheeeew.feature.screens.group.detail.component.LeaveGroupDialog
import com.pheeeew.feature.screens.group.detail.model.toReadyUiModel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_back
import pheeeew.shared.generated.resources.group_detail_copy_failed
import pheeeew.shared.generated.resources.group_detail_copy_succeeded
import pheeeew.shared.generated.resources.group_detail_invite_share_unavailable
import pheeeew.shared.generated.resources.group_detail_loading
import pheeeew.shared.generated.resources.group_detail_membership_changed_body
import pheeeew.shared.generated.resources.group_detail_membership_changed_title
import pheeeew.shared.generated.resources.group_detail_menu_leave
import pheeeew.shared.generated.resources.group_detail_more
import pheeeew.shared.generated.resources.group_detail_not_found_body
import pheeeew.shared.generated.resources.group_detail_not_found_title
import pheeeew.shared.generated.resources.group_detail_return_home
import pheeeew.shared.generated.resources.group_home_title
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted

@Preview(widthDp = 402, heightDp = 874, name = "그룹 상세 화면")
@Composable
private fun GroupDetailScreenPreview() {
    GroupDetailScreen(
        uiState =
            GroupDetailUiState(
                content =
                    GroupDetailContent.Ready(
                        previewGroupDetailUiModel(GroupRole.MEMBER),
                    ),
            ),
        actions = previewActions(),
        modifier = Modifier.fillMaxSize(),
    )
}
