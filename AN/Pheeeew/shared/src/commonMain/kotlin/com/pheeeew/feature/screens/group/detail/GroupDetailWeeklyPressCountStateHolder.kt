package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns the refresh lifecycle for the group's weekly aggregate count. */
internal class GroupDetailWeeklyPressCountStateHolder(
    private val groupId: GroupId,
    private val source: GroupDetailWeeklyPressCountSource,
    private val scope: CoroutineScope,
    private val onStateChanged: (GroupDetailWeeklyPressCountUiState) -> Unit,
) {
    private var requestJob: Job? = null
    private var requestGeneration = 0L
    private var state = GroupDetailWeeklyPressCountUiState()

    fun load(force: Boolean = false) {
        if (requestJob?.isActive == true && !force) return

        val previous = state
        requestJob?.cancel()
        val requestId = ++requestGeneration
        publish(previous.copy(isRefreshing = true, hasRefreshError = false))

        requestJob =
            scope.launch {
                try {
                    val result = source.load(groupId)
                    if (requestId != requestGeneration) return@launch
                    when (result) {
                        is GroupDetailWeeklyPressCountResult.Loaded -> {
                            publish(
                                GroupDetailWeeklyPressCountUiState(
                                    serverTotal = result.total,
                                ),
                            )
                        }

                        GroupDetailWeeklyPressCountResult.Unavailable -> {
                            publish(previous.copy(isRefreshing = false, hasRefreshError = true))
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (requestId == requestGeneration) {
                        publish(previous.copy(isRefreshing = false, hasRefreshError = true))
                    }
                } finally {
                    if (requestId == requestGeneration) requestJob = null
                }
            }
    }

    /** Keeps a press-triggered read from racing and replacing a newer local accepted press. */
    fun pauseForAcceptedPress() {
        if (requestJob?.isActive != true) return
        requestGeneration++
        requestJob?.cancel()
        requestJob = null
        publish(state.copy(isRefreshing = false))
    }

    /** The POST response confirms one press, but only the weekly GET can establish the server baseline. */
    fun recordAcceptedPress() {
        requestGeneration++
        requestJob?.cancel()
        requestJob = null
        publish(
            state.copy(
                locallyConfirmedPresses = state.locallyConfirmedPresses + 1,
                isRefreshing = false,
                hasRefreshError = false,
            ),
        )
        if (state.serverTotal == null) load(force = true)
    }

    fun clear() {
        requestGeneration++
        requestJob?.cancel()
        requestJob = null
    }

    private fun publish(next: GroupDetailWeeklyPressCountUiState) {
        state = next
        onStateChanged(next)
    }
}
