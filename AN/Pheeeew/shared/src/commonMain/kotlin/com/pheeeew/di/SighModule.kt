package com.pheeeew.di

import com.pheeeew.core.geo.Epsg5179SighLocationObfuscator
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.createPlatformHttpClient
import com.pheeeew.data.remote.sigh.api.KtorSighV1Api
import com.pheeeew.data.remote.sigh.api.KtorSighV2Api
import com.pheeeew.data.repository.SighRepositoryImpl
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.domain.usecase.CreateSighUseCase

data class SighDependencies(
    val repository: SighRepository,
    val createSigh: CreateSighUseCase,
)

object SighModule {
    fun create(config: ApiConfig): SighDependencies {
        val client = createPlatformHttpClient(config)
        val repository =
            SighRepositoryImpl(
                sighV1Api = KtorSighV1Api(client),
                sighV2Api = KtorSighV2Api(client),
            )
        return SighDependencies(
            repository = repository,
            createSigh =
                CreateSighUseCase(
                    repository = repository,
                    locationObfuscator = Epsg5179SighLocationObfuscator(),
                ),
        )
    }
}
