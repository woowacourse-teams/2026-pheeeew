package com.pheeeew.feature.screens.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

enum class CollectionCardExportPhase { IDLE, CAPTURING, SAVING, SHARING }

internal class CollectionCardExportState(
    private val scope: CoroutineScope,
    private val imageActions: CollectionCardImageActions,
) {
    var phase by mutableStateOf(CollectionCardExportPhase.IDLE)
        private set
    var result by mutableStateOf<CollectionImageActionResult?>(null)
        private set
    var resultId by mutableIntStateOf(0)
        private set

    private class Operation(
        val cardId: String,
    )

    private var operation: Operation? = null
    private var job: Job? = null
    private var disposed = false
    private var suppressCurrentResult = false

    fun export(
        cardId: String,
        action: CollectionImageAction,
        capture: suspend () -> ByteArray,
    ) {
        if (disposed || operation != null) return
        val current = Operation(cardId)
        operation = current
        suppressCurrentResult = false
        result = null
        phase = CollectionCardExportPhase.CAPTURING
        job =
            scope.launch {
                try {
                    val bytes = capture()
                    ensureActive()
                    phase =
                        when (action) {
                            CollectionImageAction.SAVE -> CollectionCardExportPhase.SAVING
                            CollectionImageAction.SHARE -> CollectionCardExportPhase.SHARING
                        }
                    val outcome =
                        when (action) {
                            CollectionImageAction.SAVE -> imageActions.save(bytes)
                            CollectionImageAction.SHARE -> imageActions.share(bytes, COLLECTION_CARD_SHARE_URL)
                        }
                    ensureActive()
                    complete(current, action, outcome)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    val outcome =
                        if (phase == CollectionCardExportPhase.CAPTURING) {
                            CollectionImageActionOutcome.CAPTURE_FAILED
                        } else {
                            CollectionImageActionOutcome.FAILED
                        }
                    complete(current, action, outcome)
                } finally {
                    if (operation === current) {
                        operation = null
                        phase = CollectionCardExportPhase.IDLE
                        job = null
                    }
                }
            }
    }

    fun cancelCapture(cardId: String) {
        if (operation?.cardId == cardId && phase == CollectionCardExportPhase.CAPTURING) {
            cancelOperation()
        }
    }

    fun dismissResult() {
        result = null
        if (operation != null) suppressCurrentResult = true
    }

    fun dispose() {
        disposed = true
        cancelOperation()
    }

    private fun cancelOperation() {
        operation = null
        phase = CollectionCardExportPhase.IDLE
        job?.cancel()
        job = null
    }

    private fun complete(
        current: Operation,
        action: CollectionImageAction,
        outcome: CollectionImageActionOutcome,
    ) {
        if (disposed || operation !== current || outcome == CollectionImageActionOutcome.CANCELLED) return
        if (!suppressCurrentResult) {
            result = CollectionImageActionResult(current.cardId, action, outcome)
            resultId++
        }
    }
}
