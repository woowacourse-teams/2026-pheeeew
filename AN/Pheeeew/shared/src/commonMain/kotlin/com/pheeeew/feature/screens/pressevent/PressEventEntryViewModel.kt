package com.pheeeew.feature.screens.pressevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.network.ConnectivityObserver
import com.pheeeew.data.repository.PressEventConfigRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal enum class PressEventEntryStatus { Loading, Available, Disabled, Offline, Failed }

/** One check per navigation entry. An accepted entry is not revoked during a game or submission. */
@Inject
internal class PressEventEntryViewModel(
    config: PressEventConfigRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val mutableStatus = MutableStateFlow(PressEventEntryStatus.Loading)
    val status = mutableStatus.asStateFlow()

    init {
        viewModelScope.launch {
            mutableStatus.value =
                when (config.fetchEventEnabledForEntry()) {
                    true -> {
                        PressEventEntryStatus.Available
                    }

                    false -> {
                        PressEventEntryStatus.Disabled
                    }

                    null -> {
                        val connected =
                            withTimeoutOrNull(1_000L) {
                                connectivity.isConnected.catch { emit(true) }.first()
                            }
                        if (connected == false) PressEventEntryStatus.Offline else PressEventEntryStatus.Failed
                    }
                }
        }
    }
}
