package com.pheeeew.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@Composable
fun MapRoute(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    viewModel: MapViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    var startupPermissionsChecked by remember { mutableStateOf(false) }
    val lifeCycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifeCycleOwner, isActive) {
        if (!isActive) {
            viewModel.onMapBackground()
            return@LaunchedEffect
        }

        lifeCycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onMapForeground()

            try {
                if (!startupPermissionsChecked) {
                    viewModel.ensureLocationPermission(refreshLocation = false)
                    startupPermissionsChecked = true
                    launch { viewModel.refreshLocationPermission() }
                } else {
                    viewModel.refreshLocationPermission()
                }
                awaitCancellation()
            } finally {
                viewModel.onMapBackground()
            }
        }
    }

    MapScreen(
        uiState = uiState,
        onSettingsClick = onSettingsClick,
        onZoomInClick = viewModel::onZoomInClick,
        onZoomOutClick = viewModel::onZoomOutClick,
        onMyLocationClick = viewModel::onMyLocationClick,
        onBoundsChanged = viewModel::loadSighs,
        onRegisterSighAfterExplosion = viewModel::registerSighAfterExplosion,
        onCancelFailedSighRegistration = viewModel::cancelFailedSighRegistration,
        onConsumeFocusRequest = viewModel::consumeFocusRequest,
        onEnsureLocationPermission = { viewModel.ensureLocationPermission() },
        onOpenLocationSettings = viewModel::openLocationSettings,
        onOpenAppSettings = viewModel::openAppSettings,
        onMapError = viewModel::onMapError,
        isActive = isActive,
        modifier = modifier,
    )
}
