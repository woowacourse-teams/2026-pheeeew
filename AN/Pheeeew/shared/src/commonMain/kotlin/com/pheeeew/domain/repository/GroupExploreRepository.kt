package com.pheeeew.domain.repository

import com.pheeeew.domain.model.group.GroupExplorePage

fun interface GroupExploreRepository {
    suspend fun loadPage(
        keyword: String?,
        cursor: String?,
    ): GroupExploreResult
}

sealed interface GroupExploreResult {
    data class Loaded(
        val page: GroupExplorePage,
    ) : GroupExploreResult

    data object InvalidCursor : GroupExploreResult

    data object Unavailable : GroupExploreResult
}
