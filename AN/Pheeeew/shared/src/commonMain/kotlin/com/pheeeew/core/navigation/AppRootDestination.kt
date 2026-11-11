package com.pheeeew.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MapRootDestination

@Serializable
data class PressEventDestination(
    val eventId: String? = null,
)

@Serializable
data object PressRootDestination

@Serializable
data object RankingRootDestination

@Serializable
data class DailyPressRankingRootDestination(
    val date: String,
)

@Serializable
data object CollectionRootDestination

@Serializable
data class CollectionDayDestination(
    val month: String,
    val cardId: String,
)
