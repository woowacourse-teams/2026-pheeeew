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
import com.pheeeew.feature.screens.group.create.GroupCreateRoute
import com.pheeeew.feature.screens.group.create.GroupCreateViewModel
import com.pheeeew.feature.screens.group.detail.GroupCopyCodeResult
import com.pheeeew.feature.screens.group.detail.GroupDetailRoute
import com.pheeeew.feature.screens.group.detail.GroupDetailViewModel
import com.pheeeew.feature.screens.group.home.GroupHomeRoute
import com.pheeeew.feature.screens.group.home.GroupHomeViewModel
import com.pheeeew.feature.screens.group.members.GroupMembersRoute
import com.pheeeew.feature.screens.group.members.GroupMembersViewModel
import com.pheeeew.feature.screens.group.model.GroupId
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

@Composable
@Suppress("DEPRECATION")
fun GroupFeatureHost(
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
            val homeViewModel = rememberGroupHomeViewModel(entry)
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
            val homeViewModel = rememberGroupHomeViewModel(homeBackStackEntry)
            val createViewModel: GroupCreateViewModel =
                metroViewModel()
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
            val homeViewModel = rememberGroupHomeViewModel(homeBackStackEntry)
            val detailViewModel: GroupDetailViewModel =
                assistedMetroViewModel<GroupDetailViewModel, GroupDetailViewModel.Factory>(
                    viewModelStoreOwner = entry,
                ) {
                    create(groupId)
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
                onJoined = onMembershipChanged,
                onMembersClick = {
                    if (navController.currentBackStackEntry == entry) {
                        navController.navigate(GroupMembersDestination(groupId.value)) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable<GroupMembersDestination> { entry ->
            val destination = entry.toRoute<GroupMembersDestination>()
            val groupId = GroupId(destination.groupId)
            val membersViewModel: GroupMembersViewModel =
                assistedMetroViewModel<GroupMembersViewModel, GroupMembersViewModel.Factory>(
                    viewModelStoreOwner = entry,
                ) {
                    create(groupId)
                }
            GroupMembersRoute(
                viewModel = membersViewModel,
                onBack = { navController.popBackStackIfCurrent(entry) },
            )
        }
    }
}

@Composable
private fun rememberGroupHomeViewModel(entry: NavBackStackEntry): GroupHomeViewModel =
    metroViewModel(viewModelStoreOwner = entry, key = GROUP_HOME_VIEW_MODEL_KEY)

private fun NavController.popBackStackIfCurrent(entry: NavBackStackEntry) {
    if (currentBackStackEntry == entry) popBackStack()
}

private const val GROUP_HOME_VIEW_MODEL_KEY = "group-home"
