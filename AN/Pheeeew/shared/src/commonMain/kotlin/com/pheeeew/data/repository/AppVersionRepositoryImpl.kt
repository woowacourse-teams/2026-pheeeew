package com.pheeeew.data.repository

import com.pheeeew.data.remote.api.AppVersionApi
import com.pheeeew.data.remote.mapper.toPolicy
import com.pheeeew.domain.model.version.AppVersionPolicy
import com.pheeeew.domain.repository.AppVersionRepository
import dev.zacsweers.metro.Inject

@Inject
class AppVersionRepositoryImpl(
    private val api: AppVersionApi,
) : AppVersionRepository {
    override suspend fun getPolicy(): AppVersionPolicy = api.getPolicy().toPolicy()
}
