package com.pheeeew.data.press.race

import com.pheeeew.core.di.device.PressRaceReceiveMode
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PressRaceComparisonReceiverTest {
    @Test
    fun snapshotMappingPreservesTieRanksEmptyListsNonparticipantAndOptionalState() {
        val response =
            Json.decodeFromString<PressRaceSnapshotResponse>(
                """
                {
                  "date":"2026-11-08","generatedAt":"2026-11-08T01:02:03Z","activeCount":4,"view":"ALL",
                  "me":{"publicId":null,"nickname":null,"score":0,"rank":null,"participating":false},
                  "nearby":[],
                  "top100":[
                    {"publicId":"a","nickname":"같은 순위","score":4,"rank":1,"participating":true,"representativeState":"ANGRY"},
                    {"publicId":"b","nickname":"같은 순위","score":4,"rank":1,"participating":true}
                  ]
                }
                """.trimIndent(),
            )

        val mapped = response.toRankingSnapshot(PressRaceView.ALL)
        assertEquals("2026-11-08", mapped.date)
        assertEquals("2026-11-08T01:02:03Z", mapped.generatedAt)
        assertEquals(PressRaceView.ALL, mapped.view)
        assertEquals(4L, mapped.activeCount)
        assertNull(mapped.me.publicId)
        assertFalse(mapped.me.participating)
        assertTrue(mapped.nearby.isEmpty())
        assertEquals(listOf<Long?>(1L, 1L), mapped.top100.map { it.rank })
        assertEquals("ANGRY", mapped.top100.first().representativeState)
        assertNull(mapped.top100.last().representativeState)
    }

    @Test
    fun responseRejectsMismatchedViewAndImpossibleDate() {
        val press = response(PressRaceView.PRESS)
        kotlin.test.assertFails { press.toRankingSnapshot(PressRaceView.ALL) }
        kotlin.test.assertFails { press.copy(date = "2026-02-30").toRankingSnapshot(PressRaceView.PRESS) }
    }

    @Test
    fun responseDecodingRequiresViewForPressAndAll() {
        val pressResponseWithoutView = snapshotJson(PressRaceView.PRESS).replace("\"view\":\"PRESS\",", "")
        assertFailsWith<SerializationException> {
            Json.decodeFromString<PressRaceSnapshotResponse>(pressResponseWithoutView)
        }

        val allResponseWithoutView = snapshotJson(PressRaceView.ALL).replace("\"view\":\"ALL\",", "")
        assertFailsWith<SerializationException> {
            Json.decodeFromString<PressRaceSnapshotResponse>(allResponseWithoutView)
        }
    }

    @Test
    fun comparisonStateUsesNeutralRankingProjectionWithoutWsMetadataOrNotices() {
        val snapshot = response(PressRaceView.PRESS).toRankingSnapshot(PressRaceView.PRESS)
        val ranking =
            PressRaceRankingState().updateComparison(
                PressRaceComparisonState(
                    epoch = 1L,
                    view = PressRaceView.PRESS,
                    phase = PressRacePhase.Ready,
                    snapshot = snapshot,
                ),
            )

        assertEquals(snapshot, ranking.ranking(PressRaceView.PRESS))
        assertTrue(ranking.isFresh(PressRaceView.PRESS))
        assertNull(ranking.session.snapshot)
        assertNull(ranking.session.ready)
        assertNull(ranking.session.notice)
    }

    @Test
    fun sseDecoderIgnoresHeartbeatAndOnlyEmitsSnapshotEvents() {
        val decoder = PressRaceSseDecoder()
        assertNull(decoder.acceptLine(":heartbeat"))
        assertNull(decoder.acceptLine(""))
        assertNull(decoder.acceptLine("event: ready"))
        assertNull(decoder.acceptLine("data: {\"unsupported\":true}"))
        assertNull(decoder.acceptLine(""))
        assertNull(decoder.acceptLine("event: snapshot"))
        assertNull(decoder.acceptLine("data: ${snapshotJson(PressRaceView.PRESS)}"))
        assertEquals(PressRaceView.PRESS, decoder.dispatch()?.toRankingSnapshot(PressRaceView.PRESS)?.view)
        assertNull(decoder.dispatch())
    }

    @Test
    fun replacementCancelsAndJoinsOldRequestAndDropsItsLateResponse() = runTest {
        val source = BlockingSource()
        val receiver =
            PressRaceComparisonReceiver(
                source,
                PressRaceReceiveMode.CACHE_POLLING,
                this,
                debugDiagnostics = false,
                randomInt = { 0 },
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        receiver.setView(PressRaceView.PRESS)
        runCurrent()
        assertEquals(1, source.active)

        receiver.setView(PressRaceView.ALL)
        runCurrent()
        assertEquals(1, source.maxActive)
        assertEquals(1, source.active)
        source.callbacks.getValue(PressRaceView.PRESS).single()(response(PressRaceView.PRESS))
        assertNull(receiver.state.value.snapshot)
        source.callbacks.getValue(PressRaceView.ALL).single()(response(PressRaceView.ALL))
        assertEquals(PressRaceView.ALL, receiver.state.value.snapshot?.view)
        assertEquals(PressRacePhase.Ready, receiver.state.value.phase)

        receiver.close()
        runCurrent()
        assertEquals(0, source.active)
    }

    @Test
    fun diagnosticsSeparateFirstResponseFromBusinessStateChanges() = runTest {
        val source = BlockingSource()
        val receiver =
            PressRaceComparisonReceiver(
                source,
                PressRaceReceiveMode.SSE,
                this,
                debugDiagnostics = false,
                randomInt = { 0 },
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        receiver.setView(PressRaceView.PRESS)
        runCurrent()
        val emit = source.callbacks.getValue(PressRaceView.PRESS).single()
        emit(response(PressRaceView.PRESS))
        assertEquals(0, receiver.state.value.stateChangeCount)
        assertTrue(receiver.state.value.firstResponseMillis != null)
        emit(response(PressRaceView.PRESS).copy(generatedAt = "2026-11-08T01:02:04Z"))
        assertEquals(0, receiver.state.value.stateChangeCount)
        emit(response(PressRaceView.PRESS).copy(activeCount = 2))
        assertEquals(1, receiver.state.value.stateChangeCount)
        receiver.close()
        runCurrent()
    }

    @Test
    fun latePreviousDateCannotOverwriteNewerEpochForTheSameView() = runTest {
        val source = BlockingSource()
        val receiver =
            PressRaceComparisonReceiver(
                source,
                PressRaceReceiveMode.CACHE_POLLING,
                this,
                debugDiagnostics = false,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        receiver.setView(PressRaceView.PRESS)
        runCurrent()
        val oldDate = response(PressRaceView.PRESS).copy(date = "2026-11-08")
        receiver.refresh(PressRaceView.PRESS)
        runCurrent()
        val newerDate = response(PressRaceView.PRESS).copy(date = "2026-11-09")
        source.callbacks.getValue(PressRaceView.PRESS).last()(newerDate)
        source.callbacks.getValue(PressRaceView.PRESS).first()(oldDate)
        assertEquals("2026-11-09", receiver.state.value.snapshot?.date)
        receiver.close()
        runCurrent()
    }

    @Test
    fun pollingWaitsOneSecondAfterEachSettledRequestAndNeverOverlaps() = runTest {
        val source = CadenceSource()
        val receiver =
            PressRaceComparisonReceiver(
                source,
                PressRaceReceiveMode.BASIC_POLLING,
                this,
                debugDiagnostics = false,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        receiver.setView(PressRaceView.PRESS)
        runCurrent()
        assertEquals(1, source.calls)
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(1, source.calls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, source.calls)
        assertEquals(1, source.maxActive)
        assertEquals(1_000L, PressRaceComparisonClient.INITIAL_POLL_INTERVAL_MILLIS)
        receiver.close()
        runCurrent()
    }

    @Test
    fun sseRetries429WithBoundedDelayAndNeverFallsBackToPolling() = runTest {
        val source = RateLimitedStreamSource()
        val receiver =
            PressRaceComparisonReceiver(
                source,
                PressRaceReceiveMode.SSE,
                this,
                debugDiagnostics = false,
                randomInt = { 0 },
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        receiver.setView(PressRaceView.PRESS)
        runCurrent()
        assertEquals(1, source.streamCalls)
        assertEquals(0, source.pollCalls)
        assertEquals(429, receiver.state.value.failureStatus)
        advanceTimeBy(499L)
        runCurrent()
        assertEquals(1, source.streamCalls)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, source.streamCalls)
        assertEquals(0, source.pollCalls)
        assertTrue(receiver.state.value.retryCount >= 1)
        receiver.close()
        runCurrent()
    }

    @Test
    fun normallyClosedSseStreamReconnectsAfterBackoff() = runTest {
        val source = ClosedStreamSource()
        val receiver =
            PressRaceComparisonReceiver(
                source,
                PressRaceReceiveMode.SSE,
                this,
                debugDiagnostics = false,
                randomInt = { 0 },
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        receiver.setView(PressRaceView.PRESS)
        runCurrent()
        assertEquals(1, source.streamCalls)
        assertEquals("STREAM_CLOSED", receiver.state.value.failureKind)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(2, source.streamCalls)
        receiver.close()
        runCurrent()
    }

    private fun response(view: PressRaceView) =
        PressRaceSnapshotResponse(
            date = "2026-11-08",
            generatedAt = "2026-11-08T01:02:03Z",
            activeCount = 1,
            view = view.name,
            me = PressRaceParticipant(null, null, 0, null, false),
            nearby = emptyList(),
            top100 = emptyList(),
        )

    private fun snapshotJson(view: PressRaceView) =
        """{"date":"2026-11-08","generatedAt":"2026-11-08T01:02:03Z","activeCount":1,"view":"${view.name}","me":{"publicId":null,"nickname":null,"score":0,"rank":null,"participating":false},"nearby":[],"top100":[]}"""

    private class BlockingSource : PressRaceComparisonSource {
        val callbacks = mutableMapOf<PressRaceView, MutableList<(PressRaceSnapshotResponse) -> Unit>>()
        var active = 0
        var maxActive = 0

        override suspend fun poll(
            mode: PressRaceReceiveMode,
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> = hold(view, onSnapshot)

        override suspend fun stream(
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> = hold(view, onSnapshot)

        private suspend fun hold(
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> {
            callbacks.getOrPut(view) { mutableListOf() }.add(onSnapshot)
            active++
            maxActive = maxOf(maxActive, active)
            try {
                awaitCancellation()
            } finally {
                active--
            }
        }
    }

    private class CadenceSource : PressRaceComparisonSource {
        var calls = 0
        var active = 0
        var maxActive = 0

        override suspend fun poll(
            mode: PressRaceReceiveMode,
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> {
            calls++
            active++
            maxActive = maxOf(maxActive, active)
            try {
                onSnapshot(
                    PressRaceSnapshotResponse(
                        date = "2026-11-08",
                        generatedAt = "2026-11-08T01:02:03Z",
                        activeCount = 1,
                        view = view.name,
                        me = PressRaceParticipant(null, null, 0, null, false),
                        nearby = emptyList(),
                        top100 = emptyList(),
                    ),
                )
                return ApiResult.Success(Unit)
            } finally {
                active--
            }
        }

        override suspend fun stream(
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> = error("Polling mode must not open an SSE stream")
    }

    private class RateLimitedStreamSource : PressRaceComparisonSource {
        var streamCalls = 0
        var pollCalls = 0

        override suspend fun poll(
            mode: PressRaceReceiveMode,
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> {
            pollCalls++
            return ApiResult.Success(Unit)
        }

        override suspend fun stream(
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> {
            streamCalls++
            return ApiResult.Failure(
                NetworkFailure.HttpStatus(429, retryAfter = "0", mutationCertainty = MutationCertainty.NOT_A_MUTATION),
            )
        }
    }

    private class ClosedStreamSource : PressRaceComparisonSource {
        var streamCalls = 0

        override suspend fun poll(
            mode: PressRaceReceiveMode,
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> = error("SSE mode must not poll")

        override suspend fun stream(
            view: PressRaceView,
            onSnapshot: (PressRaceSnapshotResponse) -> Unit,
        ): ApiResult<Unit> {
            streamCalls++
            return ApiResult.Success(Unit)
        }
    }
}
