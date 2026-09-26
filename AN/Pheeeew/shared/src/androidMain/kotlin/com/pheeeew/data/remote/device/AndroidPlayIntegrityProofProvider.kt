package com.pheeeew.data.remote.device

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityServiceException
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.pheeeew.domain.model.device.DevicePlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Classic requests use the server challenge unchanged as their nonce. */
class AndroidPlayIntegrityProofProvider(
    context: Context,
    private val cloudProjectNumber: Long,
) : DeviceProofProvider {
    private val applicationContext = context.applicationContext
    private val manager by lazy { IntegrityManagerFactory.create(applicationContext) }

    init {
        require(cloudProjectNumber > 0) { "Play Integrity cloud project is required" }
    }

    override suspend fun attest(
        platform: DevicePlatform,
        challenge: String,
    ): DeviceAttestationDto {
        require(platform == DevicePlatform.ANDROID)
        require(challenge.isNotBlank())
        val token =
            suspendCancellableCoroutine<String> { continuation ->
                manager
                    .requestIntegrityToken(
                        IntegrityTokenRequest
                            .builder()
                            .setNonce(challenge)
                            .setCloudProjectNumber(cloudProjectNumber)
                            .build(),
                    ).addOnSuccessListener { response ->
                        continuation.resume(response.token())
                    }.addOnFailureListener { failure ->
                        continuation.resumeWithException(
                            DeviceProofException((failure as? IntegrityServiceException)?.errorCode),
                        )
                    }.addOnCanceledListener {
                        continuation.cancel()
                    }
            }
        check(token.isNotBlank()) { "Play Integrity returned an empty proof" }
        return DeviceAttestationDto(platform.name, token, challenge)
    }
}
