package com.pheeeew

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pheeeew.core.notification.AndroidNotificationPermissionReader
import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.RevisitNotificationPlan
import com.pheeeew.core.notification.RevisitNotificationPolicy
import com.pheeeew.core.notification.RevisitNotificationScheduler
import com.pheeeew.core.notification.RevisitNotificationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal class AndroidRevisitNotificationStore(
    context: Context,
    environment: String,
) : RevisitNotificationStore {
    private val preferences = context.getSharedPreferences("revisit-notifications-$environment", Context.MODE_PRIVATE)

    override fun lastAccessEpochMillis(): Long? =
        if (preferences.contains(KEY_LAST_ACCESS)) preferences.getLong(KEY_LAST_ACCESS, 0L) else null

    override fun saveLastAccess(epochMillis: Long) {
        synchronized(AndroidRevisitPlanLock) {
            check(
                preferences
                    .edit()
                    .putLong(KEY_LAST_ACCESS, epochMillis)
                    .putInt(KEY_PROCESSED, 0)
                    .commit(),
            )
        }
    }

    fun isCurrentPlan(lastAccessEpochMillis: Long): Boolean = lastAccessEpochMillis() == lastAccessEpochMillis

    /** Claim before posting: a crash can skip this slot, but cannot alert twice after a restart. */
    fun claim(index: Int): Claim {
        val mask = 1 shl index
        val processed = preferences.getInt(KEY_PROCESSED, 0)
        if (processed and mask != 0) return Claim.ALREADY_CLAIMED
        return if (preferences.edit().putInt(KEY_PROCESSED, processed or mask).commit()) Claim.CLAIMED else Claim.FAILED
    }

    enum class Claim { CLAIMED, ALREADY_CLAIMED, FAILED }

    private companion object {
        const val KEY_LAST_ACCESS = "last-access-epoch-millis"
        const val KEY_PROCESSED = "processed-slots"
    }
}

/** Serializes visit-plan replacement with the final stale-worker check and notification post. */
internal object AndroidRevisitPlanLock

internal class AndroidRevisitNotificationScheduler(
    context: Context,
) : RevisitNotificationScheduler {
    private val applicationContext = context.applicationContext
    private val workManager = WorkManager.getInstance(applicationContext)

    override suspend fun cancelOwned() {
        withContext(Dispatchers.IO) {
            workManager.cancelAllWorkByTag(WORK_TAG).result.get()
            val notifications = applicationContext.getSystemService(NotificationManager::class.java)
            repeat(MAX_SCHEDULED_NOTIFICATIONS) { index -> notifications.cancel(NOTIFICATION_ID_BASE + index) }
        }
    }

    override suspend fun schedule(plan: RevisitNotificationPlan) {
        val now = System.currentTimeMillis()
        val requests =
            plan.fireTimesEpochMillis.mapIndexedNotNull { index, fireAt ->
                if (fireAt <= now) return@mapIndexedNotNull null
                OneTimeWorkRequestBuilder<AndroidRevisitNotificationWorker>()
                    .setInitialDelay(fireAt - now, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_PLAN_ID to plan.lastAccessEpochMillis, KEY_INDEX to index))
                    .addTag(WORK_TAG)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                            .build(),
                    ).build()
            }
        if (requests.isEmpty()) return
        try {
            withContext(Dispatchers.IO) {
                requests.forEachIndexed { index, request ->
                    workManager
                        .enqueueUniqueWork(uniqueWorkName(index), ExistingWorkPolicy.REPLACE, request)
                        .result
                        .get()
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    workManager.cancelAllWorkByTag(WORK_TAG).result.get()
                } catch (_: Exception) {
                    // Cancellation must still propagate if best-effort cleanup fails.
                }
            }
            throw cancelled
        } catch (error: Exception) {
            withContext(Dispatchers.IO) { workManager.cancelAllWorkByTag(WORK_TAG).result.get() }
            throw error
        }
    }

    companion object {
        const val WORK_NAME = "revisit-notification-plan"
        const val WORK_TAG = "revisit-notification"
        const val MAX_SCHEDULED_NOTIFICATIONS = 30
        const val NOTIFICATION_ID_BASE = 71_000
        const val KEY_PLAN_ID = "plan-id"
        const val KEY_INDEX = "index"

        private fun uniqueWorkName(index: Int): String = "$WORK_NAME-$index"
    }
}

internal class AndroidRevisitNotificationWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? PheeeewApplication ?: return Result.failure()
        val lastAccess = inputData.getLong(AndroidRevisitNotificationScheduler.KEY_PLAN_ID, -1L)
        val index = inputData.getInt(AndroidRevisitNotificationScheduler.KEY_INDEX, -1)
        if (lastAccess < 0L || index !in 0 until AndroidRevisitNotificationScheduler.MAX_SCHEDULED_NOTIFICATIONS) {
            return Result.failure()
        }
        val runtime = application.notificationRuntime
        val plan = runCatching { RevisitNotificationPolicy().plan(lastAccess) }.getOrNull() ?: return Result.failure()
        val fireAt = plan.fireTimesEpochMillis.getOrNull(index) ?: return Result.failure()
        val now = System.currentTimeMillis()
        if (now < fireAt) return Result.retry()
        val nextWindowStart =
            plan.fireTimesEpochMillis.getOrNull(index + 1)
                ?: plan.fireTimesEpochMillis.getOrNull(index - 1)?.let { previousFireAt ->
                    val interval = fireAt - previousFireAt
                    if (fireAt <= Long.MAX_VALUE - interval) fireAt + interval else null
                }
        if (nextWindowStart != null && now >= nextWindowStart) return Result.success()
        if (runtime.isForeground || !runtime.revisitStore.isCurrentPlan(lastAccess)) return Result.success()
        if (AndroidNotificationPermissionReader(applicationContext).authorization() !=
            NotificationAuthorization.AUTHORIZED
        ) {
            return Result.success()
        }
        return synchronized(AndroidRevisitPlanLock) {
            if (runtime.isForeground ||
                !runtime.revisitStore.isCurrentPlan(lastAccess)
            ) {
                return@synchronized Result.success()
            }
            val channelId = applicationContext.getString(R.string.revisit_notification_channel_id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    applicationContext
                        .getSystemService(
                            NotificationManager::class.java,
                        ).getNotificationChannel(channelId)
                if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                    return@synchronized Result.success()
                }
            }
            when (runtime.revisitStore.claim(index)) {
                AndroidRevisitNotificationStore.Claim.ALREADY_CLAIMED -> return@synchronized Result.success()
                AndroidRevisitNotificationStore.Claim.FAILED -> return@synchronized Result.retry()
                AndroidRevisitNotificationStore.Claim.CLAIMED -> Unit
            }
            try {
                postNotification(channelId, lastAccess, index)
                Result.success()
            } catch (_: SecurityException) {
                Result.success()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(
        channelId: String,
        planId: Long,
        index: Int,
    ) {
        val notificationId = AndroidRevisitNotificationScheduler.NOTIFICATION_ID_BASE + index
        val stableId = "revisit-$planId-$index"
        val intent =
            Intent(applicationContext, MainActivity::class.java)
                .putExtra("type", "REVISIT_REMINDER")
                .putExtra("notificationId", stableId)
                .setData(Uri.parse("pheeeew://notification/$stableId"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("히유~ 요즘 어떻게 지내요?")
                .setContentText("쌓인 마음이 있다면, 히유에서 가볍게 털어놔요.")
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }
}
