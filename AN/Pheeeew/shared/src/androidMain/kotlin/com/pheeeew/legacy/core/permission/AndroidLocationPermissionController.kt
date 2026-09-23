package com.pheeeew.legacy.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Activity Result API를 사용해 포그라운드 위치 권한만 요청합니다.
 *
 * Android는 요청 전에는 "아직 묻지 않음"과 "다시 묻지 않음"을 구분하지
 * 못하므로, 권한을 실제로 요청한 적이 있는지만 로컬에 기록합니다. 정확한
 * 위치나 권한 요청 결과 자체는 로그나 분석 시스템에 전달하지 않습니다.
 */
class AndroidLocationPermissionController(
    activity: ComponentActivity,
) : LocationPermissionController,
    AutoCloseable {
    private var activityReference = WeakReference(activity)
    private val applicationContext = activity.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestMutex = Mutex()
    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private var pendingContinuation: CancellableContinuation<LocationPermissionStatus>? = null
    private val lifecycleObserver =
        LifecycleEventObserver { owner, event ->
            if (event == Lifecycle.Event.ON_DESTROY && activityReference.get() === owner) {
                val changingConfigurations = owner.isChangingConfigurations
                detach(cancelPendingRequest = !changingConfigurations)
            }
        }

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val locationManager: LocationManager =
        requireNotNull(
            applicationContext.getSystemService(LocationManager::class.java),
        )

    init {
        attach(activity)
    }

    /** 구성 변경 뒤 새 Activity의 registry와 lifecycle에 launcher를 다시 연결합니다. */
    fun attach(activity: ComponentActivity) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Location permission controller must be attached on the main thread"
        }
        if (activityReference.get() === activity && permissionLauncher != null) return

        detach(cancelPendingRequest = false)
        activityReference = WeakReference(activity)
        permissionLauncher =
            activity.activityResultRegistry.register(
                ACTIVITY_RESULT_KEY,
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                val continuation = pendingContinuation
                pendingContinuation = null
                if (continuation?.isActive == true) {
                    continuation.resume(statusNow())
                }
            }
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    override suspend fun currentStatus(): LocationPermissionStatus =
        withContext(Dispatchers.Main.immediate) {
            statusNow()
        }

    override suspend fun requestPermission(): LocationPermissionStatus =
        requestMutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                if (hasLocationPermission()) {
                    return@withContext statusNow()
                }
                val launcher = permissionLauncher ?: return@withContext statusNow()

                preferences.edit().putBoolean(KEY_HAS_REQUESTED, true).apply()

                suspendCancellableCoroutine { continuation ->
                    pendingContinuation = continuation
                    continuation.invokeOnCancellation {
                        mainHandler.post {
                            if (pendingContinuation === continuation) {
                                pendingContinuation = null
                            }
                        }
                    }

                    try {
                        launcher.launch(LOCATION_PERMISSIONS)
                    } catch (_: IllegalStateException) {
                        pendingContinuation = null
                        if (continuation.isActive) {
                            continuation.resume(statusNow())
                        }
                    }
                }
            }
        }

    override fun close() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detach(cancelPendingRequest = true)
        } else {
            mainHandler.post { detach(cancelPendingRequest = true) }
        }
    }

    private fun detach(cancelPendingRequest: Boolean) {
        activityReference.get()?.lifecycle?.removeObserver(lifecycleObserver)
        permissionLauncher?.unregister()
        permissionLauncher = null
        activityReference.clear()
        if (cancelPendingRequest) {
            pendingContinuation?.cancel()
            pendingContinuation = null
        }
    }

    private fun statusNow(): LocationPermissionStatus {
        val locationServicesEnabled =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                locationManager.isLocationEnabled

        val wasRequested = preferences.getBoolean(KEY_HAS_REQUESTED, false)
        val activity = activityReference.get()
        val canExplainDenial =
            activity?.let {
                LOCATION_PERMISSIONS.any { permission ->
                    ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
                }
            } ?: false

        return androidLocationPermissionStatus(
            hasPermission = hasLocationPermission(),
            locationServicesEnabled = locationServicesEnabled,
            wasRequested = wasRequested,
            isActivityAttached = activity != null,
            canExplainDenial = canExplainDenial,
        )
    }

    private fun hasLocationPermission(): Boolean =
        LOCATION_PERMISSIONS.any { permission ->
            ContextCompat.checkSelfPermission(applicationContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

    private companion object {
        const val PREFERENCES_NAME = "location-permission"
        const val KEY_HAS_REQUESTED = "has-requested-foreground-location"
        const val ACTIVITY_RESULT_KEY = "pheeeew-foreground-location-permission"

        val LOCATION_PERMISSIONS =
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
    }
}

internal fun androidLocationPermissionStatus(
    hasPermission: Boolean,
    locationServicesEnabled: Boolean,
    wasRequested: Boolean,
    isActivityAttached: Boolean,
    canExplainDenial: Boolean,
): LocationPermissionStatus =
    when {
        hasPermission && !locationServicesEnabled -> LocationPermissionStatus.ServicesDisabled
        hasPermission -> LocationPermissionStatus.Granted
        !isActivityAttached -> LocationPermissionStatus.Denied
        wasRequested && !canExplainDenial -> LocationPermissionStatus.PermanentlyDenied
        else -> LocationPermissionStatus.Denied
    }
