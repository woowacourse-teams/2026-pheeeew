package com.pheeeew.feature.screens.group.home.explore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupExploreUiState(
    val query: String = "",
    val content: GroupExploreContent = GroupExploreContent.NotLoaded,
)

sealed interface GroupExploreContent {
    data object NotLoaded : GroupExploreContent

    data object Loading : GroupExploreContent

    data object Empty : GroupExploreContent

    data object Failed : GroupExploreContent

    data class Ready(
        val items: List<GroupExploreItemUiModel>,
        val nextCursor: String?,
        val isRefreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreFailed: Boolean = false,
        val requiresFirstPageReload: Boolean = false,
    ) : GroupExploreContent {
        init {
            require(items.isNotEmpty()) { "Ready 상태에는 그룹이 한 개 이상 있어야 합니다." }
        }

        val hasNextPage: Boolean
            get() = nextCursor != null
    }
}

/** Owns query debouncing, request freshness, and cursor pagination for the explore tab. */
class GroupExploreStateHolder(
    private val scope: CoroutineScope,
    private val source: GroupExploreSource,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val _uiState = MutableStateFlow(GroupExploreUiState())
    val uiState = _uiState.asStateFlow()

    private var queryGeneration = 0L
    private var activeKeyword: String? = null
    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null

    fun ensureLoaded() {
        if (_uiState.value.content == GroupExploreContent.NotLoaded) {
            beginFirstPage(normalizeGroupExploreKeyword(_uiState.value.query), debounce = false)
        }
    }

    fun onQueryChanged(query: String) {
        if (_uiState.value.query == query) return
        val oldKeyword = normalizeGroupExploreKeyword(_uiState.value.query)
        val newKeyword = normalizeGroupExploreKeyword(query)
        _uiState.update { it.copy(query = query) }
        if (oldKeyword == newKeyword) return
        beginFirstPage(newKeyword, debounce = true)
    }

    fun onSearchSubmitted() {
        beginFirstPage(normalizeGroupExploreKeyword(_uiState.value.query), debounce = false)
    }

    fun retry() {
        when (val content = _uiState.value.content) {
            GroupExploreContent.Failed -> {
                beginFirstPage(normalizeGroupExploreKeyword(_uiState.value.query), debounce = false)
            }

            is GroupExploreContent.Ready -> {
                when {
                    content.requiresFirstPageReload -> refresh()
                    content.loadMoreFailed -> loadNextPage()
                    content.refreshFailed -> refresh()
                }
            }

            GroupExploreContent.Empty,
            GroupExploreContent.Loading,
            GroupExploreContent.NotLoaded,
            -> {
                refresh()
            }
        }
    }

    /** Refresh only an already opened catalog; its next first load is always server-backed. */
    fun onMembershipChanged() {
        if (_uiState.value.content == GroupExploreContent.NotLoaded) return
        refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        if (!force && firstPageJob?.isActive == true) return
        val content = _uiState.value.content
        if (content is GroupExploreContent.Ready) {
            _uiState.update { state ->
                val ready = state.content as? GroupExploreContent.Ready ?: return@update state
                state.copy(content = ready.copy(isRefreshing = true, refreshFailed = false))
            }
            beginFirstPage(normalizeGroupExploreKeyword(_uiState.value.query), debounce = false, keepSnapshot = true)
        } else {
            beginFirstPage(normalizeGroupExploreKeyword(_uiState.value.query), debounce = false)
        }
    }

    fun loadNextPage() {
        val ready = _uiState.value.content as? GroupExploreContent.Ready ?: return
        val cursor = ready.nextCursor ?: return
        if (ready.isRefreshing || ready.isLoadingMore || ready.requiresFirstPageReload ||
            nextPageJob?.isActive == true
        ) {
            return
        }
        val generation = queryGeneration
        _uiState.update { state ->
            val current = state.content as? GroupExploreContent.Ready ?: return@update state
            state.copy(content = current.copy(isLoadingMore = true, loadMoreFailed = false))
        }
        nextPageJob =
            scope.launch {
                val result = requestPage(activeKeyword, cursor)
                if (generation != queryGeneration) return@launch
                _uiState.update { state ->
                    val current = state.content as? GroupExploreContent.Ready ?: return@update state
                    when (result) {
                        is GroupExplorePageResult.Success -> {
                            if (result.page.nextCursor == cursor) {
                                state.copy(content = current.copy(isLoadingMore = false, loadMoreFailed = true))
                            } else {
                                val mergedItems = (current.items + result.page.items).distinctBy { it.group.id }
                                state.copy(
                                    content =
                                        if (mergedItems.isEmpty()) {
                                            GroupExploreContent.Empty
                                        } else {
                                            current.copy(
                                                items = mergedItems,
                                                nextCursor = result.page.nextCursor,
                                                isLoadingMore = false,
                                                loadMoreFailed = false,
                                            )
                                        },
                                )
                            }
                        }

                        GroupExplorePageResult.InvalidCursor -> {
                            state.copy(
                                content =
                                    current.copy(
                                        isLoadingMore = false,
                                        loadMoreFailed = true,
                                        requiresFirstPageReload = true,
                                    ),
                            )
                        }

                        GroupExplorePageResult.Unavailable -> {
                            state.copy(content = current.copy(isLoadingMore = false, loadMoreFailed = true))
                        }
                    }
                }
            }
    }

    private fun beginFirstPage(
        keyword: String?,
        debounce: Boolean,
        keepSnapshot: Boolean = false,
    ) {
        val retainedItemCount =
            (_uiState.value.content as? GroupExploreContent.Ready)?.takeIf { keepSnapshot }?.items?.size ?: 0
        queryGeneration += 1
        val generation = queryGeneration
        activeKeyword = keyword
        firstPageJob?.cancel()
        nextPageJob?.cancel()
        nextPageJob = null
        _uiState.update { state ->
            val retainedContent =
                (state.content as? GroupExploreContent.Ready)
                    ?.takeIf { keepSnapshot }
                    ?.copy(
                        isRefreshing = true,
                        refreshFailed = false,
                        isLoadingMore = false,
                        loadMoreFailed = false,
                    )
            state.copy(
                content = retainedContent ?: GroupExploreContent.Loading,
            )
        }
        firstPageJob =
            scope.launch {
                if (debounce) delay(debounceMillis)
                if (generation != queryGeneration) return@launch
                val result = requestSnapshot(keyword, retainedItemCount, generation)
                if (generation != queryGeneration) return@launch
                _uiState.update { state ->
                    when (result) {
                        is GroupExplorePageResult.Success -> {
                            if (result.page.items.isEmpty()) {
                                state.copy(content = GroupExploreContent.Empty)
                            } else {
                                state.copy(
                                    content =
                                        GroupExploreContent.Ready(
                                            items = result.page.items.distinctBy { it.group.id },
                                            nextCursor = result.page.nextCursor,
                                        ),
                                )
                            }
                        }

                        GroupExplorePageResult.InvalidCursor,
                        GroupExplorePageResult.Unavailable,
                        -> {
                            if (keepSnapshot) {
                                val current = state.content as? GroupExploreContent.Ready
                                if (current != null) {
                                    state.copy(
                                        content =
                                            current.copy(
                                                isRefreshing = false,
                                                refreshFailed = true,
                                                requiresFirstPageReload =
                                                true,
                                            ),
                                    )
                                } else {
                                    state.copy(content = GroupExploreContent.Failed)
                                }
                            } else {
                                state.copy(content = GroupExploreContent.Failed)
                            }
                        }
                    }
                }
            }
    }

    /** Rebuild the loaded range from fresh pages before replacing it, preserving the list anchor. */
    private suspend fun requestSnapshot(
        keyword: String?,
        minimumItems: Int,
        generation: Long,
    ): GroupExplorePageResult {
        val first = requestPage(keyword, cursor = null)
        if (first !is GroupExplorePageResult.Success) return first
        val items =
            first.page.items
                .distinctBy { it.group.id }
                .toMutableList()
        var cursor = first.page.nextCursor
        val visited = mutableSetOf<String>()
        while (items.size < minimumItems && cursor != null) {
            if (generation != queryGeneration) throw CancellationException("Superseded catalog snapshot")
            if (!visited.add(cursor)) return GroupExplorePageResult.InvalidCursor
            val next = requestPage(keyword, cursor)
            if (next !is GroupExplorePageResult.Success) return next
            val merged = (items + next.page.items).distinctBy { it.group.id }
            if (merged.size == items.size && next.page.nextCursor != null) return GroupExplorePageResult.InvalidCursor
            items.clear()
            items.addAll(merged)
            cursor = next.page.nextCursor
        }
        return GroupExplorePageResult.Success(GroupExplorePage(items.toList(), cursor))
    }

    private suspend fun requestPage(
        keyword: String?,
        cursor: String?,
    ): GroupExplorePageResult =
        try {
            source.loadPage(keyword, cursor)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            GroupExplorePageResult.Unavailable
        }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}
