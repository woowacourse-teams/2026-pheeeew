package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.local.device.CredentialRead
import com.pheeeew.data.local.device.DeviceCredentialStorage
import com.pheeeew.data.remote.device.DeviceAttestationDto
import com.pheeeew.data.remote.device.DeviceAttestationPolicy
import com.pheeeew.data.remote.device.DeviceProofException
import com.pheeeew.data.remote.device.DeviceRefreshRequestDto
import com.pheeeew.data.remote.device.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.device.DeviceSessionApi
import com.pheeeew.domain.model.device.DeviceAccess
import com.pheeeew.domain.model.device.DeviceCredentials
import com.pheeeew.domain.model.device.DeviceDiagnosticOutcome
import com.pheeeew.domain.model.device.DevicePlatform
import com.pheeeew.domain.model.device.DeviceSessionDiagnostic
import com.pheeeew.domain.model.device.DeviceSessionDiagnostics
import com.pheeeew.domain.model.device.DeviceSessionFailure
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import com.pheeeew.domain.model.device.DeviceSessionResult
import com.pheeeew.domain.model.device.DeviceSessionStage
import com.pheeeew.domain.model.device.recordSafely
import com.pheeeew.domain.repository.DeviceSessionRepository
import io.ktor.http.fromHttpToGmtDate
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/** Called exclusively by the app's single session owner. */
class DeviceSessionRepositoryImpl(
    private val api: DeviceSessionApi,
    private val storage: DeviceCredentialStorage,
    private val platform: DevicePlatform,
    private val attestationPolicy: DeviceAttestationPolicy,
    private val now: () -> Long,
    private val diagnostics: DeviceSessionDiagnostics = DeviceSessionDiagnostics {},
    private val newRequestId: () -> String = { Uuid.random().toString() },
) : DeviceSessionRepository {
    private var stage = DeviceSessionStage.STORAGE
    private var blockedUntil: Long = 0
    private var blockedFailure: DeviceSessionResult.Failed? = null

    override suspend fun prepare(): DeviceSessionResult {
        if (now() < blockedUntil) return checkNotNull(blockedFailure)
        return try {
            stage = DeviceSessionStage.STORAGE
            prepareStored()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed(DeviceSessionFailureKind.CONTRACT)
        }
    }

    private suspend fun prepareStored(): DeviceSessionResult {
        val credentials =
            when (val result = storage.read()) {
                is CredentialRead.Found -> result.credentials

                CredentialRead.Missing -> DeviceCredentials()

                is CredentialRead.Failure -> return failed(
                    if (result.legacyEnvironmentUnknown) {
                        DeviceSessionFailureKind.LEGACY_ENVIRONMENT_UNKNOWN
                    } else {
                        DeviceSessionFailureKind.STORAGE
                    },
                )
            }
        val refreshToken = credentials.refreshToken
        if (refreshToken != null) {
            stage = DeviceSessionStage.REFRESH
            val issuedAt = now()
            when (val result = api.refresh(DeviceRefreshRequestDto(refreshToken))) {
                is ApiResult.Success -> {
                    return access(result.value.accessToken, result.value.expiresIn, issuedAt, credentials.generation)
                }

                is ApiResult.Failure -> {
                    val http = result.reason as? NetworkFailure.HttpStatus
                    if (http?.statusCode != 401 || http.error?.code !in setOf("DEVICE-003", "DEVICE-004")) {
                        return failure(result.reason)
                    }
                    // Persist invalidation before any new registration. No token deletion on network failure.
                    val cleared = DeviceCredentials(generation = credentials.generation)
                    if (!storage.write(cleared)) return failed(DeviceSessionFailureKind.STORAGE)
                    return register(cleared)
                }
            }
        }
        return register(credentials)
    }

    private suspend fun register(initial: DeviceCredentials): DeviceSessionResult {
        var pending = initial
        // At most two registration transmissions, including expired-window recovery.
        repeat(2) { attempt ->
            if (pending.pendingRequestId == null) {
                pending =
                    DeviceCredentials(
                        pendingRequestId = newRequestId(),
                        pendingStartedAtMillis = now(),
                        generation = pending.generation,
                    )
                if (!storage.write(pending)) return failed(DeviceSessionFailureKind.STORAGE)
            }
            val attestation =
                when (val policy = attestationPolicy) {
                    DeviceAttestationPolicy.PlatformOnly -> {
                        DeviceAttestationDto(platform.name)
                    }

                    is DeviceAttestationPolicy.Required -> {
                        stage = DeviceSessionStage.CHALLENGE
                        val challenge =
                            when (val result = api.challenge()) {
                                is ApiResult.Success -> result.value
                                is ApiResult.Failure -> return failure(result.reason)
                            }
                        if (challenge.challenge.isBlank() ||
                            challenge.expiresIn <= 0
                        ) {
                            return failed(DeviceSessionFailureKind.CONTRACT)
                        }
                        stage = DeviceSessionStage.PROOF
                        diagnostics.recordSafely(DeviceSessionDiagnostic(stage, DeviceDiagnosticOutcome.STARTED))
                        val proof =
                            try {
                                policy.provider.attest(platform, challenge.challenge)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                return failed(
                                    DeviceSessionFailureKind.ATTESTATION,
                                    (error as? DeviceProofException)?.sdkCode,
                                )
                            }
                        if (proof.platform != platform.name || proof.token.isNullOrBlank() ||
                            proof.challenge != challenge.challenge ||
                            (platform == DevicePlatform.IOS && proof.keyId.isNullOrBlank())
                        ) {
                            return failed(DeviceSessionFailureKind.ATTESTATION)
                        }
                        diagnostics.recordSafely(DeviceSessionDiagnostic(stage, DeviceDiagnosticOutcome.SUCCEEDED))
                        proof
                    }
                }
            stage = DeviceSessionStage.REGISTER
            val issuedAt = now()
            when (
                val result =
                    api.register(
                        DeviceRegistrationRequestDto(checkNotNull(pending.pendingRequestId), attestation),
                    )
            ) {
                is ApiResult.Success -> {
                    val dto = result.value
                    if (dto.refreshToken.isBlank() ||
                        pending.generation == Long.MAX_VALUE
                    ) {
                        return failed(DeviceSessionFailureKind.CONTRACT)
                    }
                    val ready = access(dto.accessToken, dto.expiresIn, issuedAt, pending.generation + 1)
                    if (ready !is DeviceSessionResult.Ready) return ready
                    // One atomic replacement both saves refresh and removes the pending registration.
                    if (!storage.write(
                            DeviceCredentials(refreshToken = dto.refreshToken, generation = ready.access.generation),
                        )
                    ) {
                        return failed(DeviceSessionFailureKind.STORAGE)
                    }
                    return ready
                }

                is ApiResult.Failure -> {
                    val http = result.reason as? NetworkFailure.HttpStatus
                    if (attempt == 0 && http?.statusCode == 409 && http.error?.code == "DEVICE-002") {
                        pending = DeviceCredentials(generation = pending.generation)
                    } else if (attempt == 0 && retryableRegistration(result.reason)) {
                        // Preserve requestId even when the server's success body could not be decoded.
                    } else {
                        return failure(result.reason)
                    }
                }
            }
        }
        return failed(DeviceSessionFailureKind.SERVER)
    }

    private fun retryableRegistration(reason: NetworkFailure): Boolean =
        when (reason) {
            is NetworkFailure.Transport, is NetworkFailure.Contract -> {
                true
            }

            is NetworkFailure.HttpStatus -> {
                (reason.statusCode >= 500 && reason.statusCode != 503) ||
                    (
                        reason.statusCode == 400 && reason.error?.code == "DEVICE-005" &&
                            attestationPolicy is DeviceAttestationPolicy.Required
                    )
            }

            else -> {
                false
            }
        }

    private fun access(
        token: String,
        seconds: Long,
        issuedAt: Long,
        generation: Long,
    ): DeviceSessionResult {
        if (token.isBlank() || seconds <= 0 || issuedAt < 0 || seconds > (Long.MAX_VALUE - issuedAt) / 1000) {
            return failed(DeviceSessionFailureKind.CONTRACT)
        }
        return DeviceSessionResult.Ready(DeviceAccess(token, issuedAt, issuedAt + seconds * 1000, generation))
    }

    private fun failure(reason: NetworkFailure): DeviceSessionResult.Failed {
        val http = reason as? NetworkFailure.HttpStatus
        val kind =
            when (reason) {
                is NetworkFailure.Transport -> {
                    DeviceSessionFailureKind.NETWORK
                }

                is NetworkFailure.Contract -> {
                    DeviceSessionFailureKind.CONTRACT
                }

                is NetworkFailure.HttpStatus -> {
                    when {
                        reason.statusCode == 429 || reason.error?.code == "DEVICE-007" -> {
                            DeviceSessionFailureKind.RATE_LIMITED
                        }

                        reason.error?.code in setOf("DEVICE-005", "DEVICE-006") -> {
                            DeviceSessionFailureKind.ATTESTATION
                        }

                        else -> {
                            DeviceSessionFailureKind.SERVER
                        }
                    }
                }

                else -> {
                    DeviceSessionFailureKind.SERVER
                }
            }
        val retrySeconds =
            http
                ?.retryAfter
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it >= 0 }
        val time = now()
        val retryAt =
            retrySeconds?.let { time + minOf(it, (Long.MAX_VALUE - time) / 1000) * 1000 }
                ?: http?.retryAfter?.let { runCatching { it.fromHttpToGmtDate().timestamp }.getOrNull() }
        val result =
            DeviceSessionResult.Failed(
                DeviceSessionFailure(
                    kind,
                    http?.statusCode,
                    http?.error?.code?.takeIf {
                        it.matches(Regex("(?:DEVICE|AUTH|COMMON)-[0-9]{3}"))
                    },
                    retryAt,
                    stage,
                ),
            )
        if (retryAt != null && http?.statusCode in setOf(429, 503)) {
            blockedUntil = retryAt
            blockedFailure = result
        }
        return result
    }

    private fun failed(
        kind: DeviceSessionFailureKind,
        sdkCode: Int? = null,
    ): DeviceSessionResult.Failed {
        val failedStage =
            if (kind == DeviceSessionFailureKind.STORAGE ||
                kind == DeviceSessionFailureKind.LEGACY_ENVIRONMENT_UNKNOWN
            ) {
                DeviceSessionStage.STORAGE
            } else {
                stage
            }
        diagnostics.recordSafely(
            DeviceSessionDiagnostic(failedStage, DeviceDiagnosticOutcome.FAILED, sdkCode = sdkCode, failureKind = kind),
        )
        return DeviceSessionResult.Failed(DeviceSessionFailure(kind, stage = failedStage, sdkCode = sdkCode))
    }
}
