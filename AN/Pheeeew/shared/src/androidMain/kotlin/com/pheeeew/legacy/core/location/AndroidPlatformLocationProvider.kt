package com.pheeeew.legacy.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.pheeeew.legacy.domain.model.location.CurrentLocation
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Android의 [LocationManager]에서 단발성 포그라운드 위치를 가져옵니다. */
class AndroidPlatformLocationProvider(
    context: Context,
    private val locationManager: LocationManager =
        requireNotNull(context.applicationContext.getSystemService(LocationManager::class.java)),
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext),
) : PlatformLocationProvider {
    private val applicationContext = context.applicationContext

    override suspend fun getCurrentLocation(): PlatformLocationResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            return PlatformLocationResult.GpsUnavailable
        }

        val hasFinePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarsePermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFinePermission && !hasCoarsePermission) {
            return PlatformLocationResult.GpsUnavailable
        }

        findRecentLastKnownLocation()?.let { location ->
            return PlatformLocationResult.Success(location)
        }

        return requestFromAvailableProviders()?.let { location ->
            PlatformLocationResult.Success(location)
        } ?: PlatformLocationResult.GpsUnavailable
    }

    private fun isProviderEnabled(provider: String): Boolean =
        try {
            locationManager.isProviderEnabled(provider)
        } catch (_: IllegalArgumentException) {
            false
        }

    private suspend fun findRecentLastKnownLocation(): com.pheeeew.legacy.domain.model.location.CurrentLocation? {
        val nowMillis = System.currentTimeMillis()
        val fusedLastKnownLocation =
            try {
                withTimeoutOrNull(LAST_KNOWN_LOCATION_TIMEOUT_MILLIS) {
                    fusedLocationClient.lastLocation.awaitOrNull()
                }
            } catch (_: Exception) {
                null
            }

        return firstValidCurrentLocation(
            listOf(
                fusedLastKnownLocation
                    ?.takeIf { it.isRecent(nowMillis) }
                    .toCurrentLocationOrNull(),
                lastKnownLocation(LocationManager.NETWORK_PROVIDER, nowMillis)
                    .toCurrentLocationOrNull(),
                lastKnownLocation(LocationManager.GPS_PROVIDER, nowMillis)
                    .toCurrentLocationOrNull(),
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(
        provider: String,
        nowMillis: Long,
    ): Location? =
        if (!isProviderEnabled(provider)) {
            null
        } else {
            try {
                locationManager.getLastKnownLocation(provider)?.takeIf { it.isRecent(nowMillis) }
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private suspend fun requestFromAvailableProviders(): com.pheeeew.legacy.domain.model.location.CurrentLocation? =
        coroutineScope {
            val requests =
                buildList<Deferred<com.pheeeew.legacy.domain.model.location.CurrentLocation?>> {
                    add(async { requestFusedLocationCandidate() })
                    if (isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        add(async { requestProviderCandidate(LocationManager.NETWORK_PROVIDER) })
                    }
                    if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                        isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ) {
                        add(async { requestProviderCandidate(LocationManager.GPS_PROVIDER) })
                    }
                }

            val remainingRequests = requests.toMutableList()
            try {
                while (remainingRequests.isNotEmpty()) {
                    val completedRequest =
                        select<Deferred<com.pheeeew.legacy.domain.model.location.CurrentLocation?>> {
                            remainingRequests.forEach { request ->
                                request.onAwait { request }
                            }
                        }
                    remainingRequests.remove(completedRequest)
                    completedRequest.await()?.let { return@coroutineScope it }
                }
                null
            } finally {
                requests.forEach { it.cancel() }
            }
        }

    private suspend fun requestFusedLocationCandidate(): com.pheeeew.legacy.domain.model.location.CurrentLocation? =
        try {
            withTimeoutOrNull(LOCATION_REQUEST_TIMEOUT_MILLIS) {
                requestFusedLocation().toCurrentLocationOrNull()
            }
        } catch (_: Exception) {
            null
        }

    private suspend fun requestProviderCandidate(provider: String): com.pheeeew.legacy.domain.model.location.CurrentLocation? =
        try {
            withTimeoutOrNull(LOCATION_REQUEST_TIMEOUT_MILLIS) {
                (requestLocation(provider) as? PlatformLocationResult.Success)?.location
            }
        } catch (_: Exception) {
            null
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestFusedLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

            try {
                val locationTask =
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token,
                    )
                locationTask
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }.addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }.addOnCanceledListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(provider: String): PlatformLocationResult =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }

                try {
                    locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(applicationContext),
                    ) { location ->
                        if (continuation.isActive) {
                            continuation.resume(location.toPlatformResult())
                        }
                    }
                } catch (_: SecurityException) {
                    if (continuation.isActive) {
                        continuation.resume(PlatformLocationResult.GpsUnavailable)
                    }
                } catch (_: IllegalArgumentException) {
                    if (continuation.isActive) {
                        continuation.resume(PlatformLocationResult.GpsUnavailable)
                    }
                }
            } else {
                requestLegacyLocation(provider, continuation)
            }
        }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun requestLegacyLocation(
        provider: String,
        continuation: kotlinx.coroutines.CancellableContinuation<PlatformLocationResult>,
    ) {
        val listener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location.toPlatformResult())
                    }
                }

                override fun onProviderDisabled(disabledProvider: String) {
                    if (disabledProvider != provider) return
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(PlatformLocationResult.GpsUnavailable)
                    }
                }

                override fun onProviderEnabled(enabledProvider: String) = Unit

                override fun onStatusChanged(
                    changedProvider: String?,
                    status: Int,
                    extras: Bundle?,
                ) = Unit
            }

        continuation.invokeOnCancellation {
            locationManager.removeUpdates(listener)
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            if (continuation.isActive) {
                continuation.resume(PlatformLocationResult.GpsUnavailable)
            }
        } catch (_: IllegalArgumentException) {
            if (continuation.isActive) {
                continuation.resume(PlatformLocationResult.GpsUnavailable)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location?.toPlatformResult(): PlatformLocationResult {
        this ?: return PlatformLocationResult.GpsUnavailable
        return androidPlatformLocationResult(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy.takeIf { hasAccuracy() },
            capturedAtMillis = time,
        )
    }

    private fun Location?.toCurrentLocationOrNull(): com.pheeeew.legacy.domain.model.location.CurrentLocation? =
        (toPlatformResult() as? PlatformLocationResult.Success)?.location

    private fun Location.isRecent(nowMillis: Long): Boolean =
        isRecentAndroidLocation(time, nowMillis, MAX_LAST_KNOWN_LOCATION_AGE_MILLIS)

    private suspend fun <T> Task<T>.awaitOrNull(): T? =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value ->
                if (continuation.isActive) continuation.resume(value)
            }
            addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
            addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    companion object {
        private const val LAST_KNOWN_LOCATION_TIMEOUT_MILLIS = 1_000L
        private const val MAX_LAST_KNOWN_LOCATION_AGE_MILLIS = 60_000L
        private const val LOCATION_REQUEST_TIMEOUT_MILLIS = 30_000L
    }
}

internal fun isRecentAndroidLocation(
    capturedAtMillis: Long,
    nowMillis: Long,
    maximumAgeMillis: Long,
): Boolean =
    capturedAtMillis > 0L &&
        nowMillis >= capturedAtMillis &&
        nowMillis - capturedAtMillis <= maximumAgeMillis

internal fun firstValidCurrentLocation(candidates: List<com.pheeeew.legacy.domain.model.location.CurrentLocation?>): com.pheeeew.legacy.domain.model.location.CurrentLocation? =
    candidates.asSequence().filterNotNull().firstOrNull()

internal fun androidPlatformLocationResult(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    capturedAtMillis: Long,
): PlatformLocationResult {
    if (
        !latitude.isFinite() ||
        !longitude.isFinite() ||
        latitude !in -90.0..90.0 ||
        longitude !in -180.0..180.0 ||
        accuracyMeters == null ||
        !accuracyMeters.isFinite() ||
        accuracyMeters < 0f ||
        capturedAtMillis <= 0L
    ) {
        return PlatformLocationResult.GpsUnavailable
    }

    return PlatformLocationResult.Success(
        com.pheeeew.legacy.domain.model.location.CurrentLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            capturedAtMillis = capturedAtMillis,
        ),
    )
}
