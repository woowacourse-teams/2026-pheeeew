package com.pheeeew.feature.emotion.component

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberTapReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver

    fun isAnimatorDurationDisabled(): Boolean =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    var reduced by remember(resolver) { mutableStateOf(isAnimatorDurationDisabled()) }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reduced = isAnimatorDurationDisabled()
                }
            }
        val settingUri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        resolver.registerContentObserver(settingUri, false, observer)
        reduced = isAnimatorDurationDisabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
