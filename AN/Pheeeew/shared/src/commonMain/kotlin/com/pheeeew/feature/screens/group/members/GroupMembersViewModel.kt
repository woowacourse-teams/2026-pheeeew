package com.pheeeew.feature.screens.group.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupMembersViewModel(
    private val groupId: GroupId,
    private val source: GroupMembersSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupMembersUiState())
    val uiState = _uiState.asStateFlow()

    private var generation = 0L
    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null
    private val consumedCursors = mutableSetOf<String>()

    init {
        loadFirstPage()
    }

    fun retry() {
        when (val content = _uiState.value.content) {
            is GroupMembersContent.Ready -> {
                if (content.loadMoreFailed) loadNextPage()
            }

            GroupMembersContent.Loading,
            GroupMembersContent.Empty,
            GroupMembersContent.Forbidden,
            GroupMembersContent.NotFound,
            GroupMembersContent.Failed,
            -> {
                loadFirstPage()
            }
        }
    }

    fun loadNextPage() {
        val ready = _uiState.value.content as? GroupMembersContent.Ready ?: return
        val cursor = ready.nextCursor ?: return
        if (ready.isLoadingMore || nextPageJob?.isActive == true || cursor in consumedCursors) return

        val requestGeneration = generation
        _uiState.update { state ->
            val current = state.content as? GroupMembersContent.Ready ?: return@update state
            state.copy(content = current.copy(isLoadingMore = true, loadMoreFailed = false))
        }
        nextPageJob =
            viewModelScope.launch {
                val result = requestPage(cursor)
                if (requestGeneration != generation) return@launch
                _uiState.update { state ->
                    val current = state.content as? GroupMembersContent.Ready ?: return@update state
                    when (result) {
                        is GroupMembersPageResult.Success -> {
                            val nextCursor = result.page.nextCursor
                            if (nextCursor == cursor || nextCursor in consumedCursors) {
                                state.copy(content = current.copy(isLoadingMore = false, loadMoreFailed = true))
                            } else {
                                consumedCursors += cursor
                                val appended = current.items + result.page.items
                                state.copy(
                                    content =
                                        if (appended.isEmpty()) {
                                            GroupMembersContent.Empty
                                        } else {
                                            current.copy(
                                                items = appended,
                                                nextCursor = nextCursor,
                                                isLoadingMore = false,
                                                loadMoreFailed = false,
                                            )
                                        },
                                )
                            }
                        }

                        GroupMembersPageResult.Forbidden -> {
                            state.copy(content = GroupMembersContent.Forbidden)
                        }

                        GroupMembersPageResult.NotFound -> {
                            state.copy(content = GroupMembersContent.NotFound)
                        }

                        GroupMembersPageResult.Unavailable -> {
                            state.copy(content = current.copy(isLoadingMore = false, loadMoreFailed = true))
                        }
                    }
                }
            }
    }

    private fun loadFirstPage() {
        generation += 1
        val requestGeneration = generation
        consumedCursors.clear()
        firstPageJob?.cancel()
        nextPageJob?.cancel()
        nextPageJob = null
        _uiState.value = GroupMembersUiState(GroupMembersContent.Loading)
        firstPageJob =
            viewModelScope.launch {
                val result = requestPage(cursor = null)
                if (requestGeneration != generation) return@launch
                _uiState.value =
                    GroupMembersUiState(
                        content =
                            when (result) {
                                is GroupMembersPageResult.Success -> {
                                    val page = result.page
                                    if (page.items.isEmpty()) {
                                        GroupMembersContent.Empty
                                    } else if (page.nextCursor == null) {
                                        GroupMembersContent.Ready(
                                            items = page.items,
                                            nextCursor = null,
                                            generation = requestGeneration,
                                        )
                                    } else {
                                        GroupMembersContent.Ready(
                                            items = page.items,
                                            nextCursor = page.nextCursor,
                                            generation = requestGeneration,
                                        )
                                    }
                                }

                                GroupMembersPageResult.Forbidden -> {
                                    GroupMembersContent.Forbidden
                                }

                                GroupMembersPageResult.NotFound -> {
                                    GroupMembersContent.NotFound
                                }

                                GroupMembersPageResult.Unavailable -> {
                                    GroupMembersContent.Failed
                                }
                            },
                    )
            }
    }

    private suspend fun requestPage(cursor: String?): GroupMembersPageResult =
        try {
            source.loadPage(groupId, cursor)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            GroupMembersPageResult.Unavailable
        }
}
