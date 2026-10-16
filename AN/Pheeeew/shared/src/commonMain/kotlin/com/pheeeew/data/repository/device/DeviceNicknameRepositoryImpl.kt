package com.pheeeew.data.repository.device

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.device.api.DeviceNicknameApi
import com.pheeeew.domain.model.device.DeviceNicknameAvailability
import com.pheeeew.domain.model.device.DeviceNicknameLookup
import com.pheeeew.domain.model.device.DeviceNicknameState
import com.pheeeew.domain.model.device.DeviceNicknameUpdateResult
import com.pheeeew.domain.repository.device.DeviceNicknameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceNicknameRepositoryImpl(
    private val api: DeviceNicknameApi,
) : DeviceNicknameRepository {
    private val mutableNicknameState = MutableStateFlow(DeviceNicknameState())
    override val nicknameState = mutableNicknameState.asStateFlow()
    private val requestMutex = Mutex()

    /** Invalidates in-flight lookups as well as the last confirmed value when the device changes. */
    fun invalidate() {
        mutableNicknameState.update { DeviceNicknameState(revision = it.revision + 1) }
    }

    override suspend fun findMyNickname(): DeviceNicknameLookup =
        requestMutex.withLock {
            val revision = mutableNicknameState.value.revision
            mutableNicknameState.update { it.copy(isRefreshing = true, lookupFailed = false) }
            try {
                val result =
                    when (val response = api.findMyNickname()) {
                        is ApiResult.Success -> DeviceNicknameLookup.Found(response.value.nickname)
                        is ApiResult.Failure -> DeviceNicknameLookup.Failed
                    }
                if (mutableNicknameState.value.revision != revision) return@withLock DeviceNicknameLookup.Failed
                mutableNicknameState.update {
                    if (it.revision != revision) {
                        it
                    } else {
                        when (result) {
                            is DeviceNicknameLookup.Found -> {
                                it.copy(
                                    nickname = result.nickname,
                                    isConfirmed = true,
                                    isRefreshing = false,
                                    lookupFailed = false,
                                )
                            }

                            DeviceNicknameLookup.Failed -> {
                                it.copy(isRefreshing = false, lookupFailed = true)
                            }
                        }
                    }
                }
                result
            } finally {
                mutableNicknameState.update {
                    if (it.revision == revision) it.copy(isRefreshing = false) else it
                }
            }
        }

    override suspend fun checkAvailability(nickname: String): DeviceNicknameAvailability =
        when (val result = api.checkAvailability(nickname)) {
            is ApiResult.Success -> {
                DeviceNicknameAvailability.Checked(result.value.available)
            }

            is ApiResult.Failure -> {
                if ((result.reason as? NetworkFailure.HttpStatus)?.statusCode == 400) {
                    DeviceNicknameAvailability.Invalid
                } else {
                    DeviceNicknameAvailability.Failed
                }
            }
        }

    override suspend fun updateNickname(nickname: String): DeviceNicknameUpdateResult =
        requestMutex.withLock {
            when (val result = api.updateNickname(nickname)) {
                is ApiResult.Success -> {
                    DeviceNicknameUpdateResult.UPDATED
                }

                is ApiResult.Failure -> {
                    val http = result.reason as? NetworkFailure.HttpStatus
                    when {
                        http?.statusCode == 409 && http.error?.code == "DEVICE-009" -> DeviceNicknameUpdateResult.DUPLICATE
                        http?.statusCode == 400 && http.error?.code == "DEVICE-008" -> DeviceNicknameUpdateResult.INVALID
                        result.reason.mutationCertainty() == MutationCertainty.UNKNOWN -> DeviceNicknameUpdateResult.OUTCOME_UNKNOWN
                        else -> DeviceNicknameUpdateResult.FAILED
                    }
                }
            }
        }
}

private fun NetworkFailure.mutationCertainty(): MutationCertainty =
    when (this) {
        is NetworkFailure.SessionUnavailable -> mutationCertainty
        is NetworkFailure.SessionProviderFailed -> mutationCertainty
        is NetworkFailure.HttpStatus -> mutationCertainty
        is NetworkFailure.Transport -> mutationCertainty
        is NetworkFailure.Unexpected -> mutationCertainty
        is NetworkFailure.Contract -> mutationCertainty
    }
