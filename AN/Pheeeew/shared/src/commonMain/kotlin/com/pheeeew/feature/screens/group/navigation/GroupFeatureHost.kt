package com.pheeeew.feature.screens.group.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pheeeew.core.di.group.GroupDependencies
import com.pheeeew.feature.screens.group.create.GroupCreateRoute
import com.pheeeew.feature.screens.group.create.GroupCreateViewModel
import com.pheeeew.feature.screens.group.detail.GroupCopyCodeResult
import com.pheeeew.feature.screens.group.detail.GroupDetailRoute
import com.pheeeew.feature.screens.group.detail.GroupDetailViewModel
import com.pheeeew.feature.screens.group.home.GroupHomeRoute
import com.pheeeew.feature.screens.group.home.GroupHomeViewModel
import com.pheeeew.feature.screens.group.model.GroupId

@Composable
@Suppress("DEPRECATION")
fun GroupFeatureHost(
    dependencies: GroupDependencies,
    modifier: Modifier = Modifier,
    onGroupDetailVisibilityChanged: (Boolean) -> Unit = {},
    onGroupCreateVisibilityChanged: (Boolean) -> Unit = {},
    onMembershipChanged: () -> Unit = {},
    onRefreshActionChanged: ((() -> Unit)?) -> Unit = {},
) {
    val navController = rememberNavController()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val clipboardManager = LocalClipboardManager.current
    val currentOnRefreshActionChanged by rememberUpdatedState(onRefreshActionChanged)

    LaunchedEffect(currentBackStackEntry?.destination) {
        val destination = currentBackStackEntry?.destination
        onGroupDetailVisibilityChanged(destination?.hasRoute<GroupDetailDestination>() == true)
        onGroupCreateVisibilityChanged(destination?.hasRoute<GroupCreateDestination>() == true)
    }

    NavHost(
        navController = navController,
        startDestination = GroupHomeDestination,
        modifier = modifier,
    ) {
        composable<GroupHomeDestination> { entry ->
            val homeViewModel = rememberGroupHomeViewModel(entry, dependencies)
            DisposableEffect(homeViewModel) {
                currentOnRefreshActionChanged(homeViewModel::refresh)
                onDispose { currentOnRefreshActionChanged(null) }
            }
            GroupHomeRoute(
                viewModel = homeViewModel,
                isCurrentDestination = currentBackStackEntry == entry,
                onCreateClick = { navController.navigate(GroupCreateDestination) },
                onGroupClick = { groupId ->
                    // Read the live entry so repeated taps are ignored before recomposition.
                    if (navController.currentBackStackEntry == entry) {
                        navController.navigate(GroupDetailDestination(groupId.value)) {
                            launchSingleTop = true
                        }
                    }
                },
                onJoinSucceeded = { groupId, _ ->
                    onMembershipChanged()
                    navController.navigate(GroupDetailDestination(groupId.value)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<GroupCreateDestination> { entry ->
            val homeBackStackEntry = remember(navController) { navController.getBackStackEntry<GroupHomeDestination>() }
            val homeViewModel = rememberGroupHomeViewModel(homeBackStackEntry, dependencies)
            val createViewModel: GroupCreateViewModel =
                viewModel {
                    GroupCreateViewModel(
                        monitoring = dependencies.join.monitoring,
                        createGroupAction = dependencies.createActions.create,
                        errorReporter = dependencies.createErrorReporter,
                        operationKeyAllocator = dependencies.operationKeyAllocator,
                        findCandidatesAction = dependencies.createActions.findCandidates,
                        sessionStore = dependencies.createSessionStore,
                    )
                }
            GroupCreateRoute(
                viewModel = createViewModel,
                isCurrentDestination = currentBackStackEntry == entry,
                onBack = { navController.popBackStackIfCurrent(entry) },
                onCreated = { groupId, _ ->
                    onMembershipChanged()
                    navController.navigate(GroupDetailDestination(groupId.value)) {
                        popUpTo<GroupHomeDestination> { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<GroupDetailDestination> { entry ->
            val destination = entry.toRoute<GroupDetailDestination>()
            val groupId = GroupId(destination.groupId)
            val homeBackStackEntry = remember(navController) { navController.getBackStackEntry<GroupHomeDestination>() }
            val homeViewModel = rememberGroupHomeViewModel(homeBackStackEntry, dependencies)
            val detailViewModel: GroupDetailViewModel =
                viewModel(
                    viewModelStoreOwner = entry,
                ) {
                    GroupDetailViewModel(
                        groupId = groupId,
                        dependencies = dependencies.detail,
                    )
                }

            fun returnHomeAndRefresh(removedGroupId: GroupId) {
                onMembershipChanged()
                homeViewModel.removeGroup(removedGroupId)
                navController.popBackStack<GroupHomeDestination>(inclusive = false)
            }

            GroupDetailRoute(
                viewModel = detailViewModel,
                isCurrentDestination = currentBackStackEntry == entry,
                onBack = { navController.popBackStackIfCurrent(entry) },
                onReturnHome = { returnHomeAndRefresh(groupId) },
                onLeft = { leftGroupId, _ -> returnHomeAndRefresh(leftGroupId) },
                onMembershipUnavailable = { unavailableGroupId, _, _ ->
                    returnHomeAndRefresh(unavailableGroupId)
                },
                onCopyCode = { code, _ ->
                    clipboardManager.setText(AnnotatedString(code))
                    GroupCopyCodeResult.Copied
                },
                onMoodBlockClick = null,
                onMoodReportClick = null,
            )
        }
    }
}

@Composable
private fun rememberGroupHomeViewModel(
    entry: NavBackStackEntry,
    dependencies: GroupDependencies,
): GroupHomeViewModel =
    viewModel(
        viewModelStoreOwner = entry,
        key = GROUP_HOME_VIEW_MODEL_KEY,
    ) {
        GroupHomeViewModel(
            groupListSource = dependencies.groupListSource,
            groupJoinDependencies = dependencies.join,
            membershipChanges = dependencies.membershipChanges,
            invalidateSharedMembership = dependencies.invalidateSharedMembership,
        )
    }

private fun NavController.popBackStackIfCurrent(entry: NavBackStackEntry) {
    if (currentBackStackEntry == entry) popBackStack()
}

private const val GROUP_HOME_VIEW_MODEL_KEY = "group-home"
