package com.pheeeew.data.location.platform.android

import androidx.lifecycle.ViewModel
import com.pheeeew.core.di.LocationDependencies

class LocationDependenciesHolder : ViewModel() {
    var dependencies: LocationDependencies? = null

    override fun onCleared() {
        (dependencies?.permissionController as? AutoCloseable)?.close()
        dependencies = null
        super.onCleared()
    }
}
