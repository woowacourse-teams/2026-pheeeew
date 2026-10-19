package com.pheeeew.core.di

import com.pheeeew.core.di.group.GroupDependencies
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.data.repository.GroupCreateRepositoryImpl
import com.pheeeew.data.repository.GroupExploreRepositoryImpl
import com.pheeeew.data.repository.GroupJoinRepositoryImpl
import com.pheeeew.data.repository.GroupMembersRepositoryImpl
import com.pheeeew.data.repository.GroupPublicJoinRepositoryImpl
import com.pheeeew.domain.repository.EmotionRepository
import com.pheeeew.domain.repository.GroupCreateRepository
import com.pheeeew.domain.repository.GroupDetailRepository
import com.pheeeew.domain.repository.GroupExploreRepository
import com.pheeeew.domain.repository.GroupJoinRepository
import com.pheeeew.domain.repository.GroupListRepository
import com.pheeeew.domain.repository.GroupMembersRepository
import com.pheeeew.domain.repository.GroupPublicJoinRepository
import com.pheeeew.domain.repository.GroupStampListRepository
import com.pheeeew.feature.screens.group.adapter.ApiCreateGroupAction
import com.pheeeew.feature.screens.group.adapter.ApiGroupDetailSource
import com.pheeeew.feature.screens.group.adapter.ApiGroupExploreSource
import com.pheeeew.feature.screens.group.adapter.ApiGroupListSource
import com.pheeeew.feature.screens.group.adapter.ApiGroupMembersSource
import com.pheeeew.feature.screens.group.adapter.ApiJoinGroupAction
import com.pheeeew.feature.screens.group.adapter.ApiLeaveGroupAction
import com.pheeeew.feature.screens.group.adapter.ApiLookupGroupAction
import com.pheeeew.feature.screens.group.adapter.ApiPublicGroupJoinAction
import com.pheeeew.feature.screens.group.adapter.GroupListCreateRecoveryAction
import com.pheeeew.feature.screens.group.create.GroupCreateActions
import com.pheeeew.feature.screens.group.create.GroupCreateSessionStore
import com.pheeeew.feature.screens.group.create.GroupCreateViewModel
import com.pheeeew.feature.screens.group.detail.GroupDetailDependencies
import com.pheeeew.feature.screens.group.home.GroupHomeViewModel
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.home.explore.GroupExploreSource
import com.pheeeew.feature.screens.group.join.GroupJoinDependencies
import com.pheeeew.feature.screens.group.members.GroupMembersSource
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import com.pheeeew.feature.screens.group.monitoring.MonitoredCreateGroupAction
import dev.zacsweers.metro.Provides

internal interface GroupBindings {
    @Provides fun provideGroupStamps(services: UiServices): GroupStampListRepository = services.groupStamps

    @Provides fun provideGroupListRepository(services: UiServices): GroupListRepository = services.groupList

    @Provides fun provideGroupDetailRepository(services: UiServices): GroupDetailRepository = services.groupDetail

    @Provides fun provideOperationKeys(services: UiServices): GroupOperationKeyAllocator = services.operationKeys

    @Provides fun provideJoinRepository(impl: GroupJoinRepositoryImpl): GroupJoinRepository = impl

    @Provides fun provideCreateRepository(impl: GroupCreateRepositoryImpl): GroupCreateRepository = impl

    @Provides fun provideExploreRepository(impl: GroupExploreRepositoryImpl): GroupExploreRepository = impl

    @Provides fun provideMembersRepository(impl: GroupMembersRepositoryImpl): GroupMembersRepository = impl

    @Provides fun providePublicJoinRepository(impl: GroupPublicJoinRepositoryImpl): GroupPublicJoinRepository = impl

    @Provides fun provideGroupListSource(impl: ApiGroupListSource): GroupListSource = impl

    @Provides fun provideExploreSource(impl: ApiGroupExploreSource): GroupExploreSource = impl

    @Provides fun provideMembersSource(impl: ApiGroupMembersSource): GroupMembersSource = impl

    @Provides fun provideCreateActions(
        action: ApiCreateGroupAction,
        recovery: GroupListCreateRecoveryAction,
        monitoring: Monitoring,
    ): GroupCreateActions = GroupCreateActions(MonitoredCreateGroupAction(action, monitoring), recovery)

    @Provides fun provideJoin(
        lookup: ApiLookupGroupAction,
        join: ApiJoinGroupAction,
        keys: GroupOperationKeyAllocator,
        monitoring: Monitoring,
    ): GroupJoinDependencies =
        GroupJoinDependencies(lookup, join, { reportUnexpected(it) }, keys, monitoring = monitoring)

    @Provides fun provideDetail(
        source: ApiGroupDetailSource,
        leave: ApiLeaveGroupAction,
        publicJoin: ApiPublicGroupJoinAction,
        codeJoin: GroupJoinDependencies,
        emotions: EmotionRepository,
        keys: GroupOperationKeyAllocator,
        monitoring: Monitoring,
    ): GroupDetailDependencies =
        GroupDetailDependencies(
            source,
            emotions,
            leave,
            { reportUnexpected(it) },
            keys,
            publicJoinAction = publicJoin,
            codeJoin = codeJoin,
            monitoring = monitoring,
        )

    @Provides fun provideGroups(
        list: GroupListSource,
        actions: GroupCreateActions,
        keys: GroupOperationKeyAllocator,
        join: GroupJoinDependencies,
        detail: GroupDetailDependencies,
        stamps: GroupStampListRepository,
        store: GroupCreateSessionStore,
        explore: GroupExploreSource,
        members: GroupMembersSource,
    ): GroupDependencies =
        GroupDependencies(
            list,
            actions,
            { reportUnexpected(it) },
            keys,
            join,
            detail,
            stamps.membershipChanges,
            stamps::invalidate,
            store,
            explore,
            members,
        )

    @Provides fun provideGroupHome(dependencies: GroupDependencies): GroupHomeViewModel =
        GroupHomeViewModel(
            dependencies.groupListSource,
            dependencies.join,
            dependencies.membershipChanges,
            dependencies.invalidateSharedMembership,
            dependencies.exploreSource,
        )

    @Provides fun provideGroupCreate(dependencies: GroupDependencies): GroupCreateViewModel =
        GroupCreateViewModel(
            dependencies.createActions.create,
            dependencies.createErrorReporter,
            dependencies.operationKeyAllocator,
            findCandidatesAction = dependencies.createActions.findCandidates,
            sessionStore = dependencies.createSessionStore,
            monitoring = dependencies.join.monitoring,
        )
}

private fun reportUnexpected(error: Exception) {
    println("Unexpected group flow error: ${error::class.simpleName}")
}
