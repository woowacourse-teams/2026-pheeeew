package com.pheeeew.data.remote.device.api

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.AuthenticationRequirement
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.device.dto.DeviceNicknameAvailabilityResponseDto
import com.pheeeew.data.remote.device.dto.DeviceNicknameResponseDto
import com.pheeeew.data.remote.device.dto.DeviceNicknameUpdateRequestDto
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

class DeviceNicknameApi(
    private val requests: ApiRequestExecutor,
) {
    suspend fun findMyNickname(): ApiResult<DeviceNicknameResponseDto> =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                "/api/v3/devices/me/nickname",
                RequestKind.READ,
                monitoringEndpoint = "device_nickname",
            ),
        ) { it.body() }

    suspend fun checkAvailability(nickname: String): ApiResult<DeviceNicknameAvailabilityResponseDto> =
        requests.execute(
            ApiRequest(
                HttpMethod.Get,
                "/api/v3/devices/nicknames/availability",
                RequestKind.READ,
                AuthenticationRequirement.NONE,
                queryParameters = mapOf("nickname" to nickname),
                monitoringEndpoint = "device_nickname_availability",
            ),
        ) { it.body() }

    suspend fun updateNickname(nickname: String): ApiResult<Unit> =
        requests.executeNoContent(
            ApiRequest(
                HttpMethod.Put,
                "/api/v3/devices/me/nickname",
                RequestKind.WRITE,
                body = DeviceNicknameUpdateRequestDto(nickname),
                monitoringEndpoint = "device_nickname_update",
            ),
        )
}
