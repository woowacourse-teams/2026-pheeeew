package com.pheeeew.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@Composable
fun MapRoute(
    onSettingsClick: () -> Unit,
    onMapReady: () -> Unit,
    viewModel: MapViewModel,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        onEnsureLocationPermission = { viewModel.ensureLocationPermission(refreshLocation = true) },
        onOpenLocationSettings = viewModel::openLocationSettings,
        onOpenAppSettings = viewModel::openAppSettings,
        onMapError = viewModel::onMapError,
        onMapReady = onMapReady,
        isActive = isActive,
        modifier = modifier,
    )
}
