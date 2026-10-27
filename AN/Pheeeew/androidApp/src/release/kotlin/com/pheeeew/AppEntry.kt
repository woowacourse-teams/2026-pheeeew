package com.pheeeew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.di.AppGraph
import com.pheeeew.core.notification.NotificationClickInbox
import com.pheeeew.core.notification.NotificationMessageInbox

@Composable
internal fun AppEntry(
    activity: ComponentActivity,
    appGraph: AppGraph,
    owner: ActivityRetainedGraphOwner,
    notificationClicks: NotificationClickInbox,
    notificationMessages: NotificationMessageInbox,
) {
    AndroidAppContent(activity, appGraph, owner, notificationClicks, notificationMessages)
}
