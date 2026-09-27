package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberTapReducedMotion(): Boolean {
    var reduced by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer =
            center.addObserverForName(
                UIAccessibilityReduceMotionStatusDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) {
                reduced = UIAccessibilityIsReduceMotionEnabled()
            }
        onDispose { center.removeObserver(observer) }
    }
    return reduced
}
