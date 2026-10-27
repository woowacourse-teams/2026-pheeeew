package com.pheeeew.data.press.race

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.SessionAccessException
import com.pheeeew.data.press.race.transport.PressRaceClose
import com.pheeeew.data.press.race.transport.PressRaceConnection
import com.pheeeew.data.press.race.transport.PressRaceEndpoint
import com.pheeeew.data.press.race.transport.PressRaceTransport
import com.pheeeew.data.press.race.transport.PressRaceUpgradeException
import com.pheeeew.domain.model.emotion.SearchDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** App-scoped connection owner; visibility is delivered once from the composition root. */
internal class PressRaceSession(
    private val transport: PressRaceTransport,
    private val tokens: RecoverableAccessTokenProvider,
    parentScope: CoroutineScope,
    private val reconnectPolicy: PressRaceReconnectPolicy = PressRaceReconnectPolicy(),
    private val diagnostics: PressRaceDiagnostics = PressRaceDiagnostics { _, _ -> },
    private val noticeClock: () -> Long = newPressRaceClock(),
) : PressRaceInputPort {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(PressRaceSessionState())
    override val state = mutableState.asStateFlow()
    private val closed = MutableStateFlow(false)
    private val currentEpoch = MutableStateFlow(0L)
    private var epoch: Long
        get() = currentEpoch.value
        set(value) {
            currentEpoch.value = value
        }
    private var connection: PressRaceConnection? = null
    private var opening: Job? = null
    private var receiver: Job? = null
    private var writer: Job? = null
    private val outgoing = MutableStateFlow<Channel<Outgoing>?>(null)
    private val inputObserver = MutableStateFlow<PressRaceInputObserver?>(null)

    override fun observeInputs(observer: PressRaceInputObserver) {
        check(inputObserver.compareAndSet(null, observer))
    }

    override fun requestRecovery(generation: String) {
        if (!closed.value) events.trySend(Event.RecoverInputs(generation))
    }

    override suspend fun sendInput(
        text: String,
        generation: String,
        beforeAttempt: suspend () -> Boolean,
    ): Boolean {
        val completion = CompletableDeferred<Boolean>()
        val command = Outgoing(text, generation, beforeAttempt, completion)
        if (outgoing.value?.trySend(command)?.isSuccess != true) return false
        return completion.await()
    }

    private val snapshots = PressRaceSnapshotGate()
    private val notices = PressRaceNoticeReducer()
    private var noticeExpiry: Job? = null

    private fun clearNotices() {
        noticeExpiry?.cancel()
        noticeExpiry = null
        notices.reset()
        publish { it.copy(notice = null) }
    }

    private fun noticeFor(snapshot: PressRaceSnapshot): PressRaceNoticeState? {
        val previous = notices.notice?.id
        val next = notices.accept(snapshot, epoch, noticeClock())
        if (previous != next?.id) {
            noticeExpiry?.cancel()
            noticeExpiry = null
            if (next != null) {
                val attempt = epoch
                noticeExpiry =
                    scope.launch {
                        delay((next.expiresAtMillis - noticeClock()).coerceAtLeast(0L))
                        events.send(Event.ExpireNotice(attempt, next.id))
                    }
            }
        }
        return next
    }

    private var initializedView: PressRaceView? = null
    private var endpoint = PressRaceEndpoint.Competition
    private var usedToken: AccessToken? = null
    private var identity: Long? = null
    private var identityInvalid = false
    private var refreshUsed = false
    private var recoveryProbed = false
    private var failures = 0
    private var replacements = 0
    private var reconnect: Job? = null
    private var watchdog: Job? = null
    private var initPending: Set<String> = emptySet()
    private var dateFloor: String? = null

    private fun armWatchdog() {
        watchdog?.cancel()
        val attempt = epoch
        watchdog =
            scope.launch {
                delay(reconnectPolicy.initializationTimeoutMillis)
                events.send(
                    Event.Failed(attempt, PressRaceFailure(PressRaceFailureKind.Transport, code = "INIT_TIMEOUT")),
                )
            }
    }

    init {
        scope.launch {
            for (event in events) {
                if (closed.value) break
                when (event) {
                    is Event.ExpireNotice -> {
                        if (event.epoch == epoch && notices.expire(event.id, noticeClock())) {
                            noticeExpiry = null
                            publish { it.copy(notice = null) }
                        }
                    }

                    is Event.Refresh -> {
                        if (state.value.view == event.view) {
                            when (state.value.phase) {
                                PressRacePhase.Ready -> {
                                    if (state.value.isAwaitingSnapshot) continue
                                    clearNotices()
                                    publish { it.copy(isAwaitingSnapshot = true) }
                                    send(PressRaceProtocol.changeView(event.view))
                                    armWatchdog()
                                }

                                PressRacePhase.Failed, PressRacePhase.Settled -> {
                                    manualRestart()
                                }

                                else -> {
                                    Unit
                                }
                            }
                        }
                    }

                    is Event.Reconnect -> {
                        if (event.epoch == epoch && state.value.view != null) {
                            reconnect = null
                            startConnection(event.rejected)
                        }
                    }

                    is Event.RecoverInputs -> {
                        if (state.value.phase == PressRacePhase.Ready &&
                            state.value.ready?.connectionGeneration == event.generation
                        ) {
                            publish { it.copy(ready = null, isRecovering = true) }
                            stopConnection()
                            startConnection()
                        }
                    }

                    is Event.Target -> {
                        target(event.view)
                    }

                    is Event.Opened -> {
                        if (event.epoch != epoch) {
                            dispose(event.connection)
                        } else {
                            trace(PressRaceDiagnostic.UpgradeOpened)
                            connection = event.connection
                            usedToken = event.token
                            identity = event.token.generation
                            publish { it.copy(phase = PressRacePhase.Initializing) }
                            val view = state.value.view ?: continue
                            startWriter(event.connection, event.epoch)
                            initializedView = view
                            val pending = inputObserver.value?.pending().orEmpty()
                            initPending = pending.map { it.requestId }.toSet()
                            if (send(
                                    PressRaceProtocol.initialize(view, pending),
                                )
                            ) {
                                receive(event.connection, event.epoch)
                                armWatchdog()
                            }
                        }
                    }

                    is Event.Text -> {
                        if (event.epoch == epoch) message(event.text)
                    }

                    is Event.Failed -> {
                        if (event.epoch == epoch) {
                            event.token?.let { usedToken = it }
                            fail(event.failure)
                        }
                    }
                }
            }
        }
    }

    /** Visibility reasons are fixed metadata; recording cannot change session ownership. */
    fun noteVisibility(event: PressRaceDiagnostic) = trace(event)

    /** null means inactive. Repeated requests for an active view are idempotent; failures retry only explicitly. */
    fun setView(view: PressRaceView?) {
        if (!closed.value) events.trySend(Event.Target(view))
    }

    /** Explicit user refresh; ignored outside the active view and while initialization is in flight. */
    fun refresh(view: PressRaceView) {
        if (!closed.value) events.trySend(Event.Refresh(view))
    }

    private suspend fun manualRestart() {
        if (identityInvalid) return
        stopConnection()
        failures = 0
        replacements = 0
        recoveryProbed = false
        endpoint = PressRaceEndpoint.Competition
        // A manual retry does not erase the fact that a refreshed token was rejected.
        startConnection()
    }

    private suspend fun target(view: PressRaceView?) {
        val current = state.value
        if (current.view == view && current.phase !in setOf(PressRacePhase.Failed, PressRacePhase.Settled)) return
        clearNotices()
        if (view == null) {
            trace(PressRaceDiagnostic.Inactive)
            stopConnection()
            endpoint = PressRaceEndpoint.Competition
            recoveryProbed = false
            failures = 0
            replacements = 0
            publish {
                it.copy(
                    phase = PressRacePhase.Disconnected,
                    view = null,
                    ready = null,
                    failure = null,
                    isAwaitingSnapshot = false,
                )
            }
            return
        }
        publish { it.copy(view = view, isAwaitingSnapshot = true) }
        if (current.phase == PressRacePhase.Ready) {
            send(PressRaceProtocol.changeView(view))
            armWatchdog()
        } else if (current.phase == PressRacePhase.Settled) {
            manualRestart()
        } else if (reconnect != null) {
            // One reservation follows the newest desired view; no duplicate connect jobs.
            return
        } else if (current.phase != PressRacePhase.Connecting && current.phase != PressRacePhase.Initializing) {
            if (!identityInvalid) startConnection()
        }
    }

    private fun startConnection(rejected: AccessToken? = null) {
        clearNotices()
        val attempt = ++epoch
        snapshots.reset()
        publish { it.copy(phase = PressRacePhase.Connecting, ready = null, isAwaitingSnapshot = true) }
        val mode = endpoint
        opening =
            scope.launch {
                var opened: PressRaceConnection? = null
                var token: AccessToken? = null
                try {
                    withTimeout(reconnectPolicy.initializationTimeoutMillis) {
                        token = if (rejected != null) tokens.recover(rejected) else tokens.accessToken()
                        val access = token ?: error("Missing token")
                        if (identity?.let { it != access.generation } == true ||
                            rejected?.let { it.generation != access.generation } == true
                        ) {
                            events.send(
                                Event.Failed(
                                    attempt,
                                    PressRaceFailure(PressRaceFailureKind.Authentication, code = "SESSION_CHANGED"),
                                ),
                            )
                            return@withTimeout
                        }
                        if (attempt != epoch || closed.value) return@withTimeout
                        if (rejected != null) trace(PressRaceDiagnostic.TokenRecovered)
                        opened = transport.connect(access, mode)
                        events.send(Event.Opened(attempt, opened!!, access))
                        opened = null
                    }
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    events.trySend(
                        Event.Failed(
                            attempt,
                            PressRaceFailure(
                                PressRaceFailureKind.Transport,
                                code = "CONNECT_TIMEOUT",
                            ),
                            token,
                        ),
                    )
                } catch (failure: SessionAccessException) {
                    events.trySend(
                        Event.Failed(
                            attempt,
                            PressRaceFailure(
                                PressRaceFailureKind.Authentication,
                                code = failure.details.reason.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) },
                            ),
                            token,
                        ),
                    )
                } catch (failure: PressRaceUpgradeException) {
                    val kind =
                        if (failure.statusCode in setOf(401, 403)) {
                            PressRaceFailureKind.Authentication
                        } else {
                            PressRaceFailureKind.Transport
                        }
                    events.trySend(
                        Event.Failed(attempt, PressRaceFailure(kind, httpStatus = failure.statusCode), token),
                    )
                } catch (_: Exception) {
                    val kind =
                        if (token == null) {
                            PressRaceFailureKind.Authentication
                        } else {
                            PressRaceFailureKind.Transport
                        }
                    events.trySend(Event.Failed(attempt, PressRaceFailure(kind), token))
                } finally {
                    opened?.let { dispose(it) }
                }
            }
    }

    private fun receive(
        socket: PressRaceConnection,
        attempt: Long,
    ) {
        receiver =
            scope.launch {
                try {
                    while (true) {
                        val text = socket.receive() ?: break
                        events.send(Event.Text(attempt, text))
                    }
                    events.send(Event.Failed(attempt, closeFailure(socket.awaitCloseReason())))
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    val reason = runCatching { socket.awaitCloseReason() }.getOrNull()
                    events.trySend(Event.Failed(attempt, closeFailure(reason)))
                } catch (_: Exception) {
                    events.trySend(Event.Failed(attempt, PressRaceFailure(PressRaceFailureKind.Transport)))
                }
            }
    }

    private suspend fun message(text: String) {
        try {
            when (val message = PressRaceProtocol.decode(text)) {
                is PressRaceMessage.Recovered -> {
                    acceptBaseline(message.value, recoveryOnly = true)
                }

                is PressRaceMessage.Ready -> {
                    acceptBaseline(message.value, recoveryOnly = false)
                }

                is PressRaceMessage.InputResult -> {
                    val ready = state.value.ready ?: return
                    if (state.value.phase != PressRacePhase.Ready || message.server != ready.serverGeneration ||
                        message.connection != ready.connectionGeneration
                    ) {
                        return
                    }
                    inputObserver.value?.result(message.value)
                    when (message.value.status) {
                        "APPLIED" -> trace(PressRaceDiagnostic.AppliedResultReceived)
                        "REJECTED" -> trace(PressRaceDiagnostic.RejectedResultReceived)
                    }
                }

                is PressRaceMessage.Snapshot -> {
                    val snapshot = message.value
                    if (!snapshots.accept(snapshot, state.value)) return
                    if (snapshot.date > (state.value.ready?.date ?: snapshot.date)) {
                        dateFloor = snapshot.date
                        publish { it.copy(dateFloor = dateFloor, isRecovering = true, ready = null) }
                        stopConnection()
                        endpoint = PressRaceEndpoint.Competition
                        startConnection()
                        return
                    }
                    val notice = noticeFor(snapshot)
                    dateFloor = snapshot.date
                    if (snapshot.baseline) trace(PressRaceDiagnostic.BaselineReceived)
                    watchdog?.cancel()
                    watchdog = null
                    failures = 0
                    refreshUsed = false
                    publish {
                        it.copy(
                            snapshot = snapshot,
                            notice = notice,
                            isAwaitingSnapshot = false,
                            isRecovering = false,
                            failure = null,
                            dateFloor = dateFloor,
                        )
                    }
                }

                is PressRaceMessage.Error -> {
                    val ready = state.value.ready
                    if (ready != null &&
                        (message.server != ready.serverGeneration || message.connection != ready.connectionGeneration)
                    ) {
                        return
                    }
                    val code = message.code.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) }
                    fail(
                        PressRaceFailure(
                            if (code ==
                                "AUTH_EXPIRED"
                            ) {
                                PressRaceFailureKind.Authentication
                            } else {
                                PressRaceFailureKind.Protocol
                            },
                            code = code,
                        ),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail(PressRaceFailure(PressRaceFailureKind.Protocol))
        }
    }

    private suspend fun acceptBaseline(
        ready: PressRaceReady,
        recoveryOnly: Boolean,
    ) {
        require((endpoint == PressRaceEndpoint.RecoveryOnly) == recoveryOnly)
        if (state.value.phase != PressRacePhase.Initializing) return
        require(ready.serverGeneration.isNotBlank() && ready.connectionGeneration.isNotBlank())
        require(ready.date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && SearchDate.parse(ready.date) != null)
        require(dateFloor?.let { ready.date >= it } != false)
        val ids = ready.results.map { it.requestId }
        require(ids.size == ids.distinct().size && ids.toSet() == initPending)
        require(ready.results.all { it.status in setOf("APPLIED", "REJECTED") })
        require(ready.counts.values.all { it >= 0L })
        clearNotices()
        inputObserver.value?.ready(ready)
        dateFloor = ready.date
        if (recoveryOnly) {
            trace(PressRaceDiagnostic.RecoveryOnlySettled)
            stopConnection()
            publish {
                it.copy(
                    phase = PressRacePhase.Settled,
                    ready = null,
                    isAwaitingSnapshot = false,
                    isRecovering = true,
                    dateFloor = dateFloor,
                )
            }
        } else {
            trace(PressRaceDiagnostic.ReadyReceived)
            publish {
                it.copy(
                    phase = PressRacePhase.Ready,
                    ready = ready,
                    isAwaitingSnapshot = true,
                    dateFloor = dateFloor,
                )
            }
            armWatchdog()
            state.value.view
                ?.takeIf { it != initializedView }
                ?.let { send(PressRaceProtocol.changeView(it)) }
        }
    }

    private fun startWriter(
        socket: PressRaceConnection,
        attempt: Long,
    ) {
        val queue = Channel<Outgoing>(32, onUndeliveredElement = { it.completion?.complete(false) })
        outgoing.value = queue
        writer =
            scope.launch {
                try {
                    for (command in queue) {
                        try {
                            val current = state.value
                            val allowed =
                                attempt == epoch && (
                                    command.generation == null ||
                                        (
                                            current.phase == PressRacePhase.Ready &&
                                                current.ready?.connectionGeneration == command.generation
                                        )
                                )
                            if (allowed && (command.beforeAttempt?.invoke() != false)) {
                                if (attempt != epoch || !currentCoroutineContext().isActive) continue
                                socket.send(command.text)
                                command.completion?.complete(true)
                            } else {
                                command.completion?.complete(false)
                            }
                        } finally {
                            command.completion?.complete(false)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    events.trySend(Event.Failed(attempt, PressRaceFailure(PressRaceFailureKind.Transport)))
                } catch (_: Exception) {
                    events.trySend(Event.Failed(attempt, PressRaceFailure(PressRaceFailureKind.Transport)))
                }
            }
    }

    // A connection has one writer; slow sends must not block the actor's deactivate/close handling.
    private fun send(text: String): Boolean {
        val accepted = outgoing.value?.trySend(Outgoing(text))?.isSuccess == true
        if (!accepted) events.trySend(Event.Failed(epoch, PressRaceFailure(PressRaceFailureKind.Transport)))
        return accepted
    }

    private suspend fun fail(failure: PressRaceFailure) {
        val rejected = usedToken
        if (failure.code == "SESSION_CHANGED") identityInvalid = true
        val mode = endpoint
        stopConnection()
        publish {
            it.copy(
                phase = PressRacePhase.Failed,
                ready = null,
                failure = failure,
                isAwaitingSnapshot = false,
                isRecovering = true,
            )
        }
        if (state.value.view == null || closed.value) return
        var refresh: AccessToken? = null
        val expired = failure.httpStatus == 401 || failure.code == "AUTH_EXPIRED"
        if (expired) {
            if (refreshUsed || rejected == null) return
            refreshUsed = true
            refresh = rejected
        } else if (failure.code == "RACE_DISABLED" || failure.httpStatus == 503) {
            if (mode == PressRaceEndpoint.RecoveryOnly || recoveryProbed) return
            if (inputObserver.value?.pending().isNullOrEmpty()) {
                if (failure.code == "RACE_DISABLED") return
            } else {
                recoveryProbed = true
                endpoint = PressRaceEndpoint.RecoveryOnly
            }
        } else if (failure.code == "CONNECTION_REPLACED") {
            if (++replacements > 3) return
        } else if (!reconnectPolicy.retryable(failure)) {
            return
        }
        if (mode == PressRaceEndpoint.RecoveryOnly && failures >= 3) return
        val attempt = epoch
        val wait = reconnectPolicy.delayMillis(++failures)
        trace(PressRaceDiagnostic.RetryScheduled)
        reconnect =
            scope.launch {
                delay(wait)
                events.send(Event.Reconnect(attempt, refresh))
            }
    }

    private suspend fun stopConnection() {
        clearNotices()
        epoch++
        reconnect?.cancel()
        reconnect = null
        watchdog?.cancel()
        watchdog = null
        opening?.cancelAndJoin()
        opening = null
        outgoing.value?.cancel()
        outgoing.value = null
        writer?.cancelAndJoin()
        writer = null
        receiver?.cancelAndJoin()
        receiver = null
        val previous = connection
        connection = null
        if (previous != null) dispose(previous)
        snapshots.reset()
        initializedView = null
    }

    private suspend fun dispose(socket: PressRaceConnection) {
        withContext(NonCancellable) {
            try {
                withTimeoutOrNull(1_000L) { socket.close() }
            } catch (
                _: Exception,
            ) {
                // Continue releasing ownership.
            }
        }
    }

    private fun trace(event: PressRaceDiagnostic) {
        runCatching { diagnostics.record(event, epoch) }
    }

    private inline fun publish(change: (PressRaceSessionState) -> PressRaceSessionState) {
        mutableState.update { if (it.phase == PressRacePhase.Closed) it else change(it) }
    }

    /** Terminal, idempotent process-resource shutdown, including any in-flight token/connect work. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        mutableState.update {
            it.copy(
                phase = PressRacePhase.Closed,
                notice = null,
                view = null,
                ready = null,
                snapshot = null,
                failure = null,
                isAwaitingSnapshot = false,
            )
        }
        outgoing.value?.cancel()
        outgoing.value = null
        currentEpoch.value++
        events.close()
        scope.cancel()
        transport.close()
    }

    private fun closeFailure(reason: PressRaceClose?): PressRaceFailure =
        PressRaceFailure(
            if (reason?.reason ==
                "AUTH_EXPIRED"
            ) {
                PressRaceFailureKind.Authentication
            } else {
                PressRaceFailureKind.Transport
            },
            closeCode = reason?.code,
            code = reason?.reason?.takeIf { it.matches(Regex("[A-Z0-9_]{1,64}")) },
        )

    private data class Outgoing(
        val text: String,
        val generation: String? = null,
        val beforeAttempt: (suspend () -> Boolean)? = null,
        val completion: CompletableDeferred<Boolean>? = null,
    )

    private sealed interface Event {
        data class ExpireNotice(
            val epoch: Long,
            val id: Long,
        ) : Event

        data class RecoverInputs(
            val generation: String,
        ) : Event

        data class Refresh(
            val view: PressRaceView,
        ) : Event

        data class Target(
            val view: PressRaceView?,
        ) : Event

        data class Opened(
            val epoch: Long,
            val connection: PressRaceConnection,
            val token: AccessToken,
        ) : Event

        data class Text(
            val epoch: Long,
            val text: String,
        ) : Event

        data class Reconnect(
            val epoch: Long,
            val rejected: AccessToken?,
        ) : Event

        data class Failed(
            val epoch: Long,
            val failure: PressRaceFailure,
            val token: AccessToken? = null,
        ) : Event
    }
}
