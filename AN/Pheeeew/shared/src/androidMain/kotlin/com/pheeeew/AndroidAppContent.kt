package com.pheeeew

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.pheeeew.core.di.ActivityRetainedGraphOwner
import com.pheeeew.core.di.AppGraph

@Composable
fun AndroidAppContent(
    activity: ComponentActivity,
    appGraph: AppGraph,
    owner: ActivityRetainedGraphOwner,
    settingsContent: @Composable () -> Unit = {},
) {
    val uiGraph = remember(owner) { owner.attach(activity) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner.uiSession) {
        App(appGraph, uiGraph, settingsContent)
    }
}
