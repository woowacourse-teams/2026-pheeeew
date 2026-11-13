package com.pheeeew.core.di

import android.content.Context
import com.pheeeew.core.network.AndroidConnectivityObserver
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.network.createAndroidApiEngine
import io.ktor.client.engine.HttpClientEngine

class AndroidAppServiceFactory(
    context: Context,
) : AppServiceFactory() {
    private val context = context.applicationContext

    override fun createApiEngine(): HttpClientEngine = createAndroidApiEngine()

    override fun createConnectivityObserver(): ConnectivityObserver = AndroidConnectivityObserver(context)
}
