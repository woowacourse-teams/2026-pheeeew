package com.pheeeew.domain.model.ranking

import com.pheeeew.domain.model.group.GroupId
import com.pheeeew.domain.model.group.GroupStamp

data class GroupRanking(
    val weeksAgo: Int,
    val startAt: String,
    val endAt: String,
    val hasPrevious: Boolean,
    val items: List<GroupRankingItem>,
)

data class GroupRankingItem(
    val rank: Int,
    val groupId: GroupId,
    val name: String,
    val stamp: GroupStamp,
    val score: Int,
)
