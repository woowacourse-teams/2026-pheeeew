package com.pheeeew.feature.screens.group.detail

import com.pheeeew.feature.screens.group.model.GroupId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns the ranking request lifecycle and translates source results into ranking UI state. */
internal class GroupDetailEmotionRankingStateHolder(
    private val groupId: GroupId,
    private val source: GroupDetailEmotionRankingSource,
    private val scope: CoroutineScope,
    private val onStateChanged: (GroupDetailEmotionRankingUiState) -> Unit,
) {
    private var requestJob: Job? = null
    private var requestGeneration = 0L
    private var state = GroupDetailEmotionRankingUiState()

    fun load(force: Boolean = false) {
        if (requestJob?.isActive == true && !force) return

        val previous = state
        requestJob?.cancel()
        val requestId = ++requestGeneration
        publish(GroupDetailEmotionRankingStateReducer.refreshStarted(previous))

        requestJob =
            scope.launch {
                try {
                    val result = source.load(groupId)
                    if (requestId != requestGeneration) return@launch
                    publish(GroupDetailEmotionRankingStateReducer.resultReceived(previous, result))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (requestId == requestGeneration) {
                        publish(GroupDetailEmotionRankingStateReducer.refreshFailed(previous))
                    }
                } finally {
                    if (requestId == requestGeneration) requestJob = null
                }
            }
    }

    fun clear() {
        requestGeneration += 1
        requestJob?.cancel()
        requestJob = null
    }

    private fun publish(next: GroupDetailEmotionRankingUiState) {
        state = next
        onStateChanged(next)
    }
}
