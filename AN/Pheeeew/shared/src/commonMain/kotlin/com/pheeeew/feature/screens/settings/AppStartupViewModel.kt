package com.pheeeew.feature.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pheeeew.core.di.AppVersion
import com.pheeeew.domain.model.version.AppVersionDecision
import com.pheeeew.domain.model.version.evaluateAppVersion
import com.pheeeew.domain.repository.AppVersionRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class AppStartupState(
    val checked: Boolean = false,
    val decision: AppVersionDecision? = null,
)

/** A failed public version lookup allows entry; cancellation never pretends to finish a check. */
@Inject
class AppStartupViewModel(
    private val repository: AppVersionRepository,
    @AppVersion private val version: String,
) : ViewModel() {
    private val mutable = MutableStateFlow(AppStartupState())
    val state = mutable.asStateFlow()
    private var check: Job? = null

    fun checkVersion() {
        if (check?.isActive == true) return
        check =
            viewModelScope.launch {
                try {
                    val policy = withTimeout(10_000L) { repository.getPolicy() }
                    mutable.value = AppStartupState(true, evaluateAppVersion(version, policy))
                } catch (_: TimeoutCancellationException) {
                    mutable.update { it.copy(checked = true) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    mutable.update { it.copy(checked = true) }
                }
            }
    }
}
