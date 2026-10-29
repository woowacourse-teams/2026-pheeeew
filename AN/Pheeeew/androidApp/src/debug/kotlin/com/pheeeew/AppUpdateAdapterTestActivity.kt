package com.pheeeew

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Instrumentation installs the driver before the Activity is started. */
class AppUpdateAdapterTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configure?.invoke(this)
    }

    companion object {
        var configure: ((ComponentActivity) -> Unit)? = null
    }
}
