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

    fun refresh() {
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
        if (ready.isRefreshing || ready.isLoadingMore || nextPageJob?.isActive == true) return
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

                        GroupExplorePageResult.InvalidCursor,
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
                val result = requestPage(keyword, cursor = null)
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
                        GroupExplorePageResult.Unavailable -> {
                            if (keepSnapshot) {
                                val current = state.content as? GroupExploreContent.Ready
                                if (current != null) {
                                    state.copy(content = current.copy(isRefreshing = false, refreshFailed = true))
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
