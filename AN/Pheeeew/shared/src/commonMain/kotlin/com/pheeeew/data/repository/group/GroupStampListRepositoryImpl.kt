package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.group.api.GroupStampListApi
import com.pheeeew.data.remote.group.mapper.GroupResponseMapper
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class GroupStampListRepositoryImpl(
    private val api: GroupStampListApi,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : GroupStampListRepository {
    private val mutex = Mutex()
    private var cached: GroupStampListLoadResult.Loaded? = null
    private var cachedAtMillis: Long? = null

    override suspend fun findMyStamps(): GroupStampListLoadResult =
        mutex.withLock {
            val now = nowMillis()
            val cachedAt = cachedAtMillis
            if (cachedAt != null && now >= cachedAt && now - cachedAt < CACHE_LIFETIME_MILLIS) {
                cached?.let { return@withLock it }
            }
            when (val result = api.findMyStamps()) {
                is ApiResult.Success -> {
                    try {
                        GroupStampListLoadResult.Loaded(result.value.map(GroupResponseMapper::toDomain)).also {
                            cached = it
                            cachedAtMillis = nowMillis()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        GroupStampListLoadResult.Unavailable
                    }
                }

                is ApiResult.Failure -> {
                    GroupStampListLoadResult.Unavailable
                }
            }
        }

    private companion object {
        const val CACHE_LIFETIME_MILLIS = 5 * 60 * 1000L
    }
}
