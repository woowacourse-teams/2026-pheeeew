package com.pheeeew.data.press.race

import com.pheeeew.core.di.device.PressRaceReceiveMode
import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestTimeoutOverride
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.core.network.RequestKind
import com.pheeeew.domain.model.emotion.SearchDate
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readLineStrict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal interface PressRaceComparisonSource {
    suspend fun poll(
        mode: PressRaceReceiveMode,
        view: PressRaceView,
        onSnapshot: (PressRaceSnapshotResponse) -> Unit,
    ): ApiResult<Unit>

    suspend fun stream(
        view: PressRaceView,
        onSnapshot: (PressRaceSnapshotResponse) -> Unit,
    ): ApiResult<Unit>
}

internal class ApiPressRaceComparisonSource(
    private val requests: ApiRequestExecutor,
) : PressRaceComparisonSource {
    override suspend fun poll(
        mode: PressRaceReceiveMode,
        view: PressRaceView,
        onSnapshot: (PressRaceSnapshotResponse) -> Unit,
    ): ApiResult<Unit> = PressRaceComparisonClient.poll(requests, mode, view, onSnapshot)

    override suspend fun stream(
        view: PressRaceView,
        onSnapshot: (PressRaceSnapshotResponse) -> Unit,
    ): ApiResult<Unit> = PressRaceComparisonClient.stream(requests, view, onSnapshot)
}

/** Small SSE parser that exposes only snapshot data; comments are deliberately discarded. */
internal class PressRaceSseDecoder(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var eventName: String? = null
    private val data = StringBuilder()

    fun acceptLine(line: String): PressRaceSnapshotResponse? =
        when {
            line.isEmpty() -> dispatch()
            line.startsWith(':') -> null
            line.startsWith("event:") -> {
                eventName = line.substringAfter(':').trim()
                null
            }
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substringAfter(':').trimStart())
                null
            }
            else -> null
        }

    fun dispatch(): PressRaceSnapshotResponse? {
        val decoded = if (eventName == "snapshot" && data.isNotEmpty()) {
            json.decodeFromString<PressRaceSnapshotResponse>(data.toString())
        } else {
            null
        }
        eventName = null
        data.clear()
        return decoded
    }
}

/** Neutral snapshot shared by WS, HTTP and SSE projections. It has no WS generations or notices. */
internal data class PressRankingSnapshot(
    val date: String,
    val generatedAt: String,
    val activeCount: Long,
    val view: PressRaceView,
    val me: PressRaceParticipant,
    val nearby: List<PressRaceParticipant>,
    val top100: List<PressRaceParticipant>,
) {
    /** generatedAt can change while the ranking state stays the same (notably for basic polling). */
    fun sameBusinessState(other: PressRankingSnapshot): Boolean =
        copy(generatedAt = "") == other.copy(generatedAt = "")
}

@Serializable
internal data class PressRaceSnapshotResponse(
    val date: String,
    val generatedAt: String,
    val activeCount: Long,
    val view: String,
    val me: PressRaceParticipant,
    val nearby: List<PressRaceParticipant>,
    val top100: List<PressRaceParticipant>,
) {
    fun toRankingSnapshot(expectedView: PressRaceView): PressRankingSnapshot {
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date)) { "Invalid press race snapshot date format" }
        require(SearchDate.parse(date) != null) { "Invalid press race snapshot date" }
        require(view == expectedView.name) { "Press race snapshot view did not match the request" }
        require(generatedAt.isNotBlank()) { "Missing press race snapshot generatedAt" }
        require(activeCount >= 0L) { "Negative active press count" }
        require((nearby + top100 + me).all { it.score >= 0L }) { "Negative participant score" }
        return PressRankingSnapshot(date, generatedAt, activeCount, expectedView, me, nearby, top100)
    }
}

internal data class PressRaceComparisonState(
    val epoch: Long = 0L,
    val view: PressRaceView? = null,
    val phase: PressRacePhase = PressRacePhase.Disconnected,
    val snapshot: PressRankingSnapshot? = null,
    val failureStatus: Int? = null,
    val failureKind: String? = null,
    val retryCount: Int = 0,
    val firstResponseMillis: Long? = null,
    val lastResponseIntervalMillis: Long? = null,
    val lastStateChangeMillis: Long? = null,
    val stateChangeCount: Int = 0,
) {
    val isRecovering: Boolean get() = phase == PressRacePhase.Failed || phase == PressRacePhase.Connecting
}

/**
 * Reads snapshots for one selected build mode. collectLatest cancels and joins the previous
 * request/stream before it handles the replacement target; epoch and view checks also drop late callbacks.
 */
internal class PressRaceComparisonReceiver(
    private val source: PressRaceComparisonSource,
    private val mode: PressRaceReceiveMode,
    parentScope: CoroutineScope,
    private val debugDiagnostics: Boolean,
    private val randomInt: (Int) -> Int = { bound -> Random.nextInt(bound) },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val selectedMode: PressRaceReceiveMode get() = mode

    private val receiverJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + receiverJob + dispatcher)
    private val target = MutableStateFlow<Target?>(null)
    private val mutableState = MutableStateFlow(PressRaceComparisonState())
    val state: StateFlow<PressRaceComparisonState> = mutableState.asStateFlow()
    private var epoch = 0L
    private val closed = MutableStateFlow(false)
    private var dateFloor: String? = null

    init {
        if (mode != PressRaceReceiveMode.WEBSOCKET) {
            scope.launch {
                target.collectLatest { next ->
                    if (next == null) {
                        mutableState.value = mutableState.value.copy(
                            view = null,
                            phase = PressRacePhase.Disconnected,
                            snapshot = null,
                            failureStatus = null,
                            failureKind = null,
                        )
                    } else {
                        receive(next)
                    }
                }
            }
        }
    }

    fun setView(view: PressRaceView?) {
        if (mode == PressRaceReceiveMode.WEBSOCKET) return
        if (closed.value) return
        val previous = target.value
        if (previous?.view == view || (previous == null && view == null)) return
        if (debugDiagnostics) {
            println("[PressRaceReceive] mode=${mode.name} event=view_selected view=${view?.name ?: "NONE"}")
        }
        val nextEpoch = ++epoch
        if (view == null) {
            mutableState.value = mutableState.value.copy(
                epoch = nextEpoch,
                view = null,
                phase = PressRacePhase.Disconnected,
                snapshot = null,
                failureStatus = null,
                failureKind = null,
            )
        } else {
            mutableState.value = mutableState.value.copy(
                epoch = nextEpoch,
                view = view,
                phase = PressRacePhase.Connecting,
                snapshot = mutableState.value.snapshot?.takeIf { it.view == view },
                failureStatus = null,
                failureKind = null,
                firstResponseMillis = null,
                lastResponseIntervalMillis = null,
                lastStateChangeMillis = null,
                stateChangeCount = 0,
            )
        }
        target.value = view?.let { Target(nextEpoch, it) }
    }

    fun refresh(view: PressRaceView) {
        if (mode == PressRaceReceiveMode.WEBSOCKET) return
        if (closed.value || target.value?.view != view) return
        val nextEpoch = ++epoch
        mutableState.value = mutableState.value.copy(
            epoch = nextEpoch,
            phase = PressRacePhase.Connecting,
            failureStatus = null,
            failureKind = null,
            firstResponseMillis = null,
            lastResponseIntervalMillis = null,
            lastStateChangeMillis = null,
            stateChangeCount = 0,
        )
        target.value = Target(nextEpoch, view)
    }

    fun close() {
        if (closed.value) return
        closed.value = true
        epoch++
        target.value = null
        receiverJob.cancel()
        mutableState.value = mutableState.value.copy(
            epoch = epoch,
            view = null,
            phase = PressRacePhase.Closed,
            snapshot = null,
            failureStatus = null,
            failureKind = null,
        )
    }

    private suspend fun receive(target: Target) {
        var errors = 0
        var accepted: PressRankingSnapshot? = mutableState.value.snapshot?.takeIf { it.view == target.view }
        var dateFloorForTarget = dateFloor
        val started = TimeSource.Monotonic.markNow()
        var lastResponseAt: Long? = null
        while (currentCoroutineContext().isActive && !closed.value && this.target.value == target) {
            if (debugDiagnostics) {
                println("[PressRaceReceive] mode=${mode.name} event=request_started view=${target.view.name}")
            }
            updateIfCurrent(target) {
                it.copy(
                    phase =
                        when {
                            errors > 0 -> PressRacePhase.Connecting
                            it.snapshot?.view == target.view -> PressRacePhase.Ready
                            else -> PressRacePhase.Initializing
                        },
                    failureStatus = null,
                    failureKind = null,
                    retryCount = errors,
                )
            }
            val onSnapshot: (PressRaceSnapshotResponse) -> Unit = { response ->
                val result = accept(target, response, accepted, dateFloorForTarget, started, lastResponseAt)
                accepted = result.snapshot
                dateFloorForTarget = result.dateFloor
                result.receivedAtMillis?.let { lastResponseAt = it }
            }
            val result = try {
                when (mode) {
                    PressRaceReceiveMode.CACHE_POLLING -> source.poll(
                        PressRaceReceiveMode.CACHE_POLLING,
                        target.view,
                        onSnapshot,
                    )
                    PressRaceReceiveMode.BASIC_POLLING -> source.poll(
                        PressRaceReceiveMode.BASIC_POLLING,
                        target.view,
                        onSnapshot,
                    )
                    PressRaceReceiveMode.SSE -> source.stream(target.view, onSnapshot)
                    PressRaceReceiveMode.WEBSOCKET -> error("WebSocket mode is owned by PressRaceSession")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateIfCurrent(target) {
                    it.copy(
                        phase = PressRacePhase.Failed,
                        failureStatus = null,
                        failureKind = "NETWORK_OR_CONTRACT",
                        retryCount = errors + 1,
                    )
                }
                errors++
                delay(scheduleRetry(errors, null, "NETWORK_OR_CONTRACT"))
                continue
            }

            when (result) {
                is ApiResult.Success -> {
                    if (mode == PressRaceReceiveMode.SSE) {
                        // A completed SSE stream without a new request reconnects with bounded backoff.
                        updateIfCurrent(target) {
                            it.copy(
                                phase = PressRacePhase.Failed,
                                failureKind = "STREAM_CLOSED",
                                retryCount = errors + 1,
                            )
                        }
                        errors++
                        delay(scheduleRetry(errors, null, "STREAM_CLOSED"))
                    } else {
                        errors = 0
                        // The previous request has fully returned before the next cadence is scheduled.
                        delay(PressRaceComparisonClient.INITIAL_POLL_INTERVAL_MILLIS)
                    }
                }
                is ApiResult.Failure -> {
                    val failure = result.reason
                    val status = (failure as? NetworkFailure.HttpStatus)?.statusCode
                    val kind = failure::class.simpleName ?: "REQUEST_FAILED"
                    updateIfCurrent(target) {
                        it.copy(
                            phase = PressRacePhase.Failed,
                            failureStatus = status,
                            failureKind = kind,
                            retryCount = errors + 1,
                        )
                    }
                    errors++
                    delay(scheduleRetry(errors, status, kind))
                }
            }
        }
    }

    private fun accept(
        target: Target,
        response: PressRaceSnapshotResponse,
        previous: PressRankingSnapshot?,
        dateFloor: String?,
        started: TimeMark,
        lastResponseAt: Long?,
    ): AcceptedSnapshot {
        if (closed.value || this.target.value != target || mutableState.value.epoch != target.epoch) {
            return AcceptedSnapshot(previous, dateFloor, null)
        }
        val snapshot = response.toRankingSnapshot(target.view)
        require(dateFloor?.let { snapshot.date >= it } != false) { "Snapshot date moved behind the accepted date" }
        val elapsed = started.elapsedNow().inWholeMilliseconds
        val interval = lastResponseAt?.let { elapsed - it }
        var stateChanged = false
        var firstResponse = false
        while (true) {
            val old = mutableState.value
            if (closed.value || this.target.value != target || old.epoch != target.epoch) {
                return AcceptedSnapshot(previous, dateFloor, null)
            }
            val oldSnapshot = old.snapshot?.takeIf { it.view == target.view } ?: previous
            stateChanged = oldSnapshot != null && !oldSnapshot.sameBusinessState(snapshot)
            firstResponse = old.firstResponseMillis == null
            val next =
                old.copy(
                    epoch = target.epoch,
                    view = target.view,
                    phase = PressRacePhase.Ready,
                    snapshot = snapshot,
                    failureStatus = null,
                    failureKind = null,
                    retryCount = 0,
                    firstResponseMillis = old.firstResponseMillis ?: elapsed,
                    lastResponseIntervalMillis = interval,
                    lastStateChangeMillis = if (stateChanged) elapsed else old.lastStateChangeMillis,
                    stateChangeCount = old.stateChangeCount + if (stateChanged) 1 else 0,
                )
            if (mutableState.compareAndSet(old, next)) break
        }
        this.dateFloor = maxOf(this.dateFloor.orEmpty(), snapshot.date).takeIf { it.isNotEmpty() }
        if (debugDiagnostics) {
            val event = when {
                firstResponse -> "first_response"
                stateChanged -> "data_changed"
                else -> "snapshot_unchanged"
            }
            println(
                "[PressRaceReceive] mode=${mode.name} event=$event view=${snapshot.view.name} " +
                    "firstResponse=$firstResponse dataChanged=$stateChanged clientElapsedMs=$elapsed " +
                    "clientIntervalMs=$interval generatedAt=${snapshot.generatedAt}",
            )
        }
        return AcceptedSnapshot(snapshot, snapshot.date, elapsed)
    }

    private fun retryDelay(errors: Int): Long {
        val base = (500L shl (errors - 1).coerceIn(0, 4)).coerceAtMost(4_500L)
        return (base + randomInt(501)).coerceIn(500L, 5_000L)
    }

    private fun updateIfCurrent(
        target: Target,
        transform: (PressRaceComparisonState) -> PressRaceComparisonState,
    ): Boolean {
        while (true) {
            val old = mutableState.value
            if (closed.value || this.target.value != target || old.epoch != target.epoch) return false
            if (mutableState.compareAndSet(old, transform(old))) return true
        }
    }

    private fun scheduleRetry(
        attempt: Int,
        status: Int?,
        kind: String,
    ): Long {
        val delayMillis = retryDelay(attempt)
        if (debugDiagnostics) {
            println(
                "[PressRaceReceive] mode=${mode.name} event=retry_scheduled kind=$kind status=$status " +
                    "attempt=$attempt delayMs=$delayMillis",
            )
        }
        return delayMillis
    }

    private data class Target(val epoch: Long, val view: PressRaceView)

    private data class AcceptedSnapshot(
        val snapshot: PressRankingSnapshot?,
        val dateFloor: String?,
        val receivedAtMillis: Long?,
    )
}

/**
 * Shared API mapping. The server cache TTL and publisher interval below are documented defaults/candidates;
 * the deployed development server may override them. Cache `generatedAt` is the server cache creation time,
 * so its activity data can be stale by up to the configured TTL.
 */
internal object PressRaceComparisonClient {
    /** Initial client poll candidate, measured from the completion of the prior request. */
    const val INITIAL_POLL_INTERVAL_MILLIS = 1_000L
    /** Documented default only; the deployed server can override its cache TTL. */
    const val DOCUMENTED_CACHE_TTL_DEFAULT_MILLIS = 1_000L
    /** Documented initial candidate only; this is not observed from a running server. */
    const val DOCUMENTED_SSE_PUBLISHER_CADENCE_DEFAULT_MILLIS = 1_000L
    /** The service sends heartbeat comments every 15 seconds; leave room for scheduling/network variance. */
    const val SSE_SOCKET_TIMEOUT_MILLIS = 45_000L

    suspend fun poll(
        requests: ApiRequestExecutor,
        mode: PressRaceReceiveMode,
        view: PressRaceView,
        onSnapshot: (PressRaceSnapshotResponse) -> Unit,
    ): ApiResult<Unit> {
        require(mode in setOf(PressRaceReceiveMode.CACHE_POLLING, PressRaceReceiveMode.BASIC_POLLING))
        val path =
            if (mode == PressRaceReceiveMode.CACHE_POLLING) {
                "/api/v2/press-race/snapshot"
            } else {
                "/api/v1/press-race/snapshot"
            }
        val result = requests.execute(
            ApiRequest(
                method = HttpMethod.Get,
                path = path,
                kind = RequestKind.READ,
                queryParameters = mapOf("view" to view.name),
                monitoringEndpoint = if (mode == PressRaceReceiveMode.CACHE_POLLING) {
                    "press_race_cache_snapshot"
                } else {
                    "press_race_basic_snapshot"
                },
            ),
        ) { response -> response.body<PressRaceSnapshotResponse>().also(onSnapshot) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
    }

    suspend fun stream(
        requests: ApiRequestExecutor,
        view: PressRaceView,
        onSnapshot: (PressRaceSnapshotResponse) -> Unit,
    ): ApiResult<Unit> = requests.execute(
        ApiRequest(
            method = HttpMethod.Get,
            path = "/api/v1/press-race/stream",
            kind = RequestKind.READ,
            queryParameters = mapOf("view" to view.name),
            monitoringEndpoint = "press_race_sse_stream",
            accept = "text/event-stream",
            timeoutOverride =
                ApiRequestTimeoutOverride(
                    requestMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS,
                    socketMillis = SSE_SOCKET_TIMEOUT_MILLIS,
                ),
        ),
    ) { response ->
        val channel = response.bodyAsChannel()
        val decoder = PressRaceSseDecoder(json)
        while (true) {
            val line = channel.readLineStrict(limit = 64L * 1024L) ?: break
            decoder.acceptLine(line)?.let(onSnapshot)
        }
        decoder.dispatch()?.let(onSnapshot)
    }

    private val json = Json { ignoreUnknownKeys = true }
}
