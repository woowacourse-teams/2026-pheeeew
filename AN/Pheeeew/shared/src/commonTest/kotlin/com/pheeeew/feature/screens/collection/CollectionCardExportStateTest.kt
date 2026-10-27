package com.pheeeew.feature.screens.collection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionCardExportStateTest {
    @Test
    fun duplicateRequestsAreBlockedUntilPlatformCompletion() =
        runTest {
            val completion = CompletableDeferred<CollectionImageActionOutcome>()
            var captures = 0
            var saves = 0
            val state =
                CollectionCardExportState(
                    this,
                    CollectionCardImageActions(
                        save = {
                            saves++
                            completion.await()
                        },
                        share = { _, _ -> error("중복 공유 요청은 실행되면 안 됩니다") },
                    ),
                )
            state.export("first", CollectionImageAction.SAVE) {
                captures++
                byteArrayOf(1)
            }
            assertEquals(CollectionCardExportPhase.CAPTURING, state.phase)
            state.export("second", CollectionImageAction.SHARE) { error("중복 캡처") }
            runCurrent()
            assertEquals(CollectionCardExportPhase.SAVING, state.phase)
            state.export("second", CollectionImageAction.SAVE) { error("중복 캡처") }
            state.cancelCapture("first")
            assertEquals(CollectionCardExportPhase.SAVING, state.phase)

            completion.complete(CollectionImageActionOutcome.SUCCEEDED)
            runCurrent()
            assertEquals(1, captures)
            assertEquals(1, saves)
            assertEquals(CollectionCardExportPhase.IDLE, state.phase)
            assertEquals("first", state.result?.cardId)
            assertEquals(CollectionImageActionOutcome.SUCCEEDED, state.result?.outcome)
        }

    @Test
    fun closingDialogCancelsOnlyItsCaptureAndAllowsRetry() =
        runTest {
            val saving = CompletableDeferred<CollectionImageActionOutcome>()
            val state =
                CollectionCardExportState(
                    this,
                    CollectionCardImageActions(save = { saving.await() }, share = { _, _ -> error("사용하지 않음") }),
                )
            state.export("first", CollectionImageAction.SAVE) { awaitCancellation() }
            runCurrent()
            state.cancelCapture("another")
            assertEquals(CollectionCardExportPhase.CAPTURING, state.phase)
            state.cancelCapture("first")
            assertEquals(CollectionCardExportPhase.IDLE, state.phase)
            state.export("second", CollectionImageAction.SAVE) { byteArrayOf(2) }
            runCurrent()
            assertEquals(CollectionCardExportPhase.SAVING, state.phase)
            saving.complete(CollectionImageActionOutcome.SUCCEEDED)
            runCurrent()
            assertEquals("second", state.result?.cardId)
        }

    @Test
    fun captureFailureAndPlatformFailureReleaseBusyState() =
        runTest {
            var saves = 0
            val state =
                CollectionCardExportState(
                    this,
                    CollectionCardImageActions(
                        save = {
                            saves++
                            error("저장 실패")
                        },
                        share = { _, _ -> error("사용하지 않음") },
                    ),
                )
            state.export("card", CollectionImageAction.SAVE) { error("캡처 실패") }
            runCurrent()
            assertEquals(0, saves)
            assertEquals(CollectionImageActionOutcome.CAPTURE_FAILED, state.result?.outcome)
            assertEquals(CollectionCardExportPhase.IDLE, state.phase)
            state.export("card", CollectionImageAction.SAVE) { byteArrayOf(1) }
            assertNull(state.result)
            runCurrent()
            assertEquals(1, saves)
            assertEquals(CollectionImageActionOutcome.FAILED, state.result?.outcome)
            assertEquals(CollectionCardExportPhase.IDLE, state.phase)
            assertEquals(2, state.resultId)
            state.dismissResult()
            assertNull(state.result)
        }

    @Test
    fun userCancellationDoesNotShowFailureMessage() =
        runTest {
            val state =
                CollectionCardExportState(
                    this,
                    CollectionCardImageActions(
                        save = { CollectionImageActionOutcome.CANCELLED },
                        share = { bytes, url ->
                            assertContentEquals(byteArrayOf(1), bytes)
                            assertEquals("https://pheeeew.com", url)
                            CollectionImageActionOutcome.SUCCEEDED
                        },
                    ),
                )
            state.export("card", CollectionImageAction.SAVE) { byteArrayOf(1) }
            runCurrent()
            assertEquals(CollectionCardExportPhase.IDLE, state.phase)
            assertNull(state.result)
            assertEquals(0, state.resultId)
            state.export("card", CollectionImageAction.SHARE) { byteArrayOf(1) }
            runCurrent()
            assertEquals(CollectionImageAction.SHARE, state.result?.action)
        }

    @Test
    fun disposedOwnerIgnoresLatePlatformCompletionAndNewRequests() =
        runTest {
            val completion = CompletableDeferred<CollectionImageActionOutcome>()
            val state =
                CollectionCardExportState(
                    this,
                    CollectionCardImageActions(
                        save = { withContext(NonCancellable) { completion.await() } },
                        share = { _, _ -> error("해제된 상태는 요청을 실행하면 안 됩니다") },
                    ),
                )
            state.export("card", CollectionImageAction.SAVE) { byteArrayOf(1) }
            runCurrent()
            state.dispose()
            state.export("other", CollectionImageAction.SHARE) { error("해제 후 캡처") }
            completion.complete(CollectionImageActionOutcome.SUCCEEDED)
            runCurrent()
            assertEquals(CollectionCardExportPhase.IDLE, state.phase)
            assertNull(state.result)
            assertEquals(0, state.resultId)
        }
}
