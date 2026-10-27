package com.pheeeew.data.press.race

import com.pheeeew.core.network.AccessTokenProvider
import com.pheeeew.data.press.race.transport.PressRaceClose
import com.pheeeew.data.press.race.transport.PressRaceConnection
import com.pheeeew.data.press.race.transport.PressRaceTransport
import com.pheeeew.data.press.race.transport.PressRaceUpgradeException
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** App-scoped connection owner; visibility is delivered once from the composition root. */
internal class PressRaceSession(
    private val transport: PressRaceTransport,
    private val tokens: AccessTokenProvider,
    parentScope: CoroutineScope,
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
    private var initializedView: PressRaceView? = null

    init {
        scope.launch {
            for (event in events) {
                if (closed.value) break
                when (event) {
                    is Event.Refresh -> {
                        if (state.value.view == event.view) {
                            when (state.value.phase) {
                                PressRacePhase.Ready -> {
                                    if (state.value.isAwaitingSnapshot) continue
                                    publish { it.copy(isAwaitingSnapshot = true) }
                                    send(PressRaceProtocol.changeView(event.view))
                                }

                                PressRacePhase.Failed -> {
                                    target(event.view)
                                }

                                else -> {
                                    Unit
                                }
                            }
                        }
                    }

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
                            val pending = inputObserver.value?.pending().orEmpty()
                            if (send(
                                    PressRaceProtocol.initialize(view, pending),
                                )
                            ) {
                                receive(event.connection, event.epoch)
                            }
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

    /** Explicit user refresh; ignored outside the active view and while initialization is in flight. */
    fun refresh(view: PressRaceView) {
        if (!closed.value) events.trySend(Event.Refresh(view))
    }

    private suspend fun target(view: PressRaceView?) {
        val current = state.value
        if (current.view == view && current.phase != PressRacePhase.Failed) return
        if (view == null) {
            stopConnection()
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
        publish { it.copy(view = view, failure = null, isAwaitingSnapshot = true) }
        if (current.phase == PressRacePhase.Ready) {
            send(PressRaceProtocol.changeView(view))
        } else if (current.phase != PressRacePhase.Connecting && current.phase != PressRacePhase.Initializing) {
            startConnection()
        }
        // An in-flight init may use the previous view; ready sends the newest desired view.
    }

    private fun startConnection() {
        val attempt = ++epoch
        snapshots.reset()
        publish { it.copy(phase = PressRacePhase.Connecting, ready = null, failure = null, isAwaitingSnapshot = true) }
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
                    inputObserver.value?.ready(ready)
                    publish { it.copy(phase = PressRacePhase.Ready, ready = ready, isAwaitingSnapshot = true) }
                    // Also covers a target changed while init was in flight. A baseline is requested once.
                    state.value.view
                        ?.takeIf { it != initializedView }
                        ?.let { send(PressRaceProtocol.changeView(it)) }
                }

                is PressRaceMessage.InputResult -> {
                    val ready = state.value.ready ?: return
                    if (state.value.phase != PressRacePhase.Ready || message.server != ready.serverGeneration ||
                        message.connection != ready.connectionGeneration
                    ) {
                        return
                    }
                    inputObserver.value?.result(message.value)
                }

                is PressRaceMessage.Snapshot -> {
                    val snapshot = message.value
                    if (!snapshots.accept(snapshot, state.value)) return
                    publish { it.copy(snapshot = snapshot, isAwaitingSnapshot = false) }
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
        stopConnection()
        publish { it.copy(phase = PressRacePhase.Failed, ready = null, failure = failure, isAwaitingSnapshot = false) }
    }

    private suspend fun stopConnection() {
        epoch++
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

    private inline fun publish(change: (PressRaceSessionState) -> PressRaceSessionState) {
        mutableState.update { if (it.phase == PressRacePhase.Closed) it else change(it) }
    }

    /** Terminal, idempotent process-resource shutdown, including any in-flight token/connect work. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        mutableState.update {
            it.copy(
                phase = PressRacePhase.Closed,
                view = null,
                ready = null,
                snapshot = null,
                failure = null,
                isAwaitingSnapshot = false,
            )
        }
        outgoing.value?.cancel()
        outgoing.value = null
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
        data class Refresh(
            val view: PressRaceView,
        ) : Event

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
