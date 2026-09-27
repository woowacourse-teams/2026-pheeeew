package com.pheeeew

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pheeeew.core.di.ApiDependencies
import com.pheeeew.core.di.group.createGroupDependencies
import com.pheeeew.feature.screens.group.navigation.GroupFeatureHost

@Composable
fun App(apiDependencies: ApiDependencies) {
    LaunchedEffect(apiDependencies) { apiDependencies.prepareSession() }
    val groupDependencies = remember(apiDependencies) { createGroupDependencies(apiDependencies.client) }
    GroupFeatureHost(dependencies = groupDependencies, modifier = Modifier.fillMaxSize())
}
