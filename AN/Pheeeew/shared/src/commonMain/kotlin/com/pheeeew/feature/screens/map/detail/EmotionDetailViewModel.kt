package com.pheeeew.feature.screens.map.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.domain.model.emotion.EmotionDetailResult
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.repository.EmotionDetailRepository
import com.pheeeew.feature.monitoring.product.ProductMonitoring
import com.pheeeew.feature.monitoring.product.labels
import com.pheeeew.feature.screens.map.monitoring.DetailVisit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface EmotionDetailLoadUiModel {
    data object Closed : EmotionDetailLoadUiModel

    data object Loading : EmotionDetailLoadUiModel

    data class Ready(
        val id: Long,
        val detail: EmotionDetailUiModel,
        val isMine: Boolean,
        val visit: DetailVisit? = null,
    ) : EmotionDetailLoadUiModel

    data class Failed(
        val message: String,
        val canRetry: Boolean,
    ) : EmotionDetailLoadUiModel
}

class EmotionDetailViewModel(
    private val repository: EmotionDetailRepository,
    private val monitoring: Monitoring = NoOpMonitoring,
) : ViewModel() {
    private val telemetry = ProductMonitoring(monitoring, "map")
    private val mutable = MutableStateFlow<EmotionDetailLoadUiModel>(EmotionDetailLoadUiModel.Closed)
    val uiModel = mutable.asStateFlow()
    private var detailVisit: DetailVisit? = null
    private var selectedId: Long? = null
    private var generation = 0L
    private var loadJob: Job? = null
    private val reactionMutations = mutableMapOf<Pair<Long, String>, ReactionMutation>()

    private class ReactionMutation(
        var confirmed: EmotionReactionUiModel,
        var desiredSelected: Boolean,
        var job: Job?,
    )

    fun open(
        id: Long,
        viewId: String = "unattributed",
        source: String = "map",
    ) {
        if (id <= 0) return
        detailVisit?.close()
        detailVisit = DetailVisit(monitoring, id, source, viewId)
        load(id)
    }

    private fun load(id: Long) {
        val visit = detailVisit
        loadJob?.cancel()
        selectedId = id
        val requestGeneration = ++generation
        mutable.value = EmotionDetailLoadUiModel.Loading
        loadJob =
            viewModelScope.launch {
                val observation = visit?.load()
                try {
                    // Finish earlier writes before reopening the same emotion.
                    reactionMutations
                        .filterKeys { it.first == id }
                        .values
                        .mapNotNull { it.job }
                        .forEach { it.join() }
                    val result =
                        try {
                            repository.findById(id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            EmotionDetailResult.Unavailable
                        }
                    val isOwn = (result as? EmotionDetailResult.Success)?.detail?.isMine
                    if (isOwn != null) visit?.ownership(isOwn)
                    observation?.finish(if (result is EmotionDetailResult.Success) "success" else "failed", isOwn)
                    if (requestGeneration != generation) return@launch
                    mutable.value =
                        when (result) {
                            is EmotionDetailResult.Success -> {
                                EmotionDetailLoadUiModel.Ready(
                                    id,
                                    result.detail.toUiModel(),
                                    result.detail.isMine,
                                    visit,
                                )
                            }

                            EmotionDetailResult.NotFound -> {
                                EmotionDetailLoadUiModel.Failed("삭제되었거나 볼 수 없는 감정이에요", false)
                            }

                            EmotionDetailResult.Unauthorized -> {
                                EmotionDetailLoadUiModel.Failed("인증 정보를 확인하지 못했어요", true)
                            }

                            EmotionDetailResult.InvalidResponse, EmotionDetailResult.Unavailable -> {
                                EmotionDetailLoadUiModel.Failed("감정을 불러오지 못했어요", true)
                            }
                        }
                } finally {
                    observation?.finish("cancelled")
                }
            }
    }

    fun toggleReaction(reactionId: String) {
        val id = selectedId ?: return
        val ready = mutable.value as? EmotionDetailLoadUiModel.Ready ?: return
        val type = EmotionReactionType.entries.firstOrNull { it.name == reactionId } ?: return
        val reaction = ready.detail.reactions.firstOrNull { it.id == reactionId } ?: return
        val desired = !reaction.isSelected
        val key = id to reactionId
        val mutation =
            reactionMutations[key] ?: ReactionMutation(reaction, desired, null).also {
                reactionMutations[key] = it
            }
        mutation.desiredSelected = desired
        mutable.value =
            ready.copy(
                detail =
                    ready.detail.copy(
                        reactionError = null,
                        reactions =
                            ready.detail.reactions.map {
                                if (it.id == reactionId) it.withSelection(desired) else it
                            },
                    ),
            )
        if (mutation.job != null) return
        val requestGeneration = generation
        val visitFields = detailVisit?.eventFields().orEmpty()
        mutation.job =
            viewModelScope.launch {
                try {
                    while (mutation.desiredSelected != mutation.confirmed.isSelected) {
                        val requestedSelection = mutation.desiredSelected
                        val success =
                            try {
                                telemetry
                                    .operation(
                                        "emotion_reaction_finished",
                                        visitFields +
                                            labels(
                                                "entry_key" to id.toString(),
                                                "entry_source" to "map",
                                                "action" to if (requestedSelection) "add" else "remove",
                                            ),
                                        "emotion_reaction_started",
                                    ).observe(
                                        { if (it) "success" else "unknown" },
                                    ) { repository.setReactionSelected(id, type, requestedSelection) }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                false
                            }
                        if (!success) {
                            // A transport failure can occur after the server accepted the write.
                            val actual =
                                try {
                                    (repository.findById(id) as? EmotionDetailResult.Success)
                                        ?.detail
                                        ?.reactions
                                        ?.firstOrNull { it.type == type }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                            telemetry.emit(
                                "operation_reconciled",
                                visitFields +
                                    labels(
                                        "operation_kind" to "emotion_reaction",
                                        "entry_key" to id.toString(),
                                        "outcome" to
                                            if (actual ==
                                                null
                                            ) {
                                                "unknown"
                                            } else if (actual.selected ==
                                                requestedSelection
                                            ) {
                                                "state_matches"
                                            } else {
                                                "state_differs"
                                            },
                                    ),
                            )
                            val rollback =
                                actual?.let {
                                    mutation.confirmed.copy(count = it.count, isSelected = it.selected)
                                } ?: mutation.confirmed
                            if (actual != null && actual.selected == requestedSelection) {
                                mutation.confirmed = rollback
                                updateReaction(
                                    id,
                                    requestGeneration,
                                    rollback.withSelection(mutation.desiredSelected),
                                    null,
                                )
                                continue
                            }
                            updateReaction(id, requestGeneration, rollback, "이모지 반응을 저장하지 못했어요. 다시 눌러주세요")
                            break
                        }
                        mutation.confirmed = mutation.confirmed.withSelection(requestedSelection)
                    }
                } finally {
                    reactionMutations.remove(key)
                }
            }
    }

    private fun updateReaction(
        id: Long,
        requestGeneration: Long,
        reaction: EmotionReactionUiModel,
        error: String?,
    ) {
        if (requestGeneration != generation || selectedId != id) return
        val current = mutable.value as? EmotionDetailLoadUiModel.Ready ?: return
        mutable.value =
            current.copy(
                detail =
                    current.detail.copy(
                        reactions = current.detail.reactions.map { if (it.id == reaction.id) reaction else it },
                        reactionError = error,
                    ),
            )
    }

    private fun EmotionReactionUiModel.withSelection(selected: Boolean): EmotionReactionUiModel =
        if (isSelected == selected) {
            this
        } else {
            copy(
                isSelected = selected,
                count = (count + if (selected) 1L else -1L).coerceAtLeast(0L),
            )
        }

    fun retry() {
        selectedId?.let(::load)
    }

    fun dismiss() {
        detailVisit?.close()
        detailVisit = null
        generation++
        loadJob?.cancel()
        selectedId = null
        mutable.value = EmotionDetailLoadUiModel.Closed
    }
}
