package com.pheeeew.feature.screens.group.members

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.feature.screens.group.model.GroupId
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@AssistedInject
class GroupMembersViewModel(
    @Assisted private val groupId: GroupId,
    private val source: GroupMembersSource,
) : ViewModel() {
    @AssistedFactory
    interface Factory : ManualViewModelAssistedFactory {
        fun create(groupId: GroupId): GroupMembersViewModel
    }

    private val _uiState = MutableStateFlow(GroupMembersUiState())
    val uiState = _uiState.asStateFlow()
    private var generation = 0L
    private var requestJob: Job? = null
    private val consumedCursors = mutableSetOf<String>()

    init {
        loadFirstPage()
    }

    fun retry() {
        if (requestJob?.isActive == true) return
        when (val content = _uiState.value.content) {
            is GroupMembersContent.Ready -> {
                when {
                    content.needsCursorRecovery -> loadFirstPage(preserved = content)
                    content.loadMoreFailed -> loadNextPage()
                }
            }

            else -> {
                loadFirstPage()
            }
        }
    }

    fun loadNextPage() {
        val ready = _uiState.value.content as? GroupMembersContent.Ready ?: return
        val cursor = ready.nextCursor ?: return
        if (requestJob?.isActive == true || ready.needsCursorRecovery || ready.isRecovering ||
            ready.isLoadingMore || cursor in consumedCursors
        ) {
            return
        }
        val requestGeneration = generation
        setContent(ready.copy(isLoadingMore = true, loadMoreFailed = false))
        requestJob =
            viewModelScope.launch {
                val result = requestPage(cursor)
                if (requestGeneration != generation) return@launch
                val current = _uiState.value.content as? GroupMembersContent.Ready ?: return@launch
                when (result) {
                    is GroupMembersPageResult.Success -> {
                        val nextCursor = result.page.nextCursor
                        if (nextCursor == cursor || nextCursor in consumedCursors ||
                            (result.page.items.isEmpty() && nextCursor != null)
                        ) {
                            setContent(current.copy(isLoadingMore = false, loadMoreFailed = true))
                        } else {
                            consumedCursors += cursor
                            setContent(
                                current.copy(
                                    items = current.items + result.page.items,
                                    nextCursor = nextCursor,
                                    isLoadingMore = false,
                                    loadMoreFailed = false,
                                ),
                            )
                        }
                    }

                    GroupMembersPageResult.InvalidCursor -> {
                        setContent(
                            current.copy(
                                nextCursor = null,
                                isLoadingMore = false,
                                loadMoreFailed = true,
                                needsCursorRecovery = true,
                            ),
                        )
                    }

                    GroupMembersPageResult.Unavailable -> {
                        setContent(current.copy(isLoadingMore = false, loadMoreFailed = true))
                    }

                    else -> {
                        setContent(terminalContent(result))
                    }
                }
            }
    }

    private fun loadFirstPage(preserved: GroupMembersContent.Ready? = null) {
        if (requestJob?.isActive == true) return
        generation += 1
        val requestGeneration = generation
        setContent(preserved?.copy(isRecovering = true, loadMoreFailed = false) ?: GroupMembersContent.Loading)
        requestJob =
            viewModelScope.launch {
                val result = requestPage(null)
                if (requestGeneration != generation) return@launch
                when (result) {
                    is GroupMembersPageResult.Success -> {
                        val page = result.page
                        if (page.items.isEmpty() && page.nextCursor != null) {
                            firstPageFailed(preserved)
                        } else {
                            consumedCursors.clear()
                            setContent(
                                if (page.items.isEmpty()) {
                                    GroupMembersContent.Empty
                                } else {
                                    GroupMembersContent.Ready(
                                        items = page.items,
                                        nextCursor = page.nextCursor,
                                        generation = requestGeneration,
                                    )
                                },
                            )
                        }
                    }

                    GroupMembersPageResult.InvalidCursor,
                    GroupMembersPageResult.Unavailable,
                    -> {
                        firstPageFailed(preserved)
                    }

                    else -> {
                        consumedCursors.clear()
                        setContent(terminalContent(result))
                    }
                }
            }
    }

    private fun firstPageFailed(preserved: GroupMembersContent.Ready?) {
        setContent(
            preserved?.copy(
                nextCursor = null,
                isRecovering = false,
                isLoadingMore = false,
                needsCursorRecovery = true,
                loadMoreFailed = true,
            ) ?: GroupMembersContent.Failed,
        )
    }

    private fun terminalContent(result: GroupMembersPageResult): GroupMembersContent =
        when (result) {
            GroupMembersPageResult.Forbidden -> GroupMembersContent.Forbidden
            GroupMembersPageResult.NotFound -> GroupMembersContent.NotFound
            GroupMembersPageResult.Unauthorized -> GroupMembersContent.Unauthorized
            else -> GroupMembersContent.Failed
        }

    private fun setContent(content: GroupMembersContent) {
        _uiState.value = GroupMembersUiState(content)
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
