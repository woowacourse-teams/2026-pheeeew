package com.pheeeew.core.di

import com.pheeeew.domain.repository.PressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/** Process-scoped feature session. UI collectors may come and go while its work continues. */
class AppSession internal constructor(
    val pressRepository: PressRepository,
    private val scope: CoroutineScope,
) {
    fun onForeground() = pressRepository.onForeground()

    fun close() = scope.cancel()
}
