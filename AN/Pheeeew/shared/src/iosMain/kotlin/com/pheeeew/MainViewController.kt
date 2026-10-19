package com.pheeeew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.IosGraphs

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { MainApp() }

@Composable
fun MainApp(onOpenDeveloperTools: (() -> Unit)? = null) {
    val appGraph = IosGraphs.app
    val uiGraph = remember(appGraph) { appGraph.controllerFactory.create() }
    DisposableEffect(uiGraph) { onDispose { uiGraph.locationDependencies.close() } }
    App(appGraph, uiGraph, onOpenDeveloperTools)
}
