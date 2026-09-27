package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.Group

fun interface GroupListRepository {
    suspend fun findMine(): GroupListLoadResult
}

sealed interface GroupListLoadResult {
    data class Loaded(
        val groups: List<Group>,
    ) : GroupListLoadResult

    data object Unavailable : GroupListLoadResult
}
