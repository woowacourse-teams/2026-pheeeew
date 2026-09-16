package com.pheeeew.core.permission

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

@OptIn(ExperimentalForeignApi::class)
class IosLocationPermissionController(
    providedLocationManager: CLLocationManager? = null,
) : LocationPermissionController {
    private val locationManager by lazy { providedLocationManager ?: CLLocationManager() }
    private val permissionDelegate by lazy { IosPermissionDelegate(locationManager) }

    override suspend fun currentStatus(): LocationPermissionStatus =
        withContext(Dispatchers.Main) {
            locationManager.authorizationStatus.toCommonStatus(
                locationServicesEnabled = CLLocationManager.locationServicesEnabled(),
            )
        }

    override suspend fun requestPermission(): LocationPermissionStatus =
        withContext(Dispatchers.Main) {
            // 앱 권한이 아직 결정되지 않았다면 위치 서비스가 꺼져 있어도
            // 앱 권한 시스템 팝업을 먼저 요청합니다. 이후 결과를 확인해
            // 위치 서비스가 꺼진 상태는 ServicesDisabled로 노출합니다.
            permissionDelegate.requestPermission()
        }
}

@OptIn(ExperimentalForeignApi::class)
private class IosPermissionDelegate(
    private val locationManager: CLLocationManager,
) : NSObject(),
    CLLocationManagerDelegateProtocol {
    private var continuation: CancellableContinuation<LocationPermissionStatus>? = null

    suspend fun requestPermission(): LocationPermissionStatus {
        val nativeStatus = locationManager.authorizationStatus
        if (nativeStatus != kCLAuthorizationStatusNotDetermined) {
            return nativeStatus.toCommonStatus(locationServicesEnabled = true)
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
        finishPermissionRequest(manager.authorizationStatus)
    }

    @Suppress("DEPRECATION")
    override fun locationManager(
        manager: CLLocationManager,
        didChangeAuthorizationStatus: CLAuthorizationStatus,
    ) {
        finishPermissionRequest(didChangeAuthorizationStatus)
    }

    private fun finishPermissionRequest(status: CLAuthorizationStatus) {
        if (status == kCLAuthorizationStatusNotDetermined) return

        val requestContinuation = continuation ?: return
        continuation = null
        locationManager.delegate = null

        if (requestContinuation.isActive) {
            requestContinuation.resume(
                status.toCommonStatus(
                    locationServicesEnabled =
                        CLLocationManager.locationServicesEnabled(),
                ),
            )
        }
    }
}

internal fun CLAuthorizationStatus.toCommonStatus(locationServicesEnabled: Boolean): LocationPermissionStatus {
    if (!locationServicesEnabled) {
        return LocationPermissionStatus.ServicesDisabled
    }

    return when (this) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse,
        -> LocationPermissionStatus.Granted

        kCLAuthorizationStatusDenied,
        kCLAuthorizationStatusRestricted,
        -> LocationPermissionStatus.PermanentlyDenied

        else -> LocationPermissionStatus.Denied
    }
}
