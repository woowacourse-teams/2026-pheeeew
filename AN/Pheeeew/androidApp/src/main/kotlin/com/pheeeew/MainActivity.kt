package com.pheeeew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pheeeew.core.di.ActivityRetainedGraphOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appGraph = (application as PheeeewApplication).appGraph
        val uiGraph = ActivityRetainedGraphOwner.get(this, appGraph).attach(this)
        setContent {
            DeveloperToolsEntry(appGraph.apiDependencies.buildConfig) { onOpenDeveloperTools ->
                App(appGraph = appGraph, uiGraph = uiGraph, onOpenDeveloperTools = onOpenDeveloperTools)
            }
        }
    }
}
