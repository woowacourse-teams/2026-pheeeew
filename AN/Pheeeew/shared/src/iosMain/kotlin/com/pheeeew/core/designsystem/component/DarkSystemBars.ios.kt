package com.pheeeew.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.uikit.LocalUIViewController
import platform.UIKit.UIUserInterfaceStyle

@Composable
internal actual fun DarkSystemBarsEffect() {
    val controller = LocalUIViewController.current
    DisposableEffect(controller) {
        val window = controller.view.window
        val previous = window?.overrideUserInterfaceStyle
        window?.overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
        onDispose {
            if (previous != null) window.overrideUserInterfaceStyle = previous
        }
    }
}
