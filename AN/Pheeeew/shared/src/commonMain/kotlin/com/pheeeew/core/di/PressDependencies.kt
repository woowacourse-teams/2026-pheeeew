package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.data.remote.press.EmotionPressApi
import com.pheeeew.data.remote.press.PressApiSender
import com.pheeeew.data.remote.press.PressApiStatisticsDataSource
import com.pheeeew.data.repository.press.PressRepositoryImpl
import com.pheeeew.domain.repository.press.PressRepository
import com.pheeeew.domain.repository.press.PressSender
import kotlinx.coroutines.CoroutineScope

/** Creates the API-backed repository; an injected sender is available for deterministic tests. */
fun createPressRepository(
    apiClient: ApiClient,
    sessionScope: CoroutineScope,
    sender: PressSender? = null,
    connectivityObserver: ConnectivityObserver? = null,
): PressRepository =
    PressRepositoryImpl(
        statistics = PressApiStatisticsDataSource(EmotionPressApi(apiClient.requests)),
        sender = sender ?: PressApiSender(EmotionPressApi(apiClient.requests)),
        sessionScope = sessionScope,
        connectivity = connectivityObserver?.isConnected,
    )
