package com.pheeeew.core.session

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.SessionAccessException
import com.pheeeew.core.network.SessionFailureDetails
import com.pheeeew.domain.model.device.DeviceAccess
import com.pheeeew.domain.model.device.DeviceSessionFailure
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.repository.DeviceSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Session work belongs to the app scope. Matching callers share its success or failure;
 * a missing-device notification waits for different work before rechecking the identity.
 */
class DeviceSessionManager(
    private val repository: DeviceSessionRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) : RecoverableAccessTokenProvider {
    private val mutex = Mutex()
    private var cached: DeviceAccess? = null
    private var cachedNicknameDraft: String? = null
    private var flight: Deferred<DeviceSessionResult>? = null
    private var flightMode: Mode? = null
    private var lastFailure: DeviceSessionResult.Failed? = null
    private var retryAt = 0L
    private val mutableState = MutableStateFlow<DeviceSessionResult?>(null)
    val state: StateFlow<DeviceSessionResult?> = mutableState.asStateFlow()

    suspend fun prepare(): DeviceSessionResult = obtain(null, Mode.LegacyPrepare)

    suspend fun restore(): DeviceSessionResult = obtain(null, Mode.Restore)

    suspend fun register(nickname: String): DeviceSessionResult = obtain(null, Mode.Register(nickname))

    suspend fun saveNicknameDraft(nickname: String?): Boolean {
        while (true) {
            val running =
                mutex.withLock {
                    if (flight == null) {
                        val saved = repository.saveNicknameDraft(nickname)
                        if (saved) cachedNicknameDraft = nickname
                        return saved
                    }
                    flight
                }
            running?.await()
        }
    }

    override suspend fun accessToken(): AccessToken = restore().tokenOrThrow()

    override suspend fun recover(rejected: AccessToken): AccessToken {
        val access = obtain(rejected, Mode.Restore).tokenOrThrow()
        if (access.generation != rejected.generation) {
            throw SessionAccessException(SessionFailureDetails(DeviceSessionFailureKind.SESSION_CHANGED.name))
        }
        return access
    }

    override suspend fun deviceUnavailable(rejected: AccessToken) {
        // The notification belongs to the session, even if the originating HTTP caller is cancelled.
        scope.async { obtain(rejected, Mode.MissingDevice(rejected.generation)) }.await()
    }

    private suspend fun obtain(
        rejected: AccessToken?,
        mode: Mode,
    ): DeviceSessionResult {
        scope.coroutineContext.ensureActive()
        val (task, needsInvalidationAfterFlight) =
            mutex.withLock {
                cached?.let { access ->
                    val canReuse =
                        if (mode is Mode.MissingDevice) {
                            // DEVICE-004 rejects the identity, including its refreshed access tokens.
                            access.generation != mode.generation
                        } else {
                            access.isUsable(now()) &&
                                (
                                    rejected == null || rejected.value != access.value ||
                                        rejected.generation != access.generation
                                )
                        }
                    if (canReuse) {
                        return DeviceSessionResult.Ready(access, cachedNicknameDraft)
                    }
                }
                flight?.let {
                    // Refresh/register and invalidation are different operations. Serialize them,
                    // then recheck the generation instead of losing invalidation in their result.
                    return@withLock it to (mode is Mode.MissingDevice && flightMode != mode)
                }
                if (mode !is Mode.MissingDevice && now() < retryAt) {
                    val manualRegistrationAllowed =
                        mode is Mode.Register &&
                            lastFailure?.reason?.retryAtMillis?.let { now() < it } != true
                    if (!manualRegistrationAllowed) lastFailure?.let { return it }
                }
                // Clear a rejected/expired token before starting work so it cannot escape during refresh.
                cached = null
                scope
                    .async(start = CoroutineStart.LAZY) {
                        val result =
                            try {
                                when (mode) {
                                    Mode.LegacyPrepare -> repository.prepare()
                                    Mode.Restore -> repository.restore()
                                    is Mode.Register -> repository.register(mode.nickname)
                                    is Mode.MissingDevice -> repository.invalidateMissingDevice(mode.generation)
                                }
                            } catch (cancelled: CancellationException) {
                                withContext(NonCancellable) {
                                    mutex.withLock {
                                        flight = null
                                        flightMode = null
                                    }
                                }
                                throw cancelled
                            } catch (_: Exception) {
                                DeviceSessionResult.Failed(DeviceSessionFailure(DeviceSessionFailureKind.UNEXPECTED))
                            }
                        mutex.withLock {
                            when (result) {
                                is DeviceSessionResult.Ready -> {
                                    cached = result.access
                                    cachedNicknameDraft = result.nicknameDraft
                                    lastFailure = null
                                    retryAt = 0
                                }

                                is DeviceSessionResult.Failed -> {
                                    lastFailure = result
                                    retryAt = maxOf(now() + 1000, result.reason.retryAtMillis ?: 0)
                                }

                                is DeviceSessionResult.NeedsRegistration -> {
                                    lastFailure = null
                                    retryAt = 0
                                }
                            }
                            mutableState.value = result
                            flight = null
                            flightMode = null
                        }
                        result
                    }.also {
                        flight = it
                        flightMode = mode
                    } to false
            }
        val result = task.await()
        return if (needsInvalidationAfterFlight) obtain(rejected, mode) else result
    }

    private fun DeviceSessionResult.tokenOrThrow(): AccessToken =
        when (this) {
            is DeviceSessionResult.Ready -> AccessToken(access.value, access.generation)

            is DeviceSessionResult.Failed -> throw SessionAccessException(reason.details())

            is DeviceSessionResult.NeedsRegistration -> throw SessionAccessException(
                SessionFailureDetails(DeviceSessionFailureKind.SESSION_CHANGED.name),
            )
        }

    private sealed interface Mode {
        data object LegacyPrepare : Mode

        data object Restore : Mode

        data class MissingDevice(
            val generation: Long,
        ) : Mode

        data class Register(
            val nickname: String,
        ) : Mode
    }

    private fun DeviceSessionFailure.details() =
        SessionFailureDetails(kind.name, statusCode, code, retryAtMillis, stage?.name, sdkCode)
}
