package com.pheeeew

import androidx.activity.ComponentActivity
import com.pheeeew.core.update.AppUpdateController
import com.pheeeew.update.AndroidAppUpdateController

internal fun createAppUpdateController(activity: ComponentActivity): AppUpdateController =
    AndroidAppUpdateController(activity)
