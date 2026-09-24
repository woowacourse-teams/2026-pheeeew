package com.pheeeew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.pheeeew.data.location.platform.android.LocationDependenciesHolder
import com.pheeeew.data.location.platform.android.createAndroidLocationDependencies

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dependenciesHolder = ViewModelProvider(this)[LocationDependenciesHolder::class.java]
        val locationDependencies =
            createAndroidLocationDependencies(
                activity = this,
                retainedDependencies = dependenciesHolder.dependencies,
            ).also { dependenciesHolder.dependencies = it }
        setContent {
            App(locationDependencies = locationDependencies)
        }
    }
}
