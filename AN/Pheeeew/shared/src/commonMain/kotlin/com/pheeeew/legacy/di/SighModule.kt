package com.pheeeew.legacy.di

import com.pheeeew.legacy.core.geo.GeodesicSighLocationObfuscator
import com.pheeeew.legacy.core.monitoring.Monitoring
import com.pheeeew.legacy.core.network.ApiConfig
import com.pheeeew.legacy.core.network.createPlatformHttpClient
import com.pheeeew.legacy.data.local.device.AccessTokenStore
import com.pheeeew.legacy.data.local.device.DeviceIdStorage
import com.pheeeew.legacy.data.remote.block.api.KtorDeviceBlockApi
import com.pheeeew.legacy.data.remote.report.api.KtorSighReportApi
import com.pheeeew.legacy.data.remote.sigh.api.KtorSighV1Api
import com.pheeeew.legacy.data.remote.sigh.api.KtorSighV2Api
import com.pheeeew.legacy.data.repository.SighRepositoryImpl
import com.pheeeew.legacy.domain.model.device.AccessToken
import com.pheeeew.legacy.domain.repository.SighRepository
import com.pheeeew.legacy.domain.usecase.BlockUserUseCase
import com.pheeeew.legacy.domain.usecase.CreateSighUseCase
import com.pheeeew.legacy.domain.usecase.ReportSighUseCase

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
        monitoring: Monitoring? = null,
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
                        monitoring = monitoring,
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
