package com.pheeeew.feature.screens.map.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReaction
import com.pheeeew.domain.repository.emotion.EmotionFailure
import com.pheeeew.domain.repository.emotion.EmotionRepository
import com.pheeeew.domain.repository.emotion.EmotionResult
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.toUiShape
import com.pheeeew.feature.screens.map.record.group.GroupSelectorGroupUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NearbyEmotionViewModel(
    private val repository: EmotionRepository,
    private val groups: GroupStampListRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NearbyEmotionUiState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<NearbyEmotionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var viewport: EmotionBounds? = null
    private var queryBounds: EmotionBounds? = null
    private var pageJob: Job? = null
    private var groupJob: Job? = null
    private var audioJob: Job? = null
    private var generation = 0L
    private var contentVersion = 0L
    private val blocked = mutableSetOf<Long>()
    private val changed = mutableMapOf<Long, NearbyEmotionItemUiModel>()

    fun onViewportChanged(bounds: EmotionBounds) {
        viewport = bounds
        if (state.value.visible && queryBounds == null) {
            queryBounds = bounds
            refresh()
        }
    }

    fun toggle() {
        if (state.value.visible) dismiss() else open()
    }

    fun open() {
        if (state.value.visible) return
        queryBounds = viewport
        mutableState.update { it.copy(visible = true, selectedId = null, message = null) }
        refresh()
        loadGroups()
    }

    fun dismiss() {
        generation++
        pageJob?.cancel()
        audioJob?.cancel()
        mutableState.update {
            it.copy(
                visible = false,
                selectedId = null,
                groupSelectorVisible = false,
                blockId = null,
                audioLoadingId = null,
                loading = false,
                loadingMore = false,
            )
        }
    }

    fun refresh() {
        val bounds =
            queryBounds ?: run {
                mutableState.update { it.copy(error = "지도 영역을 확인하고 있어요. 잠시 후 다시 시도해 주세요.", loading = false) }
                return
            }
        val ticket = ++generation
        pageJob?.cancel()
        audioJob?.cancel()
        changed.clear()
        val groupId = state.value.groupId.takeUnless { it == ALL_GROUPS }
        mutableState.update {
            it.copy(
                items = emptyList(),
                nextCursor = null,
                loading = true,
                loadingMore = false,
                error = null,
                selectedId = null,
                audioLoadingId = null,
                revision = it.revision + 1,
            )
        }
        pageJob =
            viewModelScope.launch {
                val result = repository.firstPage(bounds, groupId)
                if (ticket != generation) return@launch
                applyPage(result, append = false)
            }
    }

    fun refreshCurrentViewport() {
        queryBounds = viewport ?: queryBounds
        refresh()
    }

    fun loadMore() {
        val current = state.value
        if (!current.visible || current.loading || current.loadingMore) return
        val cursor = current.nextCursor ?: return
        val ticket = generation
        mutableState.update { it.copy(loadingMore = true, error = null) }
        pageJob =
            viewModelScope.launch {
                val result = repository.nextPage(cursor)
                if (ticket != generation) return@launch
                applyPage(result, append = true, requestedCursor = cursor)
            }
    }

    private fun applyPage(
        result: EmotionResult<EmotionPage>,
        append: Boolean,
        requestedCursor: String? = null,
    ) {
        mutableState.update { current ->
            when (result) {
                is EmotionResult.Success -> {
                    val page = result.value
                    val items =
                        ((if (append) current.items else emptyList()) + page.items.map { it.toUiModel() })
                            .distinctBy { it.id }
                            .filterNot { it.id in blocked }
                            .map { changed[it.id] ?: it }
                    current.copy(
                        items = items,
                        nextCursor = page.nextCursor?.takeUnless { it == requestedCursor },
                        loading = false,
                        loadingMore = false,
                        error = null,
                    )
                }

                is EmotionResult.Failure -> {
                    current.copy(
                        loading = false,
                        loadingMore = false,
                        error = if (append) "더 불러오지 못했어요. 다시 시도해 주세요." else result.reason.message(),
                    )
                }
            }
        }
    }

    fun select(id: Long?) {
        mutableState.update { it.copy(selectedId = id) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun react(
        id: Long,
        type: EmotionReaction,
    ) {
        val item = state.value.items.find { it.id == id } ?: return
        if (id in state.value.pendingIds) return
        val reaction = item.reactions.first { it.type == type }
        val selected = !reaction.selected
        contentVersion++
        mutableState.update { it.copy(pendingIds = it.pendingIds + id, selectedId = null) }
        viewModelScope.launch {
            when (val result = repository.react(id, type, selected)) {
                is EmotionResult.Success -> {
                    // Confirmed write; a later failed read must not roll this selection back.
                    val updated =
                        item.copy(
                            reactions =
                                item.reactions.map {
                                    if (it.type == type) {
                                        it.copy(
                                            selected = selected,
                                            count = (it.count + if (selected) 1 else -1).coerceAtLeast(0),
                                        )
                                    } else {
                                        it
                                    }
                                },
                        )
                    changed[id] = updated
                    replaceItem(updated)
                    when (val detail = repository.detail(id)) {
                        is EmotionResult.Success -> {
                            changed[id] = detail.value.toUiModel()
                            replaceItem(detail.value.toUiModel())
                        }

                        is EmotionResult.Failure -> {
                            if (detail.reason == EmotionFailure.NOT_FOUND) removeItem(id)
                        }
                    }
                }

                is EmotionResult.Failure -> {
                    if (result.reason == EmotionFailure.NOT_FOUND) removeItem(id)
                    mutableState.update { it.copy(message = result.reason.message()) }
                }
            }
            mutableState.update { it.copy(pendingIds = it.pendingIds - id) }
        }
    }

    private fun replaceItem(item: NearbyEmotionItemUiModel) {
        if (item.id in blocked) return
        mutableState.update { current ->
            current.copy(
                items =
                    current.items.map {
                        if (it.id ==
                            item.id
                        ) {
                            item
                        } else {
                            it
                        }
                    },
            )
        }
    }

    private fun removeItem(id: Long) {
        blocked += id
        changed.remove(id)
        mutableState.update { it.copy(items = it.items.filterNot { item -> item.id == id }, selectedId = null) }
        eventChannel.trySend(NearbyEmotionEvent.Hidden(id))
    }

    fun requestBlock(id: Long?) {
        if (id != null && state.value.items.none { it.id == id && !it.isMine }) return
        mutableState.update { it.copy(blockId = id, selectedId = null, message = null) }
    }

    fun confirmBlock() {
        val id = state.value.blockId ?: return
        if (id in state.value.pendingIds) return
        mutableState.update { it.copy(pendingIds = it.pendingIds + id) }
        viewModelScope.launch {
            when (val result = repository.block(id)) {
                is EmotionResult.Success -> {
                    removeItem(id)
                    mutableState.update { it.copy(blockId = null, message = "이 감정을 차단했어요.") }
                }

                is EmotionResult.Failure -> {
                    mutableState.update { it.copy(message = result.reason.message()) }
                }
            }
            mutableState.update { it.copy(pendingIds = it.pendingIds - id) }
        }
    }

    fun loadGroups() {
        if (groupJob?.isActive == true) return
        mutableState.update { it.copy(groupsLoading = true, groupsError = false) }
        groupJob =
            viewModelScope.launch {
                when (val result = groups.findMine()) {
                    is GroupStampListLoadResult.Loaded -> {
                        val options =
                            listOf(ALL_GROUP_OPTION) +
                                result.groups.map {
                                    GroupSelectorGroupUiModel(
                                        it.id.value,
                                        it.name,
                                        it.stamp.text,
                                        StampAppearanceUiModel(
                                            it.stamp.text,
                                            it.stamp.frame.toUiShape(),
                                            it.stamp.backgroundColor.argb,
                                            it.stamp.textColor.argb,
                                        ),
                                    )
                                }
                        val disappeared = options.none { it.id == state.value.groupId }
                        mutableState.update {
                            it.copy(
                                groups = options,
                                groupsLoading = false,
                                groupId = if (disappeared) ALL_GROUPS else it.groupId,
                            )
                        }
                        if (disappeared && state.value.visible) refresh()
                    }

                    GroupStampListLoadResult.Unavailable -> {
                        mutableState.update { it.copy(groupsLoading = false, groupsError = true) }
                    }
                }
            }
    }

    fun openGroups() {
        val current = state.value
        mutableState.update {
            it.copy(
                groupSelectorVisible = true,
                pendingGroupId = current.groupId,
                dialProgress =
                    current.groups
                        .indexOfFirst { group ->
                            group.id == current.groupId
                        }.coerceAtLeast(0)
                        .toFloat(),
                selectedId = null,
            )
        }
        loadGroups()
    }

    fun dismissGroups() {
        mutableState.update { it.copy(groupSelectorVisible = false) }
    }

    fun dial(progress: Float) {
        mutableState.update { it.copy(dialProgress = progress) }
    }

    fun pendingGroup(group: GroupSelectorGroupUiModel) {
        mutableState.update { it.copy(pendingGroupId = group.id) }
    }

    fun completeGroup(group: GroupSelectorGroupUiModel) {
        if (state.value.groups.none { it.id == group.id }) return
        val changedGroup = state.value.groupId != group.id
        mutableState.update { it.copy(groupId = group.id, groupSelectorVisible = false) }
        if (changedGroup) refresh()
    }

    fun play(id: Long) {
        audioJob?.cancel()
        val item = state.value.items.find { it.id == id } ?: return
        if (item.contentType != EmotionContentType.AUDIO) return
        val ticket = generation
        val version = contentVersion
        val currentAudio = item.audio
        if (currentAudio != null && currentAudio.expiresAt > kotlin.time.Clock.System.now()) {
            mutableState.update { it.copy(selectedId = null, audioLoadingId = null) }
            viewModelScope.launch { eventChannel.send(NearbyEmotionEvent.Play(id, currentAudio.url)) }
            return
        }
        mutableState.update { it.copy(audioLoadingId = id, selectedId = null) }
        audioJob =
            viewModelScope.launch {
                when (val result = repository.detail(id)) {
                    is EmotionResult.Success -> {
                        if (ticket != generation || !state.value.visible || id in blocked) return@launch
                        if (version == contentVersion &&
                            id !in state.value.pendingIds
                        ) {
                            replaceItem(result.value.toUiModel())
                        }
                        val audio = result.value.audio
                        if (audio != null && audio.expiresAt >
                            kotlin.time.Clock.System
                                .now()
                        ) {
                            eventChannel.send(NearbyEmotionEvent.Play(id, audio.url))
                        } else {
                            mutableState.update { it.copy(message = "녹음을 불러오지 못했어요. 다시 시도해 주세요.") }
                        }
                    }

                    is EmotionResult.Failure -> {
                        if (ticket == generation) {
                            if (result.reason == EmotionFailure.NOT_FOUND) removeItem(id)
                            mutableState.update { it.copy(message = result.reason.message()) }
                        }
                    }
                }
                if (ticket == generation) mutableState.update { it.copy(audioLoadingId = null) }
            }
    }
}

sealed interface NearbyEmotionEvent {
    data class Play(
        val id: Long,
        val url: String,
    ) : NearbyEmotionEvent

    data class Hidden(
        val id: Long,
    ) : NearbyEmotionEvent
}

private fun EmotionFailure.message(): String =
    when (this) {
        EmotionFailure.NOT_FOUND -> "삭제되었거나 더 이상 볼 수 없는 감정이에요."
        EmotionFailure.AUTHENTICATION -> "기기 인증을 확인하지 못했어요. 다시 시도해 주세요."
        EmotionFailure.INVALID_REQUEST -> "요청을 처리하지 못했어요. 새로고침해 주세요."
        EmotionFailure.FORBIDDEN -> "이 감정에는 해당 동작을 할 수 없어요."
        EmotionFailure.UNAVAILABLE -> "연결을 확인하고 다시 시도해 주세요."
    }
