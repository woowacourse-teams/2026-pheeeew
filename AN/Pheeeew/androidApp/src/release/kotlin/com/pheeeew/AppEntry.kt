package com.pheeeew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.di.AppGraph

@Composable
internal fun AppEntry(
    activity: ComponentActivity,
    appGraph: AppGraph,
    owner: ActivityRetainedGraphOwner,
) {
    AndroidAppContent(activity, appGraph, owner)
}
