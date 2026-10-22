package com.pheeeew.data.press.delivery

import com.pheeeew.data.press.retry.PressRetryPolicy
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.MyDailyPressSnapshot
import com.pheeeew.domain.model.press.MyDailyPressTotals
import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSendResult
import com.pheeeew.domain.press.settlement.PressPendingWrite
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import com.pheeeew.domain.repository.PressSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PressDeliveryCoordinatorTest {
    @Test
    fun `repeated scheduling keeps a single request worker active`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            var sends = 0
            val host = TestHost { _, _ -> PressDeliveryDecision.Continue }
            val sender =
                PressSender {
                    sends++
                    release.await()
                    PressSendResult.Accepted(emptyTotals())
                }
            val coordinator = coordinator(sender, host, this)

            coordinator.scheduleSend(PressDelayMillis.Zero)
            coordinator.scheduleSend(PressDelayMillis.Zero)
            runCurrent()

            assertEquals(1, sends)
            release.complete(Unit)
            runCurrent()
            assertEquals(1, sends)
        }

    @Test
    fun `retry after waits for the server minimum before resending the same batch`() =
        runTest {
            var sends = 0
            val host =
                TestHost { _, _ ->
                    sends++
                    if (sends == 1) {
                        PressDeliveryDecision.Retry(PressDelayMillis(1_500L))
                    } else {
                        PressDeliveryDecision.Continue
                    }
                }
            val sentBatches = mutableListOf<PressBatch>()
            val sender =
                PressSender { batch ->
                    sentBatches += batch
                    PressSendResult.Accepted(emptyTotals())
                }
            val coordinator = coordinator(sender, host, this)

            coordinator.scheduleSend(PressDelayMillis.Zero)
            runCurrent()
            advanceTimeBy(1_499L)
            runCurrent()
            assertEquals(1, sends)

            advanceTimeBy(1L)
            runCurrent()

            assertEquals(2, sends)
            assertEquals(listOf(host.write.pending.batch, host.write.pending.batch), sentBatches)
        }

    private fun coordinator(
        sender: PressSender,
        host: TestHost,
        scope: TestScope,
    ): PressDeliveryCoordinator =
        PressDeliveryCoordinator(
            sender = sender,
            scope = scope,
            retryPolicy = PressRetryPolicy(jitter = { 1.0 }),
            deliveryPolicy = PressDeliveryPolicy(),
            nowMillis = { PressMonotonicMillis(scope.testScheduler.currentTime) },
            host = host,
        )

    private fun emptyTotals() = MyDailyPressTotals(EmotionState.entries.associateWith { 0L }, 0L)

    private class TestHost(
        private val onResult: (PressWriteState, PressSendResult) -> PressDeliveryDecision,
    ) : PressDeliveryHost {
        val write = sampleWrite()
        private var hasOutstandingWrite = true

        override fun canStartDelivery(): Boolean = hasOutstandingWrite

        override suspend fun prepareNextWrite(): PressDeliveryPreparation =
            if (hasOutstandingWrite) PressDeliveryPreparation.Ready(write) else PressDeliveryPreparation.Stop

        override fun onSending(write: PressWriteState) = Unit

        override suspend fun onSendResult(
            write: PressWriteState,
            result: PressSendResult,
        ): PressDeliveryDecision {
            val decision = onResult(write, result)
            if (decision == PressDeliveryDecision.Continue) hasOutstandingWrite = false
            return decision
        }

        override fun onSendCancelled(write: PressWriteState): Boolean = false

        override fun isBatchReadyToFlush(): Boolean = true

        override fun remainingBatchWait(): PressDelayMillis = PressDelayMillis.Zero

        override fun hasOutstandingWrites(): Boolean = hasOutstandingWrite

        override fun onQueueDrained() = Unit

        override fun onDeliveryWorkerFinished(): PressDelayMillis? = null

        fun itHasNoOutstandingWrite() {
            hasOutstandingWrite = false
        }
    }

    private companion object {
        fun sampleWrite(): PressWriteState {
            val baseline = MyDailyPressSnapshot("2026-10-22", EmotionState.entries.associateWith { 0L }, 0L)
            val batch = PressBatch(mapOf(EmotionState.FRUSTRATED to 1))
            return PressWriteState(PressPendingWrite(batch, baseline), PressMonotonicMillis(0L))
        }
    }
}
