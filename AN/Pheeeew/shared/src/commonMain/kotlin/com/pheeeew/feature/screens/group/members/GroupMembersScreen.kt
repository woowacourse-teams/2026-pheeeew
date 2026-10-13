package com.pheeeew.feature.screens.group.members

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.component.DetailTopBar
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.domain.model.group.GroupRole
import com.pheeeew.feature.screens.group.component.GroupListSkeleton
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_members_accessibility_nickname
import pheeeew.shared.generated.resources.group_members_accessibility_owner_nickname
import pheeeew.shared.generated.resources.group_members_empty_body
import pheeeew.shared.generated.resources.group_members_empty_title
import pheeeew.shared.generated.resources.group_members_forbidden_body
import pheeeew.shared.generated.resources.group_members_forbidden_title
import pheeeew.shared.generated.resources.group_members_loading_more
import pheeeew.shared.generated.resources.group_members_next_page_error
import pheeeew.shared.generated.resources.group_members_not_found_body
import pheeeew.shared.generated.resources.group_members_not_found_title
import pheeeew.shared.generated.resources.group_members_owner
import pheeeew.shared.generated.resources.group_members_retry
import pheeeew.shared.generated.resources.group_members_return_to_detail
import pheeeew.shared.generated.resources.group_members_title
import pheeeew.shared.generated.resources.group_members_unavailable_body
import pheeeew.shared.generated.resources.group_members_unavailable_title
import pheeeew.shared.generated.resources.group_members_view_order

@Composable
fun GroupMembersScreen(
    uiState: GroupMembersUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppColors.GroupBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        DetailTopBar(
            title = stringResource(Res.string.group_members_title),
            onBack = onBack,
            modifier = Modifier.background(AppColors.GroupBackground),
        )
        when (val content = uiState.content) {
            GroupMembersContent.Loading -> {
                GroupListSkeleton(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    memberRows = true,
                )
            }

            GroupMembersContent.Empty -> {
                MembersMessage(
                    title = stringResource(Res.string.group_members_empty_title),
                    body = stringResource(Res.string.group_members_empty_body),
                    action = stringResource(Res.string.group_members_return_to_detail),
                    onAction = onBack,
                )
            }

            GroupMembersContent.Failed -> {
                MembersMessage(
                    title = stringResource(Res.string.group_members_unavailable_title),
                    body = stringResource(Res.string.group_members_unavailable_body),
                    action = stringResource(Res.string.group_members_retry),
                    onAction = onRetry,
                )
            }

            GroupMembersContent.Forbidden -> {
                MembersMessage(
                    title = stringResource(Res.string.group_members_forbidden_title),
                    body = stringResource(Res.string.group_members_forbidden_body),
                    action = stringResource(Res.string.group_members_return_to_detail),
                    onAction = onBack,
                )
            }

            GroupMembersContent.NotFound -> {
                MembersMessage(
                    title = stringResource(Res.string.group_members_not_found_title),
                    body = stringResource(Res.string.group_members_not_found_body),
                    action = stringResource(Res.string.group_members_return_to_detail),
                    onAction = onBack,
                )
            }

            is GroupMembersContent.Ready -> {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "members-caption") {
                        Text(
                            text = stringResource(Res.string.group_members_view_order),
                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    itemsIndexed(
                        items = content.items,
                        key = { index, _ -> "${content.generation}:$index" },
                    ) { _, member ->
                        GroupMemberRow(member)
                    }
                    if (content.hasNextPage) {
                        item(key = "members-next-page") {
                            when {
                                content.isLoadingMore -> {
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularLoadingIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = AppColors.TextSecondary,
                                            strokeWidth = 2.dp,
                                        )
                                        Text(
                                            text = stringResource(Res.string.group_members_loading_more),
                                            modifier = Modifier.padding(start = 10.dp),
                                            color = AppColors.TextSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }

                                content.loadMoreFailed -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.group_members_next_page_error),
                                            color = AppColors.TextSecondary,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        MembersActionButton(
                                            text = stringResource(Res.string.group_members_retry),
                                            onClick = onRetry,
                                        )
                                    }
                                }

                                else -> {
                                    Spacer(Modifier.height(1.dp))
                                }
                            }
                        }
                    }
                }
                LaunchedEffect(
                    content.generation,
                    content.items.size,
                    content.nextCursor,
                    content.isLoadingMore,
                    content.loadMoreFailed,
                ) {
                    if (!content.hasNextPage || content.isLoadingMore || content.loadMoreFailed) {
                        return@LaunchedEffect
                    }
                    snapshotFlow {
                        val layout = listState.layoutInfo
                        val lastVisibleItem = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                        lastVisibleItem to layout.totalItemsCount
                    }.first { (lastVisibleItem, totalItems) ->
                        totalItems > 0 && lastVisibleItem >= totalItems - LOAD_MORE_PREFETCH_ITEMS
                    }
                    onLoadMore()
                }
            }
        }
    }
}

private const val LOAD_MORE_PREFETCH_ITEMS = 2

@Composable
private fun GroupMemberRow(member: GroupMemberUiModel) {
    val shape = RoundedCornerShape(12.dp)
    val nickname = member.displayNickname
    val accessibilityLabel =
        if (member.role == GroupRole.OWNER) {
            stringResource(Res.string.group_members_accessibility_owner_nickname, nickname)
        } else {
            stringResource(Res.string.group_members_accessibility_nickname, nickname)
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBehind {
                    val shadowOffset = 2.dp.toPx()
                    drawRoundRect(
                        color = AppColors.GroupInk,
                        topLeft = Offset(0f, shadowOffset),
                        size = Size(size.width, size.height - shadowOffset),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 2.dp)
                    .clip(shape)
                    .background(Color.White)
                    .border(AppBorders.Standard, AppColors.GroupInk, shape)
                    .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = nickname,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                color = AppColors.GroupInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.role == GroupRole.OWNER) {
                Text(
                    text = stringResource(Res.string.group_members_owner),
                    modifier =
                        Modifier
                            .background(Color(0xFFDDF4EC), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFF417567),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MembersMessage(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = AppColors.GroupInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            color = AppColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        MembersActionButton(text = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MembersActionButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .raisedButtonBorder(RoundedCornerShape(12.dp), interactionSource)
                .background(AppColors.Primary)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = AppColors.GroupInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = notoSansKrFontFamily(),
        )
    }
}

internal fun previewMembers() =
    listOf(
        GroupMemberUiModel("산책하는 하루", GroupRole.MEMBER, "2026-09-01T08:00:00Z"),
        GroupMemberUiModel(null, GroupRole.MEMBER, "2026-09-02T08:00:00Z"),
        GroupMemberUiModel("모두의 기록", GroupRole.OWNER, "2026-09-03T08:00:00Z"),
        GroupMemberUiModel("같은 닉네임", GroupRole.MEMBER, "2026-09-04T08:00:00Z"),
        GroupMemberUiModel("같은 닉네임", GroupRole.MEMBER, "2026-09-05T08:00:00Z"),
        GroupMemberUiModel("  ", GroupRole.MEMBER, "2026-09-06T08:00:00Z"),
        GroupMemberUiModel("오늘도 천천히 걷고 감정을 기록하는 긴 닉네임 예시입니다", GroupRole.MEMBER, "2026-09-07T08:00:00Z"),
    )
