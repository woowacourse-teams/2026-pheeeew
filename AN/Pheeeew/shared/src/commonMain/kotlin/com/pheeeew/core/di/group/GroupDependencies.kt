package com.pheeeew.core.di.group

import com.pheeeew.core.network.ApiClient
import com.pheeeew.feature.screens.group.create.EmptyGroupCreateSessionStore
import com.pheeeew.feature.screens.group.create.GroupCreateActions
import com.pheeeew.feature.screens.group.create.GroupCreateErrorReporter
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.detail.GroupDetailDependencies
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.join.GroupJoinDependencies
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.random.Random

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
)

/** Builds feature adapters once from the app-owned authenticated client. */
fun createGroupDependencies(
    apiClient: ApiClient,
    membershipChanges: Flow<Long> = emptyFlow(),
    invalidateSharedMembership: () -> Unit = {},
    createSessionStore: GroupCreateSessionStore = EmptyGroupCreateSessionStore,
    reportUnexpected: (Exception) -> Unit = ::reportUnexpectedGroupError,
): GroupDependencies {
    val operationKeyAllocator =
        GroupOperationKeyAllocator("group-${Random.nextLong().toULong().toString(16)}")
    val groupListSource = createGroupListSource(apiClient)
    val createActions = createGroupCreateActions(apiClient, groupListSource)
    val joinDependencies =
        createGroupJoinDependencies(
            apiClient = apiClient,
            groupListSource = groupListSource,
            errorReporter = { error -> reportUnexpected(error) },
            operationKeyAllocator = operationKeyAllocator,
        )
    val detailDependencies =
        createGroupDetailDependencies(
            apiClient = apiClient,
            errorReporter = { error -> reportUnexpected(error) },
            operationKeyAllocator = operationKeyAllocator,
        )

    return GroupDependencies(
        groupListSource = groupListSource,
        createActions = createActions,
        createErrorReporter = { error -> reportUnexpected(error) },
        operationKeyAllocator = operationKeyAllocator,
        join = joinDependencies,
        detail = detailDependencies,
        membershipChanges = membershipChanges,
        invalidateSharedMembership = invalidateSharedMembership,
        createSessionStore = createSessionStore,
    )
}

private fun reportUnexpectedGroupError(error: Exception) {
    println("Unexpected group flow error: ${error::class.simpleName}")
}
