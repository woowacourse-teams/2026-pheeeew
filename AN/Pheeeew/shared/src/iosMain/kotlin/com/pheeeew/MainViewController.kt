package com.pheeeew

import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.di.IosApiDependencies

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        App(apiDependencies = IosApiDependencies.instance)
    }
