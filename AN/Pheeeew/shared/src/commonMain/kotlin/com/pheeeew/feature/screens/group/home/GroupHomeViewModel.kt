package com.pheeeew.feature.screens.group.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 그룹 목록 상태와 조회 수명을 소유합니다. 실제 화면 이동은 소유하지 않습니다. */
class GroupHomeViewModel(
    private val groupListSource: GroupListSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupHomeUiState())
    val uiState = _uiState.asStateFlow()

    private var listJob: Job? = null
    private var requestGeneration = 0L
    private var membershipRevision = 0L
    private var isMembershipDirty = false

    init {
        loadGroups()
    }

    /** 첫 조회 실패 또는 목록 새로고침 실패에서 다시 요청합니다. */
    fun onRetry() {
        refresh()
    }

    /** 이미 진행 중인 일반 조회가 있으면 중복 요청을 만들지 않습니다. */
    fun refresh() {
        if (listJob?.isActive == true) return
        loadGroups()
    }

    /** 생성·참여·탈퇴 성공 뒤 홈이 다시 보일 때 변경된 가입 목록을 읽습니다. */
    fun refreshIfDirty() {
        if (isMembershipDirty) refresh()
    }

    /** 가입 상태가 바뀌었음을 표시하고 진행 중인 이전 목록 응답을 무효화합니다. */
    fun invalidateMembership() {
        requestGeneration += 1
        membershipRevision += 1
        isMembershipDirty = true
        listJob?.cancel()
        listJob = null
        _uiState.update { state ->
            state.copy(refreshStatus = GroupRefreshStatus.Idle)
        }
    }

    /** 탈퇴 성공 뒤 화면에 남은 항목도 즉시 제거합니다. */
    fun removeGroup(groupId: GroupId) {
        _uiState.update { state ->
            when (val content = state.content) {
                is GroupHomeContent.Ready -> {
                    val remainingGroups = content.groups.filterNot { it.id == groupId }
                    state.copy(
                        content =
                            if (remainingGroups.isEmpty()) {
                                GroupHomeContent.Empty
                            } else {
                                GroupHomeContent.Ready(remainingGroups)
                            },
                        refreshStatus = GroupRefreshStatus.Idle,
                    )
                }

                GroupHomeContent.Empty,
                GroupHomeContent.Failed,
                GroupHomeContent.Loading,
                -> {
                    state
                }
            }
        }
    }

    private fun loadGroups() {
        if (listJob?.isActive == true) return

        val requestId = ++requestGeneration
        val requestedRevision = membershipRevision
        val hasSnapshot = _uiState.value.hasSnapshot
        _uiState.update { state ->
            state.copy(
                content = if (hasSnapshot) state.content else GroupHomeContent.Loading,
                refreshStatus =
                    if (hasSnapshot) {
                        GroupRefreshStatus.Refreshing
                    } else {
                        GroupRefreshStatus.Idle
                    },
            )
        }

        listJob =
            viewModelScope.launch {
                try {
                    val result = requestGroups()
                    if (requestId != requestGeneration) return@launch

                    when (result) {
                        is GroupListResult.Success -> {
                            val groups = result.groups.toList()
                            _uiState.update { state ->
                                state.copy(
                                    content =
                                        if (groups.isEmpty()) {
                                            GroupHomeContent.Empty
                                        } else {
                                            GroupHomeContent.Ready(groups)
                                        },
                                    refreshStatus = GroupRefreshStatus.Idle,
                                )
                            }
                            if (requestedRevision == membershipRevision) {
                                isMembershipDirty = false
                            }
                        }

                        GroupListResult.Unavailable -> {
                            _uiState.update { state ->
                                if (state.hasSnapshot) {
                                    state.copy(refreshStatus = GroupRefreshStatus.Failed)
                                } else {
                                    state.copy(
                                        content = GroupHomeContent.Failed,
                                        refreshStatus = GroupRefreshStatus.Idle,
                                    )
                                }
                            }
                        }
                    }
                } finally {
                    if (requestId == requestGeneration) listJob = null
                }
            }
    }

    private suspend fun requestGroups(): GroupListResult =
        try {
            groupListSource.loadGroups()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            GroupListResult.Unavailable
        }
}
