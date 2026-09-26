package com.pheeeew.data.remote.device

import com.pheeeew.domain.model.device.DevicePlatform
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.DeviceCheck.DCAppAttestService
import platform.DeviceCheck.DCError
import platform.DeviceCheck.DCErrorDomain
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Registration attestation only; access-token refresh never generates a new key. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosAppAttestProofProvider : DeviceProofProvider {
    override suspend fun attest(
        platform: DevicePlatform,
        challenge: String,
    ): DeviceAttestationDto {
        require(platform == DevicePlatform.IOS)
        val hash = appAttestClientDataHash(challenge)
        val service = DCAppAttestService.sharedService
        if (!service.isSupported()) throw DeviceProofException(DCError.DCErrorFeatureUnsupported.value.toInt())
        val keyId = service.generateKey()
        val data =
            try {
                service.attestation(keyId, hash)
            } catch (failure: AppAttestFailure) {
                if (!failure.serverUnavailable) throw failure
                // Apple requires the same key and hash when retrying a temporary service failure.
                delay(1_000)
                service.attestation(keyId, hash)
            }
        check(data.length > 0u) { "App Attest returned an empty proof" }
        return DeviceAttestationDto(platform.name, data.base64EncodedStringWithOptions(0u), challenge, keyId)
    }

    private suspend fun DCAppAttestService.generateKey(): String =
        suspendCancellableCoroutine { continuation ->
            generateKeyWithCompletionHandler { key, error ->
                when {
                    error != null -> continuation.resumeWithException(AppAttestFailure(error))
                    !key.isNullOrBlank() -> continuation.resume(key)
                    else -> continuation.resumeWithException(IllegalStateException("App Attest key missing"))
                }
            }
        }

    private suspend fun DCAppAttestService.attestation(
        keyId: String,
        hash: NSData,
    ): NSData =
        suspendCancellableCoroutine { continuation ->
            attestKey(keyId, clientDataHash = hash) { data, error ->
                when {
                    error != null -> continuation.resumeWithException(AppAttestFailure(error))
                    data != null -> continuation.resume(data)
                    else -> continuation.resumeWithException(IllegalStateException("App Attest proof missing"))
                }
            }
        }

    private class AppAttestFailure(
        error: NSError,
    ) : DeviceProofException(error.code.toInt().takeIf { error.domain == DCErrorDomain }) {
        val serverUnavailable = error.domain == DCErrorDomain && error.code == DCError.DCErrorServerUnavailable.value
    }
}

/** The backend hashes the ASCII challenge, not Base64-decoded challenge bytes. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun appAttestClientDataHash(challenge: String): NSData {
    require(challenge.isNotBlank() && challenge.all { it.code in 0..127 })
    val bytes = challenge.encodeToByteArray()
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { input ->
        digest.usePinned { output ->
            CC_SHA256(input.addressOf(0), bytes.size.toUInt(), output.addressOf(0).reinterpret())
        }
    }
    return digest.usePinned { NSData.create(bytes = it.addressOf(0), length = digest.size.toULong()) }
}
