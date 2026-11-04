package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.api.PressRankingApi
import com.pheeeew.data.remote.mapper.PressRankingMapper
import com.pheeeew.domain.repository.DailyPressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingLoadResult
import com.pheeeew.domain.repository.PressRankingRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

@Inject
class PressRankingRepositoryImpl(
    private val api: PressRankingApi,
    private val config: AppConfigRepository,
) : PressRankingRepository {
    override suspend fun findDaily(
        date: String,
        expectedSessionGeneration: Long?,
    ): DailyPressRankingLoadResult =
        coroutineScope {
            config.initialize()
            if (!config.state.value.config.dailyPressRankingEnabled) {
                return@coroutineScope DailyPressRankingLoadResult.Unavailable
            }
            val request = async { loadDaily(date, expectedSessionGeneration) }
            val disabled = async { config.state.first { !it.config.dailyPressRankingEnabled } }
            try {
                select<DailyPressRankingLoadResult> {
                    request.onAwait {
                        if (config.state.value.config.dailyPressRankingEnabled) {
                            it
                        } else {
                            DailyPressRankingLoadResult.Unavailable
                        }
                    }
                    disabled.onAwait { DailyPressRankingLoadResult.Unavailable }
                }
            } finally {
                request.cancel()
                disabled.cancel()
            }
        }

    private suspend fun loadDaily(
        date: String,
        expectedSessionGeneration: Long?,
    ): DailyPressRankingLoadResult =
        when (val result = api.findDaily(date, expectedSessionGeneration)) {
            is ApiResult.Success -> {
                try {
                    DailyPressRankingLoadResult.Loaded(PressRankingMapper.toDailyDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    DailyPressRankingLoadResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                DailyPressRankingLoadResult.Unavailable
            }
        }

    override suspend fun find(
        weeksAgo: Int,
        state: String?,
    ): PressRankingLoadResult =
        when (val result = api.find(weeksAgo, state)) {
            is ApiResult.Success -> {
                try {
                    PressRankingLoadResult.Loaded(PressRankingMapper.toDomain(result.value))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    PressRankingLoadResult.Unavailable
                }
            }

            is ApiResult.Failure -> {
                PressRankingLoadResult.Unavailable
            }
        }
}
