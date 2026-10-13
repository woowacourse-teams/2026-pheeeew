package com.pheeeew.feature.screens.group.mapper

import com.pheeeew.feature.screens.group.model.GroupJoinType
import com.pheeeew.domain.model.group.GroupJoinType as DomainGroupJoinType

fun GroupJoinType.toDomainJoinType(): DomainGroupJoinType =
    when (this) {
        GroupJoinType.PUBLIC -> DomainGroupJoinType.PUBLIC
        GroupJoinType.PRIVATE -> DomainGroupJoinType.PRIVATE
    }

fun DomainGroupJoinType.toUiJoinType(): GroupJoinType =
    when (this) {
        DomainGroupJoinType.PUBLIC -> GroupJoinType.PUBLIC
        DomainGroupJoinType.PRIVATE -> GroupJoinType.PRIVATE
    }
