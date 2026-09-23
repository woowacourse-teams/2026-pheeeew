package com.pheeeew.legacy.core.location

import com.pheeeew.legacy.domain.model.location.CurrentLocation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val UNIX_TO_REFERENCE_SECONDS = 978_307_200.0

@OptIn(ExperimentalForeignApi::class)
class IosPlatformLocationProvider(
    providedLocationManager: CLLocationManager? = null,
) : PlatformLocationProvider {
    private val locationManager by lazy { providedLocationManager ?: CLLocationManager() }
    private val locationDelegate by lazy { IosLocationDelegate(locationManager) }

    override suspend fun getCurrentLocation(): PlatformLocationResult =
        withContext(Dispatchers.Main) {
            if (!CLLocationManager.locationServicesEnabled()) {
                return@withContext PlatformLocationResult.GpsUnavailable
            }

            locationDelegate.getCurrentLocation()
        }
}

@OptIn(ExperimentalForeignApi::class)
private class IosLocationDelegate(
    private val locationManager: CLLocationManager,
) : NSObject(),
    CLLocationManagerDelegateProtocol {
    private var continuation: CancellableContinuation<PlatformLocationResult>? = null

    suspend fun getCurrentLocation(): PlatformLocationResult =
        suspendCancellableCoroutine<PlatformLocationResult> { locationContinuation ->
            continuation?.cancel()
            continuation = locationContinuation
            locationManager.delegate = this
            locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            locationContinuation.invokeOnCancellation {
                dispatch_async(dispatch_get_main_queue()) {
                    if (continuation === locationContinuation) {
                        continuation = null
                        locationManager.delegate = null
                        locationManager.stopUpdatingLocation()
                    }
                }
            }
            locationManager.requestLocation()
        }

    override fun locationManager(
        manager: CLLocationManager,
        didUpdateLocations: List<*>,
    ) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation
        val result = location?.toPlatformResult() ?: PlatformLocationResult.GpsUnavailable
        finish(result)
    }

    override fun locationManager(
        manager: CLLocationManager,
        didFailWithError: NSError,
    ) {
        finish(PlatformLocationResult.GpsUnavailable)
    }

    private fun finish(result: PlatformLocationResult) {
        val locationContinuation = continuation ?: return
        continuation = null
        locationManager.delegate = null
        locationManager.stopUpdatingLocation()
        if (locationContinuation.isActive) {
            locationContinuation.resume(result)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toPlatformResult(): PlatformLocationResult {
    val (latitude, longitude) = coordinate.useContents { latitude to longitude }
    val capturedDate: NSDate = timestamp
    val capturedAtMillis =
        ((capturedDate.timeIntervalSinceReferenceDate + UNIX_TO_REFERENCE_SECONDS) * 1_000.0).toLong()
    return iosPlatformLocationResult(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = horizontalAccuracy,
        capturedAtMillis = capturedAtMillis,
    )
}

internal fun iosPlatformLocationResult(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Double,
    capturedAtMillis: Long,
): PlatformLocationResult {
    if (
        !latitude.isFinite() ||
        !longitude.isFinite() ||
        latitude !in -90.0..90.0 ||
        longitude !in -180.0..180.0 ||
        !accuracyMeters.isFinite() ||
        accuracyMeters < 0.0 ||
        accuracyMeters > Float.MAX_VALUE.toDouble() ||
        capturedAtMillis <= 0L
    ) {
        return PlatformLocationResult.GpsUnavailable
    }

    return PlatformLocationResult.Success(
        CurrentLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters.toFloat(),
            capturedAtMillis = capturedAtMillis,
        ),
    )
}
