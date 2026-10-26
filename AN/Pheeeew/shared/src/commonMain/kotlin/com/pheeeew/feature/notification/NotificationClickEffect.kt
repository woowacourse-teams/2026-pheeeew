package com.pheeeew.feature.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.core.navigation.NotificationClickRouter
import com.pheeeew.core.navigation.NotificationDestination
import com.pheeeew.core.notification.NotificationClickInbox
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull

/** Wait for app/navigation readiness and resume before handling a notification click. */
@Composable
internal fun NotificationClickEffect(
    inbox: NotificationClickInbox?,
    ready: Boolean,
    onNavigate: (NotificationDestination, String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigate by rememberUpdatedState(onNavigate)

    DisposableEffect(inbox, ready) {
        inbox?.setReady(ready)
        onDispose { inbox?.setReady(false) }
    }

    LaunchedEffect(inbox, ready, lifecycleOwner) {
        if (inbox == null || !ready) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            inbox.pendingClick.filterNotNull().collect { click ->
                navigate(NotificationClickRouter.destinationFor(click.target), click.notificationId)
                // Leave the click pending if navigation fails instead of acknowledging it early.
                inbox.acknowledge(click.notificationId)
            }
        }
    }
}
