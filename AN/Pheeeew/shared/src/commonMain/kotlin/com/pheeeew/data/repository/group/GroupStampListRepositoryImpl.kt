package com.pheeeew.data.repository.group

import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.group.api.GroupStampListApi
import com.pheeeew.data.remote.group.mapper.GroupResponseMapper
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class GroupStampListRepositoryImpl(
    private val api: GroupStampListApi,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : GroupStampListRepository {
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    override val membershipChanges = revision.asStateFlow()
    private var cached: GroupStampListLoadResult.Loaded? = null
    private var cachedAtMillis: Long? = null
    private var cachedRevision = -1L

    override fun invalidate() {
        revision.update { it + 1 }
    }

    override suspend fun findMyStamps(): GroupStampListLoadResult = mutex.withLock { loadCurrentRevision() }

    private suspend fun loadCurrentRevision(): GroupStampListLoadResult {
        while (true) {
            val requestRevision = revision.value
            val now = nowMillis()
            val cachedAt = cachedAtMillis
            if (cachedRevision == requestRevision && cachedAt != null &&
                now >= cachedAt && now - cachedAt < CACHE_LIFETIME_MILLIS
            ) {
                cached?.let { return it }
            }
            val loaded =
                when (val result = api.findMyStamps()) {
                    is ApiResult.Success -> {
                        try {
                            GroupStampListLoadResult.Loaded(result.value.map(GroupResponseMapper::toDomain))
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
            // A request started before a membership change must neither refill the cache
            // nor return the old membership list to either selector.
            if (requestRevision != revision.value) continue
            if (loaded is GroupStampListLoadResult.Loaded) {
                cached = loaded
                cachedAtMillis = nowMillis()
                cachedRevision = requestRevision
            }
            return loaded
        }
    }

    private companion object {
        const val CACHE_LIFETIME_MILLIS = 5 * 60 * 1000L
    }
}
