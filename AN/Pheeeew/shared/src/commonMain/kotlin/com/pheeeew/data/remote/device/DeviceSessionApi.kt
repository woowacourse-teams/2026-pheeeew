package com.pheeeew.data.remote.device

import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.AuthenticationRequirement
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.core.network.RequestKind
import com.pheeeew.domain.model.device.DeviceDiagnosticOutcome
import com.pheeeew.domain.model.device.DeviceSessionDiagnostic
import com.pheeeew.domain.model.device.DeviceSessionDiagnostics
import com.pheeeew.domain.model.device.DeviceSessionStage
import com.pheeeew.domain.model.device.recordSafely
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

interface DeviceSessionApi {
    suspend fun register(request: DeviceRegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto>

    suspend fun refresh(request: DeviceRefreshRequestDto): ApiResult<DeviceRefreshResponseDto>

    suspend fun challenge(): ApiResult<DeviceChallengeDto>
}

class KtorDeviceSessionApi(
    private val requests: ApiRequestExecutor,
    private val diagnostics: DeviceSessionDiagnostics = DeviceSessionDiagnostics {},
) : DeviceSessionApi {
    override suspend fun register(request: DeviceRegistrationRequestDto): ApiResult<DeviceRegistrationResponseDto> =
        execute(DeviceSessionStage.REGISTER, post("/api/v2/devices", request), setOf(200, 201))

    override suspend fun refresh(request: DeviceRefreshRequestDto): ApiResult<DeviceRefreshResponseDto> =
        execute(DeviceSessionStage.REFRESH, post("/api/v2/devices/tokens", request), setOf(200))

    override suspend fun challenge(): ApiResult<DeviceChallengeDto> =
        execute(DeviceSessionStage.CHALLENGE, post("/api/v2/devices/challenge", null), setOf(200))

    private suspend inline fun <reified T> execute(
        stage: DeviceSessionStage,
        request: ApiRequest,
        accepted: Set<Int>,
    ): ApiResult<T> {
        diagnostics.recordSafely(DeviceSessionDiagnostic(stage, DeviceDiagnosticOutcome.STARTED))
        var status: Int? = null
        val result =
            requests.execute(request) { response ->
                status = response.status.value
                check(status in accepted)
                response.body<T>()
            }
        val http = (result as? ApiResult.Failure)?.reason as? NetworkFailure.HttpStatus
        diagnostics.recordSafely(
            DeviceSessionDiagnostic(
                stage,
                if (result is ApiResult.Success) DeviceDiagnosticOutcome.SUCCEEDED else DeviceDiagnosticOutcome.FAILED,
                http?.statusCode ?: status,
                http?.error?.code,
            ),
        )
        return result
    }

    private fun post(
        path: String,
        body: Any?,
    ) = ApiRequest(HttpMethod.Post, path, RequestKind.WRITE, AuthenticationRequirement.NONE, body = body)
}
