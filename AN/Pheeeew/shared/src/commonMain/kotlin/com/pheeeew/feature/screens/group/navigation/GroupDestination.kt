package com.pheeeew.feature.screens.group.navigation

import kotlinx.serialization.Serializable

@Serializable
data object GroupHomeDestination

@Serializable
data object GroupCreateDestination

@Serializable
data class GroupDetailDestination(
    val groupId: String,
)
