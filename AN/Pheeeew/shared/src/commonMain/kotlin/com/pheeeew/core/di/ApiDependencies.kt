package com.pheeeew.core.di

import com.pheeeew.core.di.device.DeviceSessionBuildConfig
import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.core.session.DeviceSessionManager
import com.pheeeew.data.repository.AppConfigRepository
import com.pheeeew.data.repository.DeviceNicknameRepositoryImpl
import com.pheeeew.domain.model.device.DeviceSessionResult
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Owns process resources; features borrow them and must never close them. */
@Inject
class ApiDependencies(
    val buildConfig: DeviceSessionBuildConfig,
    @Bootstrap private val bootstrap: ApiClient,
    val client: ApiClient,
    val session: DeviceSessionManager,
    val nicknameRepository: DeviceNicknameRepositoryImpl,
    val connectivityObserver: ConnectivityObserver,
    val appSession: AppSession,
    val appConfig: AppConfigRepository,
    @SessionWork private val scope: CoroutineScope,
) {
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var generation: Long? = null
            session.state.collect { state ->
                val nextGeneration =
                    when (state) {
                        is DeviceSessionResult.Ready -> state.access.generation
                        is DeviceSessionResult.NeedsRegistration -> null
                        else -> return@collect
                    }
                if (generation != null && generation != nextGeneration) nicknameRepository.invalidate()
                generation = nextGeneration
            }
        }
    }

    fun close() {
        appSession.close()
        scope.cancel()
        client.close()
        bootstrap.close()
    }
}

/** Storage partition is selected from the same configuration as the request destination. */
fun ApiConfig.deviceStorageEnvironment(): String =
    when (baseUrl.trimEnd('/')) {
        "https://api-dev.pheeeew.com" -> "dev"
        "https://api.pheeeew.com" -> "prod"
        else -> error("Configure a credential partition for this API environment")
    }
