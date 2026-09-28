package com.pheeeew.domain.repository.group

import com.pheeeew.domain.model.group.GroupStampItem

fun interface GroupStampListRepository {
    suspend fun findMine(): GroupStampListLoadResult
}

sealed interface GroupStampListLoadResult {
    data class Loaded(
        val groups: List<GroupStampItem>,
    ) : GroupStampListLoadResult

    data object Unavailable : GroupStampListLoadResult
}
