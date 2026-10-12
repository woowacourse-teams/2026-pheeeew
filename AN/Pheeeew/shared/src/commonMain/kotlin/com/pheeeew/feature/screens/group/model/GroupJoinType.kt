package com.pheeeew.feature.screens.group.model

import kotlinx.serialization.Serializable

/** Defines how someone can become a member of a group. */
@Serializable
enum class GroupJoinType {
    PUBLIC,
    PRIVATE,
}
