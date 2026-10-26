package com.pheeeew.feature.screens.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

internal const val FEATURE_GUIDE_PAGE_COUNT = 3

/** Explanatory screens never open recording, request permissions, or submit a user's emotion. */
@Stable
internal class FeatureGuideState(
    pending: Boolean,
    private val onFinished: () -> Unit,
) {
    var pending by mutableStateOf(pending)
        private set
    var page by mutableStateOf(0)
        private set
    val isLastPage: Boolean get() = page == FEATURE_GUIDE_PAGE_COUNT - 1

    fun start() {
        page = 0
        pending = true
    }

    fun next() {
        if (pending && !isLastPage) page++
    }

    fun back() {
        if (pending && page > 0) page--
    }

    fun finish() {
        if (!pending || !isLastPage) return
        onFinished()
        pending = false
    }

    companion object {
        fun saver(
            onFinished: () -> Unit,
            canResume: () -> Boolean = { true },
        ) = listSaver<FeatureGuideState, Any>(
            save = { listOf(it.pending, it.page) },
            restore = {
                FeatureGuideState((it[0] as? Boolean ?: true) && canResume(), onFinished).apply {
                    page = (it[1] as? Int ?: 0).coerceIn(0, FEATURE_GUIDE_PAGE_COUNT - 1)
                }
            },
        )
    }
}
