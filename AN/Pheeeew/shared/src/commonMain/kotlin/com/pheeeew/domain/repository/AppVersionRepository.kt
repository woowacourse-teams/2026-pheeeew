package com.pheeeew.domain.repository

import com.pheeeew.domain.model.version.AppVersionPolicy

fun interface AppVersionRepository {
    suspend fun getPolicy(): AppVersionPolicy

    fun cachedPolicy(): AppVersionPolicy? = null
}
