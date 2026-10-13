package com.pheeeew.feature.screens.group.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.group.detail.GroupDetailJoinUiState
import com.pheeeew.feature.screens.group.detail.model.GroupDetailReadyUiModel
import com.pheeeew.feature.screens.group.model.GroupJoinType
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_code_entry
import pheeeew.shared.generated.resources.group_detail_invite_code
import pheeeew.shared.generated.resources.group_detail_join
import pheeeew.shared.generated.resources.group_detail_join_became_private
import pheeeew.shared.generated.resources.group_detail_join_check_failed
import pheeeew.shared.generated.resources.group_detail_join_checking
import pheeeew.shared.generated.resources.group_detail_join_completed
import pheeeew.shared.generated.resources.group_detail_join_not_confirmed
import pheeeew.shared.generated.resources.group_detail_join_processing
import pheeeew.shared.generated.resources.group_detail_join_type_private
import pheeeew.shared.generated.resources.group_detail_join_type_public
import pheeeew.shared.generated.resources.group_detail_joined
import pheeeew.shared.generated.resources.group_detail_member_count
import pheeeew.shared.generated.resources.group_detail_members_open
import pheeeew.shared.generated.resources.group_detail_private_join_description
import pheeeew.shared.generated.resources.group_detail_private_join_title
import pheeeew.shared.generated.resources.group_detail_rank_empty
import pheeeew.shared.generated.resources.group_detail_rank_number
import pheeeew.shared.generated.resources.group_join_failure_outcome_unknown
import pheeeew.shared.generated.resources.group_join_failure_unavailable
import pheeeew.shared.generated.resources.group_join_verify_membership

@Composable
internal fun GroupProfile(
    group: GroupDetailReadyUiModel,
    onInviteClick: () -> Unit,
    joinState: GroupDetailJoinUiState = GroupDetailJoinUiState(),
    onJoinClick: () -> Unit = {},
    onMembersClick: (() -> Unit)? = null,
) {
    val canOpenMembers = group.role != GroupRole.NONE && onMembersClick != null
    val memberCountDescription =
        stringResource(Res.string.group_detail_members_open, group.memberCount)
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .then(
                            if (canOpenMembers) {
                                Modifier
                                    .clickable(role = Role.Button, onClick = { onMembersClick.invoke() })
                                    .semantics { contentDescription = memberCountDescription }
                            } else {
                                Modifier
                            },
                        ).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(Res.string.group_detail_member_count, group.memberCount),
                    color = AppColors.GroupInk,
                    fontSize = 12.sp,
                )
                if (canOpenMembers) {
                    Text("›", color = AppColors.TextSecondary, fontSize = 16.sp, lineHeight = 16.sp)
                }
            }
            if (group.role != GroupRole.NONE && group.inviteCode != null) {
                Box(
                    Modifier
                        .background(Color(0xFFF0F1ED), CircleShape)
                        .clickable(role = Role.Button, onClick = onInviteClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(Res.string.group_detail_invite_code),
                        color = AppColors.GroupInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        AnimatedVisibility(visible = group.role != GroupRole.NONE) {
            Column {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.group_detail_joined),
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (group.role == GroupRole.NONE) Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GroupStamp(appearance = group.stamp, size = 84.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = group.description.orEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp),
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupVisibilityLabel(group.joinType)
            AnimatedVisibility(
                visible =
                    group.role == GroupRole.NONE && (
                        group.joinType == GroupJoinType.PUBLIC ||
                            joinState.phase == GroupDetailJoinPhase.VerificationRequired
                    ),
            ) {
                GroupDetailJoinCallToAction(onClick = onJoinClick, phase = joinState.phase)
            }
        }
        AnimatedVisibility(
            visible =
                group.role == GroupRole.NONE &&
                    joinState.failure != null,
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            when (joinState.failure) {
                                null -> {
                                    Res.string.group_join_failure_unavailable
                                }

                                GroupDetailJoinFailure.Unavailable -> {
                                    Res.string.group_join_failure_unavailable
                                }

                                GroupDetailJoinFailure.OutcomeUnknown -> {
                                    Res.string.group_join_failure_outcome_unknown
                                }

                                GroupDetailJoinFailure.NotJoined -> {
                                    Res.string.group_detail_join_not_confirmed
                                }

                                GroupDetailJoinFailure.PrivateGroup -> {
                                    Res.string.group_detail_join_became_private
                                }
                            },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun GroupVisibilityLabel(joinType: GroupJoinType) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .size(6.dp)
                .background(
                    if (joinType == GroupJoinType.PUBLIC) AppColors.RankingAccent else AppColors.TextSecondary,
                    CircleShape,
                ),
        )
        Text(
            text =
                stringResource(
                    if (joinType == GroupJoinType.PUBLIC) {
                        Res.string.group_detail_join_type_public
                    } else {
                        Res.string.group_detail_join_type_private
                    },
                ),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun GroupDetailJoinCallToAction(
    onClick: () -> Unit,
    phase: GroupDetailJoinPhase = GroupDetailJoinPhase.Idle,
    codeEntry: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier
                .height(36.dp)
                .widthIn(min = 90.dp)
                .raisedButtonBorder(CircleShape, interactionSource)
                .clip(CircleShape)
                .background(AppColors.Primary)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    enabled = phase == GroupDetailJoinPhase.Idle || phase == GroupDetailJoinPhase.VerificationRequired,
                    onClick = onClick,
                ).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(phase) { currentPhase ->
            Text(
                text =
                    stringResource(
                        when (currentPhase) {
                            GroupDetailJoinPhase.Idle -> {
                                if (codeEntry) {
                                    Res.string.group_detail_code_entry
                                } else {
                                    Res.string.group_detail_join
                                }
                            }

                            GroupDetailJoinPhase.Submitting -> {
                                Res.string.group_detail_join_processing
                            }

                            GroupDetailJoinPhase.Checking -> {
                                Res.string.group_detail_join_checking
                            }

                            GroupDetailJoinPhase.VerificationRequired -> {
                                Res.string.group_join_verify_membership
                            }

                            GroupDetailJoinPhase.Completed -> {
                                Res.string.group_detail_join_completed
                            }
                        },
                    ),
                color = AppColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = notoSansKrFontFamily(),
            )
        }
    }
}

@Composable
internal fun GroupDetailPrivateJoinNotice(
    onJoinClick: () -> Unit,
    phase: GroupDetailJoinPhase,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.RankingSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(Res.string.group_detail_private_join_title),
            color = AppColors.GroupInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.group_detail_private_join_description),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        GroupDetailJoinCallToAction(onClick = onJoinClick, phase = phase, codeEntry = true)
    }
}

@Composable
internal fun GroupStatistics(group: GroupDetailReadyUiModel) {
    HorizontalDivider(color = AppColors.BorderLight)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Statistic(
            label = "이번주 스탬프",
            rank = group.weeklyStampRank,
            count = "(${formatCount(group.weeklyStampCount)}개)",
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).height(54.dp).background(AppColors.BorderLight))
        Statistic(
            label = "이번주 프레스",
            rank = group.weeklyEmotionPressRank,
            count = "(${formatCount(group.weeklyEmotionPressCount)}번)",
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider(color = Rule)
}

@Composable
private fun Statistic(
    label: String,
    rank: Int?,
    count: String,
    modifier: Modifier,
) {
    Column(
        modifier.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            modifier = Modifier.align(Alignment.Start).padding(start = 4.dp),
            color = AppColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                rank?.let { stringResource(Res.string.group_detail_rank_number, it) }
                    ?: stringResource(Res.string.group_detail_rank_empty),
            modifier = Modifier,
            color = AppColors.GroupInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count,
            modifier = Modifier.padding(end = 4.dp).align(Alignment.End),
            color = AppColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

private val Rule = Color(0xFFDFE2D9)

private fun formatCount(count: Long): String = count.toString()
