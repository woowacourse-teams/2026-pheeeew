package com.pheeeew.core.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pheeeew.core.permission.AppSettingsLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.UUID

/** Android notification permission bridge retained across Activity recreation. */
class AndroidNotificationPermissionController(
    activity: ComponentActivity,
    private val appSettingsLauncher: AppSettingsLauncher,
) : NotificationPermissionController,
    AutoCloseable {
    private val applicationContext = activity.applicationContext
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val requests = AndroidNotificationPermissionRequest()
    private val history =
        AndroidNotificationPermissionHistory(preferences.getBoolean(KEY_HAS_OBSERVED_RATIONALE, false))
    private val activityResultKey = "foundation-post-notifications-${UUID.randomUUID()}"
    private var activityReference = WeakReference(activity)
    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private val lifecycleObserver =
        LifecycleEventObserver { owner, event ->
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
        permissionLauncher =
            activity.activityResultRegistry.register(
                activityResultKey,
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                history.onResult(granted, shouldShowRationale())
                preferences.edit().putBoolean(KEY_HAS_OBSERVED_RATIONALE, history.hasObservedRationale).apply()
                requests.complete()
            }
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    override suspend fun authorization(): NotificationAuthorization =
        withContext(Dispatchers.Main.immediate) { authorizationNow() }

    override suspend fun canRequestAuthorization(): Boolean =
        withContext(Dispatchers.Main.immediate) { canRequestNow() }

    override suspend fun requestAuthorization(): Unit =
        withContext(Dispatchers.Main.immediate) {
            // A cancelled UI waiter does not cancel the OS dialog. Join that request until its result arrives.
            requests.request {
                if (!canRequestNow()) {
                    requests.complete()
                    return@request
                }
                val launcher = permissionLauncher ?: error("Notification permission request is unavailable")
                history.observeRationale(shouldShowRationale())
                preferences.edit().putBoolean(KEY_HAS_OBSERVED_RATIONALE, history.hasObservedRationale).apply()
                preferences.edit().putBoolean(KEY_HAS_REQUESTED, true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    override suspend fun openSettings(): Boolean =
        withContext(Dispatchers.Main.immediate) {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                applicationContext.startActivity(intent)
                true
            } catch (_: Exception) {
                appSettingsLauncher.openAppSettings()
            }
        }

    override fun close() {
        check(Looper.myLooper() == Looper.getMainLooper())
        detach(cancelPendingRequest = true)
    }

    private fun detach(cancelPendingRequest: Boolean) {
        activityReference.get()?.lifecycle?.removeObserver(lifecycleObserver)
        permissionLauncher?.unregister()
        permissionLauncher = null
        activityReference.clear()
        if (cancelPendingRequest) {
            requests.cancel()
        }
    }

    private fun authorizationNow(): NotificationAuthorization {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotificationsPermission()) {
            return if (preferences.getBoolean(KEY_HAS_REQUESTED, false)) {
                NotificationAuthorization.DENIED
            } else {
                NotificationAuthorization.NOT_DETERMINED
            }
        }
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return NotificationAuthorization.DENIED
        }
        return NotificationAuthorization.AUTHORIZED
    }

    private fun shouldShowRationale(): Boolean =
        activityReference.get()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } ?: false

    private fun canRequestNow(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPostNotificationsPermission() && activityReference.get() != null &&
            history.canRequest(shouldShowRationale())

    private fun hasPostNotificationsPermission(): Boolean =
        applicationContext.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val PREFERENCES_NAME = "foundation-notification-permission"
        private const val KEY_HAS_REQUESTED = "has-requested-post-notifications"
        private const val KEY_HAS_OBSERVED_RATIONALE = "has-observed-notification-rationale"
    }
}
