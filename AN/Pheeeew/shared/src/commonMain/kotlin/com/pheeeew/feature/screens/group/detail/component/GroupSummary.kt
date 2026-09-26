package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_invite_code
import pheeeew.shared.generated.resources.group_detail_member_count

/** 시안의 상단 한 줄: 멤버 수와 초대코드 버튼만 표시합니다. */
@Composable
internal fun GroupSummary(
    memberCount: Int,
    onInviteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.group_detail_member_count, memberCount),
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            color = Color(0xFF7B817B),
            fontSize = 16.sp,
        )
        Box(
            modifier =
                Modifier
                    .sizeIn(minWidth = 71.dp, minHeight = 34.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onInviteClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.group_detail_invite_code),
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFF0F2EC))
                        .padding(horizontal = 15.dp, vertical = 6.dp),
                color = AppColors.GroupInk,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
