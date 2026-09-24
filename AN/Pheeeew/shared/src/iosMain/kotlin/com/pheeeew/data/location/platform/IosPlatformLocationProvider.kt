package com.pheeeew.data.location.platform.ios

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
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.core.location.PlatformLocationProvider

@OptIn(ExperimentalForeignApi::class)
internal class IosPlatformLocationProvider(
    private val locationManager: CLLocationManager = CLLocationManager(),
) : PlatformLocationProvider {
    private val delegate = LocationDelegate(locationManager)

    override suspend fun getCurrentLocation(): CurrentLocation? = withContext(Dispatchers.Main) {
        if (!CLLocationManager.locationServicesEnabled()) null else delegate.requestLocation()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate(
    private val locationManager: CLLocationManager,
) : NSObject(), CLLocationManagerDelegateProtocol {
    private var continuation: CancellableContinuation<CurrentLocation?>? = null

    suspend fun requestLocation(): CurrentLocation? = suspendCancellableCoroutine { requestContinuation ->
        continuation?.cancel()
        continuation = requestContinuation
        locationManager.delegate = this
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        requestContinuation.invokeOnCancellation {
            dispatch_async(dispatch_get_main_queue()) {
                if (continuation === requestContinuation) {
                    continuation = null
                    locationManager.delegate = null
                    locationManager.stopUpdatingLocation()
                }
            }
        }
        locationManager.requestLocation()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        finish((didUpdateLocations.lastOrNull() as? CLLocation)?.toCurrentLocation())
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        finish(null)
    }

    private fun finish(location: CurrentLocation?) {
        val requestContinuation = continuation ?: return
        continuation = null
        locationManager.delegate = null
        locationManager.stopUpdatingLocation()
        if (requestContinuation.isActive) requestContinuation.resume(location)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toCurrentLocation(): CurrentLocation? {
    val (latitude, longitude) = coordinate.useContents { latitude to longitude }
    val capturedAtMillis =
        ((timestamp.timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000.0).toLong()
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    if (!horizontalAccuracy.isFinite() || horizontalAccuracy < 0.0 || capturedAtMillis <= 0L) return null
    return CurrentLocation(latitude, longitude, horizontalAccuracy.toFloat(), capturedAtMillis)
}
