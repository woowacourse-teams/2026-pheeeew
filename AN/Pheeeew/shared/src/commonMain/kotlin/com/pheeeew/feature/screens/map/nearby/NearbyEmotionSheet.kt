package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.core.audio.rememberEmotionAudioPlayer
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.core.navigation.PredictiveBackEffect
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.map.monitoring.rememberMonitoringForeground
import com.pheeeew.feature.screens.map.record.group.GroupSelectorContent
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.feature.screens.map.record.group.groupSelectorDialogProperties
import com.pheeeew.feature.screens.map.record.noRippleClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_refresh
import pheeeew.shared.generated.resources.app_cancel
import pheeeew.shared.generated.resources.emotion_block_action
import pheeeew.shared.generated.resources.emotion_block_confirm
import pheeeew.shared.generated.resources.emotion_moderation_hidden_notice
import pheeeew.shared.generated.resources.nearby_groups_loading
import pheeeew.shared.generated.resources.nearby_groups_load_error

@Composable
fun NearbyEmotionSheet(
    viewModel: NearbyEmotionViewModel,
    onEmotionHidden: (Long) -> Unit,
    onLeaveEmotion: () -> Unit,
    onOpenEmotionOnMap: (Long, GeoCoordinate?) -> Boolean,
    blockUser: BlockUserUseCase,
    onReportEmotion: (Long, DrawableResource) -> Unit,
    monitoringVisible: Boolean = true,
    onSheetInteraction: () -> Unit = {},
    focusedEmotionId: Long? = null,
) {
    val state by viewModel.state.collectAsState()
    var expanded by remember(state.visible) { mutableStateOf(false) }
    val foreground = rememberMonitoringForeground()
    val contentVisible =
        monitoringVisible && foreground && state.visible && !state.groupSelectorVisible && state.blockId == null
    DisposableEffect(viewModel, contentVisible) {
        viewModel.contentVisibility(contentVisible)
        onDispose { viewModel.contentVisibility(false) }
    }
    val player =
        com.pheeeew.feature.monitoring.product.rememberObservedEmotionPlayer(viewModel.telemetry) {
            viewModel.exploration.viewId
        }
    val playback by player.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
        }
    }
    LaunchedEffect(viewModel, player) {
        viewModel.events.collect { event ->
            when (event) {
                is NearbyEmotionEvent.Play -> {
                    if (viewModel.canPlay(event)) player.play(event.id, event.url)
                }

                is NearbyEmotionEvent.Hidden -> {
                    if (playback.id == event.id) player.stop()
                    onEmotionHidden(event.id)
                }
            }
        }
    }
    LaunchedEffect(state.visible, state.revision) { player.stop() }
    if (state.visible && !state.groupSelectorVisible && state.blockId == null) {
        PredictiveBackEffect(onProgress = {}, onCompleted = viewModel::dismiss, onCancelled = {})
    }
    val scroll = rememberLazyListState()
    LaunchedEffect(state.revision) { scroll.scrollToItem(0) }
    LaunchedEffect(state.contentLoad, contentVisible, state.loading) {
        val load = state.contentLoad ?: return@LaunchedEffect
        if (!contentVisible || state.loading) return@LaunchedEffect
        withFrameNanos { }
        snapshotFlow {
            val layout = scroll.layoutInfo
            val ids = state.items.mapTo(mutableSetOf()) { it.id }
            val count =
                layout.visibleItemsInfo.count { item ->
                    item.key in ids && item.offset < layout.viewportEndOffset &&
                        item.offset + item.size > layout.viewportStartOffset
                }
            // Ignore pre-layout frames; they must not finalize a nonempty page with a spurious zero.
            count.takeIf { layout.viewportEndOffset > layout.viewportStartOffset && (ids.isEmpty() || count > 0) }
        }.filterNotNull().distinctUntilChanged().collect { count -> viewModel.contentPresented(load, count) }
    }
    LaunchedEffect(contentVisible, state.revision, state.items) {
        if (!contentVisible) return@LaunchedEffect
        // Track each item's uninterrupted qualifying interval independently while scrolling.
        val starts = mutableMapOf<Long, kotlin.time.TimeSource.Monotonic.ValueTimeMark>()
        while (true) {
            val layout = scroll.layoutInfo
            val visible =
                layout.visibleItemsInfo
                    .mapNotNull { item ->
                        val id = item.key as? Long ?: return@mapNotNull null
                        val overlap =
                            (
                                minOf(item.offset + item.size, layout.viewportEndOffset) -
                                    maxOf(item.offset, layout.viewportStartOffset)
                            ).coerceAtLeast(0)
                        id.takeIf { item.size > 0 && overlap.toDouble() / item.size >= 0.5 }
                    }.toSet()
            starts.keys.retainAll(visible)
            visible.forEach { id ->
                val start =
                    starts.getOrPut(id) {
                        kotlin.time.TimeSource.Monotonic
                            .markNow()
                    }
                if (start.elapsedNow().inWholeMilliseconds >=
                    1000
                ) {
                    viewModel.exploration.itemVisible(
                        id,
                        isOwn = state.items.firstOrNull { it.id == id }?.isMine,
                        body =
                            state.items.any {
                                it.id == id &&
                                    !it.memo.isNullOrBlank()
                            },
                    )
                }
            }
            kotlinx.coroutines.delay(100)
        }
    }
    val atEnd by remember {
        derivedStateOf {
            val layout = scroll.layoutInfo
            layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 3
        }
    }
    LaunchedEffect(atEnd, state.nextCursor, state.loading, state.loadingMore, state.error) {
        if (atEnd && !state.loading && !state.loadingMore && state.error == null) viewModel.loadMore()
    }
    NearbySheetLayout(
        visible = state.visible,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onDismiss = viewModel::dismiss,
        onInteraction = onSheetInteraction,
    ) {
        val group = state.groups.find { it.id == state.groupId } ?: ALL_GROUP_OPTION
        NearbySheetHeader(
            group = group,
            loading = state.loading,
            onRefresh = viewModel::refreshCurrentViewport,
            onOpenGroups = viewModel::openGroups,
        )

        state.message?.let { NearbyNotice(it, viewModel::clearMessage) }
        playback.error?.let { NearbyNotice(it, player::stop) }
        if (state.loading) {
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                state = scroll,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.items.isEmpty() &&
                    state.error == null
                ) {
                    item {
                        NearbyEmptyState(
                            onLeaveEmotion = {
                                viewModel.dismiss()
                                onLeaveEmotion()
                            },
                            modifier = Modifier.fillParentMaxHeight(),
                        )
                    }
                }
                items(state.items, key = { it.id }) { emotion ->
                    EmotionChatRow(
                        emotion,
                        selected = state.selectedId == emotion.id,
                        focused = focusedEmotionId == emotion.id,
                        busy = emotion.id in state.pendingIds,
                        playing = playback.id == emotion.id,
                        audioLoading = state.audioLoadingId == emotion.id,
                        onOpenOnMap = {
                            expanded = false
                            viewModel.openOnMap(emotion.id, onOpenEmotionOnMap)
                        },
                        onSelect = { viewModel.select(emotion.id) },
                        onDismissMenu = { viewModel.select(null) },
                        onReact = { viewModel.react(emotion.id, it) },
                        onBlock = { viewModel.requestBlock(emotion.id) },
                        onReport = {
                            viewModel.select(null)
                            viewModel.dismiss()
                            onReportEmotion(emotion.id, emotion.state.face)
                        },
                        onPlay = {
                            if (playback.id ==
                                emotion.id
                            ) {
                                player.stop()
                            } else {
                                com.pheeeew.feature.monitoring.product
                                    .stopForReplacement(player)
                                viewModel.play(emotion.id)
                            }
                        },
                    )
                }
                state.error?.let { error ->
                    item {
                        NearbyLoadError(
                            error,
                            state.items.isNotEmpty(),
                            modifier = if (state.items.isEmpty()) Modifier.fillParentMaxHeight() else Modifier,
                        ) {
                            if (state.items.isEmpty()) viewModel.refresh() else viewModel.loadMore()
                        }
                    }
                }
                if (state.loadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
    if (state.groupSelectorVisible) {
        AppDialog(
            onDismissRequest = viewModel::dismissGroups,
            properties = groupSelectorDialogProperties(),
        ) {
            Box(Modifier.fillMaxSize()) {
                GroupSelectorContent(
                    isVisible = true,
                    groups = state.groups,
                    selectedGroupId = state.pendingGroupId,
                    dialProgress = state.dialProgress,
                    onDialProgressChange = viewModel::dial,
                    onDialProgressSettle = viewModel::dial,
                    onSelectedGroupChange = viewModel::pendingGroup,
                    onDismiss = viewModel::dismissGroups,
                    onComplete = viewModel::completeGroup,
                )
                if (state.groupsLoading || state.groupsError) {
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (state.groupsLoading) {
                            Text(stringResource(Res.string.nearby_groups_loading), Modifier.padding(16.dp))
                        } else {
                            TextButton(onClick = viewModel::loadGroups) { Text(stringResource(Res.string.nearby_groups_load_error)) }
                        }
                    }
                }
            }
        }
    }
    state.blockId?.let { id ->
        ConfirmDialog(
            title = stringResource(Res.string.emotion_block_confirm),
            content = stringResource(Res.string.emotion_moderation_hidden_notice),
            confirmText = stringResource(Res.string.emotion_block_action),
            cancelText = stringResource(Res.string.app_cancel),
            onConfirm = { viewModel.confirmBlock(blockUser) },
            onCancel = { viewModel.requestBlock(null) },
        )
    }
}

@Composable
internal fun SelectorStamp(
    group: GroupSelectorGroupUiModel,
    modifier: Modifier = Modifier,
    size: Int = 48,
) {
    if (!group.showStamp) {
        Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
            Text(group.name, color = Color(0xFF252826), fontSize = 16.sp)
        }
    } else if (group.stamp != null) {
        NearbyGroupStamp(group.stamp, size.dp, modifier)
    } else {
        Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
            Text(group.name, color = Color(0xFF252826), fontSize = 16.sp)
        }
    }
}
