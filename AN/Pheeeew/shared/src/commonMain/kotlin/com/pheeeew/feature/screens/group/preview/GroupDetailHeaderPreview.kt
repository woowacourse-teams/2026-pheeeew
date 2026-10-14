package com.pheeeew.feature.screens.group.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinUiState
import com.pheeeew.feature.screens.group.detail.GroupProfile
import com.pheeeew.feature.screens.group.detail.GroupStatistics
import com.pheeeew.feature.screens.group.detail.model.GroupDetailReadyUiModel
import com.pheeeew.feature.screens.group.model.GroupJoinType
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_invite_code
import pheeeew.shared.generated.resources.group_detail_join
import pheeeew.shared.generated.resources.group_detail_join_failure_api_pending
import pheeeew.shared.generated.resources.group_detail_join_type_private
import pheeeew.shared.generated.resources.group_detail_join_type_public
import pheeeew.shared.generated.resources.group_detail_joined
import pheeeew.shared.generated.resources.group_detail_member_count
import pheeeew.shared.generated.resources.group_detail_private_join_description
import pheeeew.shared.generated.resources.group_detail_private_join_title
import pheeeew.shared.generated.resources.group_detail_rank_empty
import pheeeew.shared.generated.resources.group_detail_rank_number

@Preview(widthDp = 402, name = "그룹 상세 · 헤더", showBackground = true)
@Composable
private fun GroupDetailHeaderPreview() {
    val group = previewGroupDetailReadyUiModel()
    Column(Modifier.padding(horizontal = 24.dp)) {
        GroupProfile(group, onInviteClick = {})
        Spacer(Modifier.height(18.dp))
        GroupStatistics(group)
    }
}

@Preview(widthDp = 402, name = "그룹 상세 · 미가입 헤더", showBackground = true)
@Composable
private fun GroupDetailGuestHeaderPreview() {
    val group = previewGroupDetailReadyUiModel(GroupRole.NONE)
    Column(Modifier.padding(horizontal = 24.dp)) {
        GroupProfile(group, onInviteClick = {})
        Spacer(Modifier.height(18.dp))
        GroupStatistics(group)
    }
}

@Preview(widthDp = 402, name = "그룹 상세 · 순위 미집계", showBackground = true)
@Composable
private fun GroupDetailUnrankedHeaderPreview() {
    val group =
        previewGroupDetailReadyUiModel().copy(
            weeklyStampCount = 0,
            weeklyStampRank = null,
            weeklyEmotionPressCount = 0,
            weeklyEmotionPressRank = null,
        )
    Column(Modifier.padding(horizontal = 24.dp)) {
        GroupProfile(group, onInviteClick = {})
        Spacer(Modifier.height(18.dp))
        GroupStatistics(group)
    }
}

@Preview(widthDp = 320, name = "상단 칩 · 공개 비멤버 참여하기", showBackground = true)
@Composable
private fun PublicGuestActionChipPreview() {
    ActionChipHeaderPreview(GroupRole.NONE, com.pheeeew.feature.screens.group.model.GroupJoinType.PUBLIC)
}

@Preview(widthDp = 320, name = "상단 칩 · 비공개 비멤버 코드 참여", showBackground = true)
@Composable
private fun PrivateGuestActionChipPreview() {
    ActionChipHeaderPreview(GroupRole.NONE, com.pheeeew.feature.screens.group.model.GroupJoinType.PRIVATE)
}

@Preview(widthDp = 320, name = "상단 칩 · 공개 멤버 초대 코드", showBackground = true)
@Composable
private fun PublicMemberActionChipPreview() {
    ActionChipHeaderPreview(GroupRole.MEMBER, com.pheeeew.feature.screens.group.model.GroupJoinType.PUBLIC)
}

@Preview(widthDp = 320, name = "상단 칩 · 비공개 멤버 초대 코드", showBackground = true)
@Composable
private fun PrivateMemberActionChipPreview() {
    ActionChipHeaderPreview(GroupRole.MEMBER, com.pheeeew.feature.screens.group.model.GroupJoinType.PRIVATE)
}

@Preview(widthDp = 320, name = "상단 칩 · 공개 그룹장 초대 코드", showBackground = true)
@Composable
private fun PublicOwnerActionChipPreview() {
    ActionChipHeaderPreview(GroupRole.OWNER, com.pheeeew.feature.screens.group.model.GroupJoinType.PUBLIC)
}

@Preview(widthDp = 320, name = "상단 칩 · 비공개 그룹장 초대 코드", showBackground = true)
@Composable
private fun PrivateOwnerActionChipPreview() {
    ActionChipHeaderPreview(GroupRole.OWNER, com.pheeeew.feature.screens.group.model.GroupJoinType.PRIVATE)
}

@Composable
private fun ActionChipHeaderPreview(
    role: GroupRole,
    type: com.pheeeew.feature.screens.group.model.GroupJoinType,
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        GroupProfile(previewGroupDetailReadyUiModel(role).copy(joinType = type), onInviteClick = {}, onJoinClick = {})
    }
}
