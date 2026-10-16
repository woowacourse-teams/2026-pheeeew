package com.pheeeew.core.di

import com.pheeeew.core.network.ApiClient
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.domain.repository.press.PressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Process-scoped feature session. UI collectors may come and go while its work continues. */
class AppSession internal constructor(
    apiClient: ApiClient,
    connectivityObserver: ConnectivityObserver,
) {
    private val job = SupervisorJob()

    // Repository.accept is synchronous; using the UI dispatcher keeps its small in-memory mutations serialized
    // with the session worker while HTTP calls suspend without blocking the UI.
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    val pressRepository: PressRepository =
        createPressRepository(apiClient, scope, connectivityObserver = connectivityObserver)

    fun onForeground() {
        pressRepository.onForeground()
    }

    fun close() {
        scope.cancel()
    }
}
