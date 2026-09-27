package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pheeeew.core.audio.rememberEmotionAudioPlayer
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.screens.map.record.group.GroupSelectorContent
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import com.pheeeew.legacy.core.navigation.PredictiveBackEffect

@Composable
fun NearbyEmotionSheet(
    viewModel: NearbyEmotionViewModel,
    onEmotionHidden: (Long) -> Unit,
    onLeaveEmotion: () -> Unit,
    // Connect the report screen (#441) here when it is available.
    onReportEmotion: ((Long) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val player = rememberEmotionAudioPlayer()
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
                    if (viewModel.state.value.visible) player.play(event.id, event.url)
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
    NearbySheetLayout(state.visible, scroll, viewModel::dismiss) { bottomPadding, nestedScroll ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val group = state.groups.find { it.id == state.groupId } ?: ALL_GROUP_OPTION
            NearbyGroupFilter(group, onClick = viewModel::openGroups)
        }

        state.message?.let { NearbyNotice(it, viewModel::clearMessage) }
        playback.error?.let { NearbyNotice(it, player::stop) }
        if (state.loading) {
            Box(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = bottomPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                state = scroll,
                modifier = Modifier.fillMaxWidth().weight(1f).nestedScroll(nestedScroll),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(24.dp),
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
                        busy = emotion.id in state.pendingIds,
                        playing = playback.id == emotion.id,
                        audioLoading = state.audioLoadingId == emotion.id,
                        onSelect = { viewModel.select(emotion.id) },
                        onDismissMenu = { viewModel.select(null) },
                        onReact = { viewModel.react(emotion.id, it) },
                        onBlock = { viewModel.requestBlock(emotion.id) },
                        onReport =
                            onReportEmotion?.let { report ->
                                {
                                    viewModel.select(null)
                                    report(emotion.id)
                                }
                            },
                        onPlay = {
                            if (playback.id ==
                                emotion.id
                            ) {
                                player.stop()
                            } else {
                                player.stop()
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
        Dialog(
            onDismissRequest = viewModel::dismissGroups,
            properties = DialogProperties(usePlatformDefaultWidth = false),
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
                            Text("가입 그룹을 불러오는 중이에요", Modifier.padding(16.dp))
                        } else {
                            TextButton(onClick = viewModel::loadGroups) { Text("그룹을 불러오지 못했어요 · 다시 시도") }
                        }
                    }
                }
            }
        }
    }
    state.blockId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (id !in state.pendingIds) viewModel.requestBlock(null) },
            title = { Text("이 감정을 차단할까요?") },
            text = {
                Column {
                    Text("이 감정 글 하나가 내 지도와 목록에서 숨겨져요.")
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmBlock,
                    enabled = id !in state.pendingIds,
                ) { Text("차단하기") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.requestBlock(null) }, enabled = id !in state.pendingIds) { Text("취소") }
            },
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
    } else if (group.appearance != null) {
        NearbyGroupStamp(group.appearance, size.dp, modifier)
    } else {
        GroupStamp(group.stampLabel, size.dp, modifier)
    }
}
