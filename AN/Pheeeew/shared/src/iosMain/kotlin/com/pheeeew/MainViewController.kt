package com.pheeeew

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.IosApiDependencies
import com.pheeeew.data.location.platform.ios.createIosLocationDependencies

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val locationDependencies = remember { createIosLocationDependencies() }
        App(locationDependencies = locationDependencies, apiDependencies = IosApiDependencies.instance)
    }
