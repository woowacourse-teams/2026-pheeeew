package com.pheeeew.data.remote.device.api

import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.data.remote.device.dto.DeviceChallengeResponseDto
import com.pheeeew.data.remote.device.dto.DeviceRegistrationRequestDto
import com.pheeeew.data.remote.device.dto.DeviceRegistrationResponseDto
import com.pheeeew.data.remote.device.dto.RefreshTokenRequestDto
import com.pheeeew.data.remote.device.dto.RefreshTokenResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KtorDeviceRegistrationApi(
    private val client: HttpClient,
) : DeviceRegistrationApi {
    override suspend fun getChallenge(): DeviceChallengeResponseDto =
        executeRequest {
            client.post("/api/v2/devices/challenge")
        }

    override suspend fun register(request: DeviceRegistrationRequestDto): DeviceRegistrationResponseDto =
        executeRequest {
            client.post("/api/v2/devices") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun refresh(request: RefreshTokenRequestDto): RefreshTokenResponseDto =
        executeRequest {
            client.post("/api/v2/devices/tokens") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
}
