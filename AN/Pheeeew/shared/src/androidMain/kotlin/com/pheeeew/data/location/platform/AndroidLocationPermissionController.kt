package com.pheeeew.data.location.platform.android

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
import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

class AndroidLocationPermissionController(
    activity: ComponentActivity,
) : LocationPermissionController, AutoCloseable {
    private val applicationContext = activity.applicationContext
    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val locationManager =
        requireNotNull(applicationContext.getSystemService(LocationManager::class.java))
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestMutex = Mutex()
    private var activityReference = WeakReference(activity)
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var pendingContinuation: CancellableContinuation<LocationPermissionStatus>? = null
    private val lifecycleObserver = LifecycleEventObserver { owner, event ->
        if (event == Lifecycle.Event.ON_DESTROY && activityReference.get() === owner) {
            detach(cancelPendingRequest = !owner.isChangingConfigurations)
        }
    }

    init {
        attach(activity)
    }

    fun attach(activity: ComponentActivity) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (activityReference.get() === activity && permissionLauncher != null) return
        detach(cancelPendingRequest = false)
        activityReference = WeakReference(activity)
        permissionLauncher = activity.activityResultRegistry.register(
            ACTIVITY_RESULT_KEY,
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            pendingContinuation?.let { continuation ->
                pendingContinuation = null
                if (continuation.isActive) continuation.resume(statusNow())
            }
        }
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    override suspend fun currentStatus(): LocationPermissionStatus =
        withContext(Dispatchers.Main.immediate) { statusNow() }

    override suspend fun requestPermission(): LocationPermissionStatus = requestMutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            if (hasLocationPermission()) return@withContext statusNow()
            val launcher = permissionLauncher ?: return@withContext statusNow()
            preferences.edit().putBoolean(KEY_HAS_REQUESTED, true).apply()
            suspendCancellableCoroutine { continuation ->
                pendingContinuation = continuation
                continuation.invokeOnCancellation {
                    mainHandler.post {
                        if (pendingContinuation === continuation) pendingContinuation = null
                    }
                }
                try {
                    launcher.launch(LOCATION_PERMISSIONS)
                } catch (_: IllegalStateException) {
                    pendingContinuation = null
                    if (continuation.isActive) continuation.resume(statusNow())
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
        val servicesEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.P || locationManager.isLocationEnabled
        val wasRequested = preferences.getBoolean(KEY_HAS_REQUESTED, false)
        val activity = activityReference.get()
        val canExplainDenial = activity?.let {
            LOCATION_PERMISSIONS.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
            }
        } ?: false
        return when {
            hasLocationPermission() && !servicesEnabled -> LocationPermissionStatus.ServicesDisabled
            hasLocationPermission() -> LocationPermissionStatus.Granted
            wasRequested && activity != null && !canExplainDenial -> LocationPermissionStatus.PermanentlyDenied
            else -> LocationPermissionStatus.Denied
        }
    }

    private fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val PREFERENCES_NAME = "foundation-location-permission"
        const val KEY_HAS_REQUESTED = "has-requested-foreground-location"
        const val ACTIVITY_RESULT_KEY = "foundation-foreground-location-permission"
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
