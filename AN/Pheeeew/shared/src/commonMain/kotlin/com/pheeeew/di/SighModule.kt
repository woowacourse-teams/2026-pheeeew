package com.pheeeew.di

import com.pheeeew.core.geo.GeodesicSighLocationObfuscator
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createPlatformHttpClient
import com.pheeeew.data.local.device.AccessTokenStore
import com.pheeeew.data.local.device.DeviceIdStorage
import com.pheeeew.data.remote.block.api.KtorDeviceBlockApi
import com.pheeeew.data.remote.report.api.KtorSighReportApi
import com.pheeeew.data.remote.sigh.api.KtorSighV1Api
import com.pheeeew.data.remote.sigh.api.KtorSighV2Api
import com.pheeeew.data.repository.SighRepositoryImpl
import com.pheeeew.domain.model.device.AccessToken
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.domain.usecase.CreateSighUseCase
import com.pheeeew.domain.usecase.ReportSighUseCase

data class SighDependencies(
    val repository: SighRepository,
    val createSigh: CreateSighUseCase,
    val blockUser: BlockUserUseCase,
    val reportSigh: ReportSighUseCase,
)

object SighModule {
    fun create(
        config: ApiConfig,
        deviceIdStorage: DeviceIdStorage,
        accessTokenStore: AccessTokenStore? = null,
        refreshAccessToken: (suspend () -> AccessToken?)? = null,
    ): SighDependencies {
        val client = createPlatformHttpClient(config)
        val repository =
            SighRepositoryImpl(
                sighV1Api =
                    KtorSighV1Api(
                        client = client,
                        accessTokenStore = accessTokenStore,
                        refreshAccessToken = refreshAccessToken,
                    ),
                sighV2Api =
                    KtorSighV2Api(
                        client = client,
                        accessTokenStore = accessTokenStore,
                        refreshAccessToken = refreshAccessToken,
                    ),
            )
        return SighDependencies(
            repository = repository,
            blockUser =
                BlockUserUseCase(
                    KtorDeviceBlockApi(
                        client = client,
                        accessTokenStore = accessTokenStore,
                        refreshAccessToken = refreshAccessToken,
                    ),
                ),
            reportSigh =
                ReportSighUseCase(
                    KtorSighReportApi(
                        client = client,
                        accessTokenStore = accessTokenStore,
                        refreshAccessToken = refreshAccessToken,
                    ),
                    deviceIdStorage,
                ),
            createSigh =
                CreateSighUseCase(
                    repository = repository,
                    locationObfuscator = GeodesicSighLocationObfuscator(),
                ),
        )
    }
}
