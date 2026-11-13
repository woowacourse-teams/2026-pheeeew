package com.pheeeew.feature.screens.map.renderer

import androidx.compose.runtime.staticCompositionLocalOf

/** Optional style input supplied by a host; the native renderer still owns loading and callbacks. */
fun interface MapStyleOverrideProvider {
    fun consumeForNextLoad(): String?
}

private object NoMapStyleOverride : MapStyleOverrideProvider {
    override fun consumeForNextLoad(): String? = null
}

val LocalMapStyleOverrideProvider = staticCompositionLocalOf<MapStyleOverrideProvider> { NoMapStyleOverride }
