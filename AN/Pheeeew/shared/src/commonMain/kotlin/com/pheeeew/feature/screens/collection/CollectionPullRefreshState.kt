package com.pheeeew.feature.screens.collection

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Gesture position is independent of the network request and its loading indicator. */
internal class CollectionPullRefreshState(
    private val scope: CoroutineScope,
    private val threshold: Float,
    private val isEnabled: () -> Boolean,
    private val onRefresh: () -> Unit,
) : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set

    private var returnJob: Job? = null
    private var pulled = false
    private var consumeFling = false

    fun beginGesture() {
        consumeFling = false
        returnJob?.cancel()
    }

    private fun drag(delta: Float): Offset {
        returnJob?.cancel()
        val previous = offset
        offset = (offset + delta).coerceIn(0f, threshold * 2)
        pulled = pulled || offset > 0f
        return Offset(0f, offset - previous)
    }

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        if (source == NestedScrollSource.UserInput && offset > 0f && available.y < 0f) {
            drag(available.y)
        } else {
            Offset.Zero
        }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        if (source == NestedScrollSource.UserInput && isEnabled() && available.y > 0f) {
            drag(available.y)
        } else {
            Offset.Zero
        }

    fun release(cancelled: Boolean = false) {
        val shouldRefresh = !cancelled && pulled && offset >= threshold && isEnabled()
        consumeFling = pulled
        pulled = false
        returnJob?.cancel()
        if (offset > 0f) {
            returnJob = scope.launch { animate(offset, 0f, animationSpec = tween(220)) { value, _ -> offset = value } }
        }
        if (shouldRefresh) onRefresh()
    }

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (consumeFling) Velocity(0f, available.y) else Velocity.Zero
}
