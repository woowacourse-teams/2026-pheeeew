package com.pheeeew

import androidx.compose.ui.window.ComposeUIViewController
import com.pheeeew.core.network.ApiConfig
import com.pheeeew.di.SighModule
import com.pheeeew.di.createIosLocationDependencies
import platform.Foundation.NSBundle

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val appVersion = NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "-"
        val apiBaseUrl = NSBundle.mainBundle.infoDictionary?.get("API_BASE_URL") as? String ?: ""
        val locationDependencies = createIosLocationDependencies()
        val sighRepository = SighModule.create(ApiConfig(baseUrl = apiBaseUrl))
        App(
            appVersion = appVersion,
            locationDependencies = locationDependencies,
            sighRepository = sighRepository,
            mapPerformanceLogger = {},
        )
    }
