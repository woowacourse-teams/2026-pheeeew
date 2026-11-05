package com.pheeeew.feature.screens.collection.coverflow

sealed interface CollectionUnavailableState {
    data object Waiting : CollectionUnavailableState

    data class Error(
        val onRetry: () -> Unit,
    ) : CollectionUnavailableState

    data object Empty : CollectionUnavailableState
}
