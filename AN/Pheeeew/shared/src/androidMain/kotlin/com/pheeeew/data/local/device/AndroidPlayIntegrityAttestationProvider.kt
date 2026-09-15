package com.pheeeew.data.local.device

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.pheeeew.domain.model.device.DevicePlatform
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AndroidPlayIntegrityAttestationProvider(
    context: Context,
    private val cloudProjectNumber: Long,
) : DeviceAttestationProvider {
    private val integrityManager = IntegrityManagerFactory.create(context.applicationContext)

    override suspend fun create(challenge: String): DeviceAttestation {
        val response =
            suspendCoroutine { continuation ->
                integrityManager
                    .requestIntegrityToken(
                        IntegrityTokenRequest
                            .builder()
                            .setNonce(challenge)
                            .setCloudProjectNumber(cloudProjectNumber)
                            .build(),
                    ).addOnSuccessListener(continuation::resume)
                    .addOnFailureListener(continuation::resumeWithException)
            }

        return DeviceAttestation(
            platform = DevicePlatform.ANDROID,
            token = response.token(),
            challenge = challenge,
        )
    }
}
