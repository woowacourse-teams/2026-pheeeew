package com.pheeeew.data.remote.api

import com.pheeeew.core.di.PlatformName
import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.AuthenticationRequirement
import com.pheeeew.core.network.RequestKind
import com.pheeeew.data.remote.dto.AppVersionResponseDto
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod

@Inject
class AppVersionApi(
    private val requests: ApiRequestExecutor,
    @PlatformName private val platform: String,
) {
    init {
        require(platform == "android" || platform == "ios")
    }

    suspend fun getPolicy(): AppVersionResponseDto =
        when (
            val result =
                requests.execute(
                    ApiRequest(
                        method = HttpMethod.Get,
                        path = "/api/v2/app/version",
                        kind = RequestKind.READ,
                        authentication = AuthenticationRequirement.NONE,
                        queryParameters = mapOf("platform" to platform),
                    ),
                ) { response -> response.body<AppVersionResponseDto>() }
        ) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> error("앱 버전 정책을 조회하지 못했습니다.")
        }
}
