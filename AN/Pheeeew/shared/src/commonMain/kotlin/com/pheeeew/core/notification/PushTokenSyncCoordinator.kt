package com.pheeeew.core.notification

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class PushTokenSyncState {
    WAITING,
    SYNCING,
    SYNCED,
    RETRYING,
    FAILED,
}

enum class PushTokenBackgroundSyncResult {
    SYNCHRONIZED,
    DEFERRED,
    RETRY,
    FAILED,
}

/** All entry points run on the app's main scope. One worker owns persistence and HTTP writes. */
class PushTokenSyncCoordinator(
    private val permissions: NotificationAuthorizationProvider,
    private val repository: PushTokenRepository,
    private val store: PushTokenStore,
    scope: CoroutineScope,
    private val retryDelaysMillis: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 30_000),
) {
    private val events = Channel<Unit>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(PushTokenSyncState.WAITING)
    val state = mutableState.asStateFlow()
    private var generation: Long? = null
    private var foreground = false
    private var connected = false
    private var revision = 0L
    private var syncedRevision = -1L
    private var latestToken: String? = null
    private var receivedSdkToken = false
    private var backgroundSyncRequested = false
    private var loaded = false
    private var storedToken: String? = null

    init {
        require(retryDelaysMillis.all { it > 0 })
        scope.launch {
            for (event in events) {
                var failures = 0
                while (true) {
                    while (events.tryReceive().isSuccess) { /* Coalesce obsolete wake-ups. */ }
                    val version = revision
                    val outcome =
                        try {
                            reconcile()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Outcome.RETRY
                        }
                    if (revision != version) {
                        failures = 0
                        continue
                    }
                    when (outcome) {
                        Outcome.SUCCESS -> {
                            syncedRevision = version
                            mutableState.value = PushTokenSyncState.SYNCED
                            if (!foreground) backgroundSyncRequested = false
                            completions.value = Completion(version, PushTokenBackgroundSyncResult.SYNCHRONIZED)
                            break
                        }

                        Outcome.WAIT -> {
                            mutableState.value = PushTokenSyncState.WAITING
                            completions.value = Completion(version, PushTokenBackgroundSyncResult.DEFERRED)
                            break
                        }

                        Outcome.STOP -> {
                            mutableState.value = PushTokenSyncState.FAILED
                            completions.value = Completion(version, PushTokenBackgroundSyncResult.FAILED)
                            break
                        }

                        Outcome.RETRY -> {
                            val delay = retryDelaysMillis.getOrNull(failures++)
                            if (delay == null) {
                                mutableState.value = PushTokenSyncState.FAILED
                                completions.value = Completion(version, PushTokenBackgroundSyncResult.RETRY)
                                break
                            }
                            mutableState.value = PushTokenSyncState.RETRYING
                            val eventArrived =
                                withTimeoutOrNull(delay) {
                                    events.receive()
                                    true
                                } == true
                            if (eventArrived) failures = 0
                        }
                    }
                }
            }
        }
    }

    fun tokenReceived(token: String) {
        if (token.isBlank()) return
        latestToken = token
        receivedSdkToken = true
        backgroundSyncRequested = true
        wake()
    }

    fun sessionChanged(readyGeneration: Long?) {
        if (generation == readyGeneration) return
        generation = readyGeneration
        wake()
    }

    fun connectivityChanged(isConnected: Boolean) {
        if (connected == isConnected) return
        connected = isConnected
        wake()
    }

    fun onForeground() {
        foreground = true
        backgroundSyncRequested = false
        wake() // The backend requires PUT/DELETE even when the previous value is unchanged.
    }

    fun onBackground() {
        foreground = false
        wake()
    }

    fun permissionChanged() = wake()

    /** A durable platform worker joins the same serialized sync owner and retries on a later run. */
    suspend fun synchronizeInBackground(): PushTokenBackgroundSyncResult =
        awaitSynchronization(requestSynchronization())

    /** Start a fresh attempt, even after a previous attempt exhausted its retries. Main thread only. */
    fun requestSynchronization(): Long {
        backgroundSyncRequested = true
        return wake()
    }

    suspend fun awaitSynchronization(targetRevision: Long): PushTokenBackgroundSyncResult {
        val completion =
            withTimeoutOrNull(BACKGROUND_SYNC_TIMEOUT_MILLIS) {
                completions.first { it.revision >= targetRevision }
            } ?: return PushTokenBackgroundSyncResult.RETRY
        return completion.result
    }

    private val completions = MutableStateFlow(Completion(-1, PushTokenBackgroundSyncResult.DEFERRED))

    private fun wake(): Long {
        revision++
        mutableState.value = PushTokenSyncState.WAITING
        events.trySend(Unit)
        return revision
    }

    private suspend fun persistLatest() {
        if (!loaded) {
            storedToken = store.read()
            if (!receivedSdkToken) latestToken = storedToken
            loaded = true
        }
        while (latestToken != storedToken) {
            val token = latestToken ?: break
            store.write(token)
            storedToken = token
        }
    }

    private suspend fun reconcile(): Outcome {
        // Persist pre-auth and background callbacks too. Never queue a stale PUT/DELETE action.
        persistLatest()
        if ((!foreground && !backgroundSyncRequested) || !connected || generation == null) return Outcome.WAIT
        if (syncedRevision == revision) return Outcome.SUCCESS
        val beforePermission = revision
        val authorization = permissions.authorization()
        if (revision != beforePermission) return Outcome.WAIT
        val result =
            when {
                authorization.allowsNotifications() -> {
                    val token = latestToken ?: return Outcome.WAIT
                    mutableState.value = PushTokenSyncState.SYNCING
                    repository.register(token, generation ?: return Outcome.WAIT)
                }

                authorization == NotificationAuthorization.DENIED -> {
                    mutableState.value = PushTokenSyncState.SYNCING
                    repository.unregister(generation ?: return Outcome.WAIT)
                }

                else -> {
                    return Outcome.WAIT
                }
            }
        return when (result) {
            is ApiResult.Success -> Outcome.SUCCESS
            is ApiResult.Failure -> if (result.reason.retryable()) Outcome.RETRY else Outcome.STOP
        }
    }

    private fun NetworkFailure.retryable(): Boolean =
        when (this) {
            is NetworkFailure.Transport -> true
            is NetworkFailure.HttpStatus -> statusCode == 408 || statusCode == 429 || statusCode in 500..599
            is NetworkFailure.SessionProviderFailed -> details?.reason in setOf("NETWORK", "SERVER", "RATE_LIMITED")
            is NetworkFailure.SessionUnavailable, is NetworkFailure.Contract, is NetworkFailure.Unexpected -> false
        }

    private enum class Outcome { SUCCESS, WAIT, STOP, RETRY }

    private data class Completion(
        val revision: Long,
        val result: PushTokenBackgroundSyncResult,
    )

    private companion object {
        const val BACKGROUND_SYNC_TIMEOUT_MILLIS = 55_000L
    }
}
