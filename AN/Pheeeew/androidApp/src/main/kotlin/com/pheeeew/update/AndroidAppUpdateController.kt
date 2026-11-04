package com.pheeeew.update

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.pheeeew.core.update.AppUpdateController
import com.pheeeew.core.update.AppUpdateDriver
import com.pheeeew.core.update.AppUpdateState
import com.pheeeew.core.update.DefaultAppUpdateController
import com.pheeeew.core.update.UpdateAvailability
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.play.core.install.model.UpdateAvailability as PlayAvailability

/** Created once per Activity, never stored in an application or retained graph. */
class AndroidAppUpdateController(
    private val activity: ComponentActivity,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity.applicationContext),
) : AppUpdateController,
    AppUpdateDriver,
    DefaultLifecycleObserver {
    private var info: AppUpdateInfo? = null
    private val controller = DefaultAppUpdateController(this, activity.lifecycleScope)
    override val state = controller.state
    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            controller.onResult(result.resultCode == Activity.RESULT_OK, result.resultCode == Activity.RESULT_CANCELED)
        }
    private val listener =
        InstallStateUpdatedListener { install ->
            val status =
                when (install.installStatus()) {
                    InstallStatus.DOWNLOADING, InstallStatus.PENDING -> AppUpdateState.DOWNLOADING
                    InstallStatus.DOWNLOADED -> AppUpdateState.DOWNLOADED
                    InstallStatus.INSTALLING -> AppUpdateState.INSTALLING
                    InstallStatus.INSTALLED -> AppUpdateState.IDLE
                    InstallStatus.CANCELED -> AppUpdateState.CANCELED
                    InstallStatus.FAILED -> AppUpdateState.FAILED
                    else -> null
                }
            status?.let(controller::onInstallState)
        }

    init {
        manager.registerListener(listener)
        activity.lifecycle.addObserver(this)
    }

    override suspend fun check(): UpdateAvailability {
        val latest = manager.appUpdateInfo.awaitResult()
        info = latest
        return UpdateAvailability(
            available = latest.updateAvailability() == PlayAvailability.UPDATE_AVAILABLE,
            flexibleAllowed = latest.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            immediateAllowed = latest.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            immediateInProgress =
                latest.updateAvailability() == PlayAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            downloaded = latest.installStatus() == InstallStatus.DOWNLOADED,
            downloading = latest.installStatus() in setOf(InstallStatus.PENDING, InstallStatus.DOWNLOADING),
            installing = latest.installStatus() == InstallStatus.INSTALLING,
        )
    }

    override fun launch(required: Boolean): Boolean {
        val current = info ?: return false
        info = null // Each AppUpdateInfo can launch only once.
        return manager.startUpdateFlowForResult(
            current,
            launcher,
            AppUpdateOptions.newBuilder(if (required) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE).build(),
        )
    }

    override suspend fun install() {
        manager.completeUpdate().awaitResult()
    }

    override fun request(required: Boolean) = controller.request(required)

    override fun resume() = controller.resume()

    override fun complete() = controller.complete()

    override fun onResume(owner: LifecycleOwner) = resume()

    override fun onDestroy(owner: LifecycleOwner) {
        manager.unregisterListener(listener)
        activity.lifecycle.removeObserver(this)
        info = null
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
