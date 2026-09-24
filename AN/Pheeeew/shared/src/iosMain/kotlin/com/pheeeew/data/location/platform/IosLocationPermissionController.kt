package com.pheeeew.data.location.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus

@OptIn(ExperimentalForeignApi::class)
class IosLocationPermissionController(
    private val locationManager: CLLocationManager = CLLocationManager(),
) : LocationPermissionController {
    private val delegate = PermissionDelegate(locationManager)

    override suspend fun currentStatus(): LocationPermissionStatus = withContext(Dispatchers.Main) {
        locationManager.authorizationStatus.toLocationPermissionStatus(
            CLLocationManager.locationServicesEnabled(),
        )
    }

    override suspend fun requestPermission(): LocationPermissionStatus = withContext(Dispatchers.Main) {
        delegate.requestPermission()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PermissionDelegate(
    private val locationManager: CLLocationManager,
) : NSObject(), CLLocationManagerDelegateProtocol {
    private var continuation: CancellableContinuation<LocationPermissionStatus>? = null

    suspend fun requestPermission(): LocationPermissionStatus {
        val status = locationManager.authorizationStatus
        if (status != kCLAuthorizationStatusNotDetermined) {
            return status.toLocationPermissionStatus(locationServicesEnabled = true)
        }
        return suspendCancellableCoroutine { requestContinuation ->
            continuation?.cancel()
            continuation = requestContinuation
            locationManager.delegate = this
            requestContinuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (continuation === requestContinuation) {
                        continuation = null
                        locationManager.delegate = null
                    }
                }
            }
            locationManager.requestWhenInUseAuthorization()
        }
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        finish(manager.authorizationStatus)
    }

    @Suppress("DEPRECATION")
    override fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: CLAuthorizationStatus) {
        finish(didChangeAuthorizationStatus)
    }

    private fun finish(status: CLAuthorizationStatus) {
        if (status == kCLAuthorizationStatusNotDetermined) return
        val requestContinuation = continuation ?: return
        continuation = null
        locationManager.delegate = null
        if (requestContinuation.isActive) {
            requestContinuation.resume(
                status.toLocationPermissionStatus(CLLocationManager.locationServicesEnabled()),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLAuthorizationStatus.toLocationPermissionStatus(
    locationServicesEnabled: Boolean,
): LocationPermissionStatus {
    if (!locationServicesEnabled) return LocationPermissionStatus.ServicesDisabled
    return when (this) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse -> LocationPermissionStatus.Granted
        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted -> LocationPermissionStatus.PermanentlyDenied
        else -> LocationPermissionStatus.Denied
    }
}
