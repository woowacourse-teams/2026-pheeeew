package com.pheeeew.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.first

/** Reports presentation only after the startup destination resumes and reaches a frame. */
@Composable
internal fun FirstMainPresentationEffect(
    backStackEntry: NavBackStackEntry?,
    ready: Boolean,
    target: NotificationDestination?,
    alreadyPresented: Boolean,
    onPresented: () -> Unit,
) {
    val notifyPresented by rememberUpdatedState(onPresented)
    LaunchedEffect(backStackEntry, ready, target, alreadyPresented) {
        val entry = backStackEntry ?: return@LaunchedEffect
        if (!ready || alreadyPresented) return@LaunchedEffect
        val targetReached =
            when (target) {
                is NotificationDestination.DailyRanking -> {
                    entry.destination.hasRoute<DailyPressRankingRootDestination>() &&
                        entry.toRoute<DailyPressRankingRootDestination>().date == target.date
                }

                NotificationDestination.Press -> {
                    entry.destination.hasRoute<PressRootDestination>()
                }

                is NotificationDestination.Collection -> {
                    entry.destination.hasRoute<CollectionRootDestination>()
                }

                NotificationDestination.Map, null -> {
                    entry.destination.hasRoute<MapRootDestination>()
                }
            }
        if (!targetReached) return@LaunchedEffect
        entry.lifecycle.currentStateFlow.first { it == Lifecycle.State.RESUMED }
        withFrameNanos { }
        notifyPresented()
    }
}
