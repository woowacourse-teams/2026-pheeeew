package com.pheeeew.core.di

import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.network.IosConnectivityObserver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

class IosAppServiceFactory : AppServiceFactory() {
    override fun createApiEngine(): HttpClientEngine = Darwin.create()

    override fun createConnectivityObserver(): ConnectivityObserver = IosConnectivityObserver()
}
