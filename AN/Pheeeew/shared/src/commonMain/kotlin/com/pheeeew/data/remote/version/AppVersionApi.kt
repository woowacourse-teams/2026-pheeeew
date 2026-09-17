package com.pheeeew.data.remote.version

import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createPlatformHttpClient
import com.pheeeew.data.remote.common.executeRequest
import com.pheeeew.domain.model.version.AppVersionPolicy
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

@Serializable
data class AppVersionResponseDto(
    val minSupportedVersion: String,
    val latestVersion: String,
    val storeUrl: String,
)

fun AppVersionResponseDto.toPolicy(): AppVersionPolicy = AppVersionPolicy(minSupportedVersion, latestVersion, storeUrl)

class AppVersionApi(
    private val client: HttpClient,
    private val platform: String,
) {
    init {
        require(platform == "android" || platform == "ios")
    }

    suspend fun getPolicy(): AppVersionResponseDto =
        executeRequest {
            client.get("/api/v2/app/version") {
                parameter("platform", platform)
            }
        }
}

fun createAppVersionApi(
    config: ApiConfig,
    platform: String,
): AppVersionApi = AppVersionApi(createPlatformHttpClient(config), platform)
