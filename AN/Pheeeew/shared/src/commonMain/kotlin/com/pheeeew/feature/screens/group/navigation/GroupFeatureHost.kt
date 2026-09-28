package com.pheeeew.feature.screens.group.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
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

/** Owns the group-only back stack and reports when its detail destination is active. */
@Composable
@Suppress("DEPRECATION")
fun GroupFeatureHost(
    dependencies: GroupDependencies,
    modifier: Modifier = Modifier,
    onGroupDetailVisibilityChanged: (Boolean) -> Unit = {},
    onGroupCreateVisibilityChanged: (Boolean) -> Unit = {},
    onMembershipChanged: () -> Unit = {},
) {
    val navController = rememberNavController()
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(currentBackStackEntry?.destination?.route) {
        val detailRoute = GroupDetailDestination::class.qualifiedName.orEmpty()
        val currentRoute = currentBackStackEntry?.destination?.route.orEmpty()
        onGroupDetailVisibilityChanged(currentRoute.startsWith(detailRoute))
        val createRoute = GroupCreateDestination::class.qualifiedName.orEmpty()
        onGroupCreateVisibilityChanged(currentRoute.startsWith(createRoute))
    }

    NavHost(
        navController = navController,
        startDestination = GroupHomeDestination,
        modifier = modifier,
    ) {
        composable<GroupHomeDestination> { entry ->
            val homeViewModel = rememberGroupHomeViewModel(entry, dependencies)
            GroupHomeRoute(
                viewModel = homeViewModel,
                isCurrentDestination = currentBackStackEntry == entry,
                onCreateClick = { navController.navigate(GroupCreateDestination) },
                onGroupClick = { groupId -> navController.navigate(GroupDetailDestination(groupId.value)) },
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
                    )
                }
            GroupCreateRoute(
                viewModel = createViewModel,
                isCurrentDestination = currentBackStackEntry == entry,
                onBack = { navController.popBackStack() },
                onCreated = { groupId, _ ->
                    onMembershipChanged()
                    homeViewModel.invalidateMembership()
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
                viewModel {
                    GroupDetailViewModel(
                        groupId = groupId,
                        dependencies = dependencies.detail,
                    )
                }

            fun returnHomeAndRefresh(removedGroupId: GroupId) {
                onMembershipChanged()
                homeViewModel.invalidateMembership()
                homeViewModel.removeGroup(removedGroupId)
                navController.popBackStack<GroupHomeDestination>(inclusive = false)
            }

            GroupDetailRoute(
                viewModel = detailViewModel,
                isCurrentDestination = currentBackStackEntry == entry,
                onBack = { navController.popBackStack() },
                onReturnHome = { returnHomeAndRefresh(groupId) },
                onLeft = { leftGroupId, _ -> returnHomeAndRefresh(leftGroupId) },
                onMembershipUnavailable = { unavailableGroupId, _, _ ->
                    returnHomeAndRefresh(unavailableGroupId)
                },
                onCopyCode = { code, _ ->
                    clipboardManager.setText(AnnotatedString(code))
                    GroupCopyCodeResult.Copied
                },
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
        )
    }

private const val GROUP_HOME_VIEW_MODEL_KEY = "group-home"
