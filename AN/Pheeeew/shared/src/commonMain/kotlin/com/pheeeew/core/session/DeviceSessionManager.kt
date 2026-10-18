package com.pheeeew.core.session

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.RecoverableAccessTokenProvider
import com.pheeeew.core.network.SessionAccessException
import com.pheeeew.core.network.SessionFailureDetails
import com.pheeeew.domain.model.device.DeviceAccess
import com.pheeeew.domain.model.device.DeviceSessionFailure
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.repository.device.DeviceSessionRepository
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

/** All callers share the same in-flight success or failure, independent of a caller's cancellation. */
class DeviceSessionManager(
    private val repository: DeviceSessionRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) : RecoverableAccessTokenProvider {
    private val mutex = Mutex()
    private var cached: DeviceAccess? = null
    private var flight: Deferred<DeviceSessionResult>? = null
    private var lastFailure: DeviceSessionResult.Failed? = null
    private var retryAt = 0L
    private val mutableState = MutableStateFlow<DeviceSessionResult?>(null)
    val state: StateFlow<DeviceSessionResult?> = mutableState.asStateFlow()

    suspend fun prepare(): DeviceSessionResult = obtain(null, Mode.LegacyPrepare)

    suspend fun restore(): DeviceSessionResult = obtain(null, Mode.Restore)

    suspend fun register(nickname: String): DeviceSessionResult = obtain(null, Mode.Register(nickname))

    override suspend fun accessToken(): AccessToken = restore().tokenOrThrow()

    override suspend fun recover(rejected: AccessToken): AccessToken {
        val access = obtain(rejected, Mode.Restore).tokenOrThrow()
        if (access.generation != rejected.generation) {
            throw SessionAccessException(SessionFailureDetails(DeviceSessionFailureKind.SESSION_CHANGED.name))
        }
        return access
    }

    private suspend fun obtain(
        rejected: AccessToken?,
        mode: Mode,
    ): DeviceSessionResult {
        scope.coroutineContext.ensureActive()
        val task =
            mutex.withLock {
                cached?.let { access ->
                    if (access.isUsable(now()) &&
                        (rejected == null || rejected.value != access.value || rejected.generation != access.generation)
                    ) {
                        return DeviceSessionResult.Ready(access)
                    }
                }
                flight?.let { return@withLock it }
                if (now() < retryAt) {
                    val rejectedNicknameCanChange =
                        mode is Mode.Register && lastFailure?.reason?.code in setOf("DEVICE-008", "DEVICE-009")
                    if (!rejectedNicknameCanChange) lastFailure?.let { return it }
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
                                }
                            } catch (cancelled: CancellationException) {
                                withContext(NonCancellable) { mutex.withLock { flight = null } }
                                throw cancelled
                            } catch (_: Exception) {
                                DeviceSessionResult.Failed(DeviceSessionFailure(DeviceSessionFailureKind.UNEXPECTED))
                            }
                        mutex.withLock {
                            when (result) {
                                is DeviceSessionResult.Ready -> {
                                    cached = result.access
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
                        }
                        result
                    }.also { flight = it }
            }
        return task.await()
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

        data class Register(
            val nickname: String,
        ) : Mode
    }

    private fun DeviceSessionFailure.details() =
        SessionFailureDetails(kind.name, statusCode, code, retryAtMillis, stage?.name, sdkCode)
}
