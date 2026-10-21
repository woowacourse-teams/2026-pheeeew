package com.pheeeew.core.di.group

import com.pheeeew.feature.screens.group.create.EmptyGroupCreateSessionStore
import com.pheeeew.feature.screens.group.create.GroupCreateActions
import com.pheeeew.feature.screens.group.create.GroupCreateErrorReporter
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.detail.GroupDetailDependencies
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.home.explore.GroupExploreSource
import com.pheeeew.feature.screens.group.join.GroupJoinDependencies
import com.pheeeew.feature.screens.group.members.GroupMembersSource
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Reusable API-backed providers shared by the group navigation entries. */
data class GroupDependencies(
    val groupListSource: GroupListSource,
    val createActions: GroupCreateActions,
    val createErrorReporter: GroupCreateErrorReporter,
    val operationKeyAllocator: GroupOperationKeyAllocator,
    val join: GroupJoinDependencies,
    val detail: GroupDetailDependencies,
    val membershipChanges: Flow<Long> = emptyFlow(),
    val invalidateSharedMembership: () -> Unit = {},
    val createSessionStore: GroupCreateSessionStore = EmptyGroupCreateSessionStore,
    val exploreSource: GroupExploreSource,
    val membersSource: GroupMembersSource,
)
