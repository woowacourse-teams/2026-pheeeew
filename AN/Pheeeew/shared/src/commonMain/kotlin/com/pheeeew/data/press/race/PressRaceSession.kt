package com.pheeeew.data.press.race

import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.data.press.race.transport.PressRaceClose
import com.pheeeew.data.press.race.transport.PressRaceConnection
import com.pheeeew.data.press.race.transport.PressRaceTransport
import com.pheeeew.data.press.race.transport.PressRaceUpgradeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** App-scoped owner; route visibility will call setView at the composition root in the next step. */
internal class PressRaceSession(
    private val transport: PressRaceTransport,
    private val tokens: AccessTokenProvider,
    parentScope: CoroutineScope,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(PressRaceSessionState())
    val state = mutableState.asStateFlow()
    private val closed = MutableStateFlow(false)
    private var epoch = 0L
    private var connection: PressRaceConnection? = null
    private var opening: Job? = null
    private var receiver: Job? = null
    private var writer: Job? = null
    private var outgoing: Channel<String>? = null
    private var version: String? = null
    private var awaitingBaseline = true
    private var initializedView: PressRaceView? = null

    init {
        scope.launch {
            for (event in events) {
                if (closed.value) break
                when (event) {
                    is Event.Target -> {
                        target(event.view)
                    }

                    is Event.Opened -> {
                        if (event.epoch != epoch) {
                            dispose(event.connection)
                        } else {
                            connection = event.connection
                            publish { it.copy(phase = PressRacePhase.Initializing) }
                            val view = state.value.view ?: continue
                            startWriter(event.connection, event.epoch)
                            initializedView = view
                            if (send(PressRaceProtocol.initialize(view))) receive(event.connection, event.epoch)
                        }
                    }

                    is Event.Text -> {
                        if (event.epoch == epoch) message(event.text)
                    }

                    is Event.Failed -> {
                        if (event.epoch == epoch) fail(event.failure)
                    }
                }
            }
        }
    }

    /** null means inactive. Repeated requests for an active view are idempotent; failures retry only explicitly. */
    fun setView(view: PressRaceView?) {
        if (!closed.value) events.trySend(Event.Target(view))
    }

    private suspend fun target(view: PressRaceView?) {
        val current = state.value
        if (current.view == view && current.phase != PressRacePhase.Failed) return
        if (view == null) {
            stopConnection()
            publish { it.copy(phase = PressRacePhase.Disconnected, view = null, ready = null, failure = null) }
            return
        }
        publish { it.copy(view = view, failure = null) }
        awaitingBaseline = true
        if (current.phase == PressRacePhase.Ready) {
            send(PressRaceProtocol.changeView(view))
        } else if (current.phase != PressRacePhase.Connecting && current.phase != PressRacePhase.Initializing) {
            startConnection()
        }
        // An in-flight init may use the previous view; ready sends the newest desired view.
    }

    private fun startConnection() {
        val attempt = ++epoch
        version = null
        publish { it.copy(phase = PressRacePhase.Connecting, ready = null, failure = null) }
        opening =
            scope.launch {
                var opened: PressRaceConnection? = null
                var tokenAcquired = false
                try {
                    val token = tokens.accessToken()
                    if (token == null) {
                        events.send(Event.Failed(attempt, PressRaceFailure(PressRaceFailureKind.Authentication)))
                        return@launch
                    }
                    tokenAcquired = true
                    opened = transport.connect(token)
                    events.send(Event.Opened(attempt, opened))
                    opened = null
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    val kind =
                        if (tokenAcquired) {
                            PressRaceFailureKind.Transport
                        } else {
                            PressRaceFailureKind.Authentication
                        }
                    events.trySend(Event.Failed(attempt, PressRaceFailure(kind)))
                } catch (failure: PressRaceUpgradeException) {
                    val kind =
                        if (failure.statusCode == 401 || failure.statusCode == 403) {
                            PressRaceFailureKind.Authentication
                        } else {
                            PressRaceFailureKind.Transport
                        }
                    events.trySend(Event.Failed(attempt, PressRaceFailure(kind, httpStatus = failure.statusCode)))
                } catch (_: Exception) {
                    val kind =
                        if (tokenAcquired) {
                            PressRaceFailureKind.Transport
                        } else {
                            PressRaceFailureKind.Authentication
                        }
                    events.trySend(Event.Failed(attempt, PressRaceFailure(kind)))
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
                is PressRaceMessage.Ready -> {
                    val ready = message.value
                    require(ready.serverGeneration.isNotBlank() && ready.connectionGeneration.isNotBlank())
                    require(ready.counts.values.all { it >= 0L })
                    if (state.value.phase != PressRacePhase.Initializing) return
                    publish { it.copy(phase = PressRacePhase.Ready, ready = ready) }
                    // Also covers a target changed while init was in flight. A baseline is requested once.
                    state.value.view
                        ?.takeIf { it != initializedView }
                        ?.let { send(PressRaceProtocol.changeView(it)) }
                }

                is PressRaceMessage.Snapshot -> {
                    val snapshot = message.value
                    val ready = state.value.ready ?: error("Snapshot before ready")
                    if (snapshot.serverGeneration != ready.serverGeneration ||
                        snapshot.connectionGeneration != ready.connectionGeneration
                    ) {
                        return
                    }
                    if (snapshot.view != state.value.view) return
                    if (awaitingBaseline && !snapshot.baseline) return
                    require(snapshot.activeCount >= 0L)
                    if (version?.let { compareSnapshotVersions(snapshot.snapshotVersion, it) <= 0 } == true) return
                    compareSnapshotVersions(snapshot.snapshotVersion, "0")
                    val previousDate =
                        state.value.snapshot
                            ?.takeIf {
                                it.serverGeneration == ready.serverGeneration &&
                                    it.connectionGeneration == ready.connectionGeneration
                            }?.date ?: ready.date
                    if (snapshot.date < previousDate) return
                    version = snapshot.snapshotVersion
                    awaitingBaseline = false
                    publish { it.copy(snapshot = snapshot) }
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

    private fun startWriter(
        socket: PressRaceConnection,
        attempt: Long,
    ) {
        val queue = Channel<String>(Channel.UNLIMITED)
        outgoing = queue
        writer =
            scope.launch {
                try {
                    for (text in queue) socket.send(text)
                } catch (cancelled: CancellationException) {
                    if (!currentCoroutineContext().isActive) throw cancelled
                    events.trySend(Event.Failed(attempt, PressRaceFailure(PressRaceFailureKind.Transport)))
                } catch (_: Exception) {
                    events.trySend(Event.Failed(attempt, PressRaceFailure(PressRaceFailureKind.Transport)))
                }
            }
    }

    // A connection has one writer; slow sends must not block the actor's deactivate/close handling.
    private fun send(text: String): Boolean = outgoing?.trySend(text)?.isSuccess == true

    private suspend fun fail(failure: PressRaceFailure) {
        stopConnection()
        publish { it.copy(phase = PressRacePhase.Failed, ready = null, failure = failure) }
    }

    private suspend fun stopConnection() {
        epoch++
        opening?.cancelAndJoin()
        opening = null
        outgoing?.close()
        outgoing = null
        writer?.cancelAndJoin()
        writer = null
        receiver?.cancelAndJoin()
        receiver = null
        val previous = connection
        connection = null
        if (previous != null) dispose(previous)
        version = null
        awaitingBaseline = true
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

    private inline fun publish(change: (PressRaceSessionState) -> PressRaceSessionState) {
        mutableState.update { if (it.phase == PressRacePhase.Closed) it else change(it) }
    }

    /** Terminal, idempotent process-resource shutdown, including any in-flight token/connect work. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        mutableState.update {
            it.copy(phase = PressRacePhase.Closed, view = null, ready = null, snapshot = null, failure = null)
        }
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

    private sealed interface Event {
        data class Target(
            val view: PressRaceView?,
        ) : Event

        data class Opened(
            val epoch: Long,
            val connection: PressRaceConnection,
        ) : Event

        data class Text(
            val epoch: Long,
            val text: String,
        ) : Event

        data class Failed(
            val epoch: Long,
            val failure: PressRaceFailure,
        ) : Event
    }
}
