package com.pheeeew.developertools.simulation

import com.pheeeew.core.di.AppServiceFactory
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.data.repository.RecordingAudioReader
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.MutableStateFlow

internal class TestPlatformFactory(
    private val engine: () -> HttpClientEngine = { MockEngine { error("Unexpected HTTP") } },
    private val reader: RecordingAudioReader = RecordingAudioReader { byteArrayOf(1, 2, 3) },
    val connected: MutableStateFlow<Boolean> = MutableStateFlow(true),
) : AppServiceFactory() {
    override fun createApiEngine(): HttpClientEngine = engine()

    override fun createRecordingAudioReader(): RecordingAudioReader = reader

    override fun createConnectivityObserver() =
        object : ConnectivityObserver {
            override val isConnected = connected
        }
}
