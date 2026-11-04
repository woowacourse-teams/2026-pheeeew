package com.pheeeew.data.remote.api

import com.pheeeew.core.di.PlatformName
import com.pheeeew.core.network.ApiRequest
import com.pheeeew.core.network.ApiRequestExecutor
import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.AuthenticationRequirement
import com.pheeeew.core.network.RequestKind
import com.pheeeew.domain.model.config.AppConfigSnapshot
import com.pheeeew.domain.model.config.toAppConfigSnapshot
import dev.zacsweers.metro.Inject
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonObject

@Inject
class AppConfigApi(
    private val requests: ApiRequestExecutor,
    @PlatformName private val platform: String,
) {
    suspend fun get(): AppConfigSnapshot =
        when (
            val result =
                requests.execute(
                    ApiRequest(
                        method = HttpMethod.Get,
                        path = "/api/v2/app/config",
                        kind = RequestKind.READ,
                        authentication = AuthenticationRequirement.NONE,
                        queryParameters = mapOf("platform" to platform),
                    ),
                ) { it.body<JsonObject>().toAppConfigSnapshot() }
        ) {
            is ApiResult.Success -> result.value.validated()
            is ApiResult.Failure -> error("설정 조회 실패")
        }
}
