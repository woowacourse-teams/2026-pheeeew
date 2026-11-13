package com.pheeeew.core.di

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** UI-owned models, independent from the process graph and its long-lived services. */
class AppUiSession :
    ViewModelStoreOwner,
    AutoCloseable {
    override val viewModelStore = ViewModelStore()

    override fun close() = viewModelStore.clear()
}
