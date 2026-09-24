package com.pheeeew.data.location.platform.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.core.location.PlatformLocationProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AndroidPlatformLocationProvider(
    context: Context,
) : PlatformLocationProvider {
    private val applicationContext = context.applicationContext
    private val locationManager = requireNotNull(
        applicationContext.getSystemService(LocationManager::class.java),
    )
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)

    override suspend fun getCurrentLocation(): CurrentLocation? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) return null
        if (!hasLocationPermission()) return null

        recentLocation(fusedClient.lastLocation.awaitOrNull())?.let { return it }
        recentLocation(lastKnown(LocationManager.NETWORK_PROVIDER))?.let { return it }
        recentLocation(lastKnown(LocationManager.GPS_PROVIDER))?.let { return it }

        withTimeoutOrNull(15_000L) { requestFusedLocation() }?.toCurrentLocation()?.let { return it }
        return withTimeoutOrNull(15_000L) { requestNetworkLocation() }?.toCurrentLocation()
            ?: withTimeoutOrNull(15_000L) { requestGpsLocation() }?.toCurrentLocation()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastKnown(provider: String): Location? = try {
        if (locationManager.isProviderEnabled(provider)) locationManager.getLastKnownLocation(provider) else null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun recentLocation(location: Location?): CurrentLocation? {
        val currentTime = System.currentTimeMillis()
        return location?.takeIf { currentTime - it.time in 0..60_000L }?.toCurrentLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFusedLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellation.cancel() }
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                .addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestNetworkLocation(): Location? = requestProvider(LocationManager.NETWORK_PROVIDER)

    @SuppressLint("MissingPermission")
    private suspend fun requestGpsLocation(): Location? = requestProvider(LocationManager.GPS_PROVIDER)

    @SuppressLint("MissingPermission")
    private suspend fun requestProvider(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        if (!runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                locationManager.getCurrentLocation(
                    provider,
                    cancellation,
                    ContextCompat.getMainExecutor(applicationContext),
                ) { if (continuation.isActive) continuation.resume(it) }
            } else {
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun Location.toCurrentLocation(): CurrentLocation? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        if (!accuracy.isFinite() || accuracy < 0f || time <= 0L) return null
        return CurrentLocation(latitude, longitude, accuracy, time)
    }

    private suspend fun com.google.android.gms.tasks.Task<Location?>.awaitOrNull(): Location? =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                .addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
        }
}
