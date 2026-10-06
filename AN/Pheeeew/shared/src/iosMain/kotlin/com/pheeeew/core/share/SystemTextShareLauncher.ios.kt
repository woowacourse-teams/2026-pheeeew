@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.pheeeew.core.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberSystemTextShareLauncher(): SystemTextShareLauncher =
    remember {
        SystemTextShareLauncher { text ->
            val rootViewController =
                UIApplication.sharedApplication.connectedScenes
                    .filterIsInstance<UIWindowScene>()
                    .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
                    .firstOrNull { window -> window.isKeyWindow() }
                    ?.rootViewController
                    ?: return@SystemTextShareLauncher false
            var presenter = rootViewController
            while (presenter.presentedViewController != null) {
                presenter = presenter.presentedViewController!!
            }

            val shareController = UIActivityViewController(listOf(text), null)
            shareController.popoverPresentationController()?.let { popover ->
                popover.sourceView = presenter.view
                popover.sourceRect = presenter.view.bounds
            }
            presenter.presentViewController(shareController, animated = true, completion = null)
            true
        }
    }
