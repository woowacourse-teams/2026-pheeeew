package com.pheeeew

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import com.pheeeew.core.di.ApiDependencies
import com.pheeeew.core.notification.AndroidNotificationPermissionReader
import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationClickInbox
import com.pheeeew.core.notification.NotificationMessage
import com.pheeeew.core.notification.NotificationMessageInbox
import com.pheeeew.core.notification.PushTokenBackgroundSyncResult
import com.pheeeew.core.notification.PushTokenStore
import com.pheeeew.core.notification.PushTokenSyncCoordinator
import com.pheeeew.core.notification.RevisitNotificationCoordinator
import com.pheeeew.core.notification.parseNotificationClick
import com.pheeeew.data.remote.api.PushTokenApi
import com.pheeeew.domain.model.device.DeviceSessionFailureKind
import com.pheeeew.domain.model.device.DeviceSessionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Process owner for notification work. It stores no Activity or UI resource. */
internal class AndroidNotificationRuntime(
    context: Context,
    private val dependencies: ApiDependencies,
) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = AndroidPushTokenStore(applicationContext, BuildConfig.DEVICE_ENVIRONMENT)
    private val tokenReceiptMutex = Mutex()
    internal val revisitStore = AndroidRevisitNotificationStore(applicationContext, BuildConfig.DEVICE_ENVIRONMENT)
    private val authorizationReader = AndroidNotificationPermissionReader(applicationContext)
    private val coordinator =
        PushTokenSyncCoordinator(
            permissions = authorizationReader,
            repository = PushTokenApi(dependencies.client.requests),
            store = store,
            scope = scope,
        )
    private val revisitCoordinator =
        RevisitNotificationCoordinator(
            permissions = authorizationReader,
            store = revisitStore,
            scheduler = AndroidRevisitNotificationScheduler(applicationContext),
        )
    private val clickStore = AndroidNotificationClickStore(applicationContext, BuildConfig.DEVICE_ENVIRONMENT)
    val clickInbox = NotificationClickInbox(clickStore)
    val messageInbox = NotificationMessageInbox()

    @Volatile private var foreground = false

    @Volatile private var networkConnected = false
    private var lastAuthorization: NotificationAuthorization? = null
    internal val isForeground get() = foreground

    init {
        scope.launch {
            dependencies.session.state.collect { session ->
                val generation = (session as? DeviceSessionResult.Ready)?.access?.generation
                coordinator.sessionChanged(generation)
                if (generation != null && store.isPending()) AndroidPushTokenSyncWorker.enqueue(applicationContext)
            }
        }
        scope.launch {
            dependencies.connectivityObserver.isConnected.collect { connected ->
                networkConnected = connected
                coordinator.connectivityChanged(connected)
            }
        }
        scope.launch {
            if (store.isPending()) AndroidPushTokenSyncWorker.enqueue(applicationContext)
        }
        requestCurrentSdkToken()
    }

    fun foreground() {
        foreground = true
        coordinator.onForeground()
        scope.launch { markPushSyncPending() }
        requestCurrentSdkToken()
        scope.launch { dependencies.session.restore() }
        scope.launch {
            lastAuthorization = authorizationReader.authorization()
            updateRevisitPlan { revisitCoordinator.recordVisit(System.currentTimeMillis()) }
        }
    }

    fun background() {
        foreground = false
        coordinator.onBackground()
        messageInbox.clearPending()
    }

    fun permissionChanged() {
        scope.launch {
            val authorization = authorizationReader.authorization()
            if (authorization != lastAuthorization) {
                lastAuthorization = authorization
                coordinator.permissionChanged()
                markPushSyncPending()
                updateRevisitPlan { revisitCoordinator.refresh() }
            }
        }
    }

    fun receiveClickIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        val data =
            extras
                .keySet()
                .mapNotNull { key ->
                    extras.getString(key)?.let { key to it }
                }.toMap()
        val requestId = data["google.message_id"] ?: data["message_id"] ?: data["notificationId"] ?: return
        clickInbox.receive(parseNotificationClick(data, requestId))
    }

    fun receiveRemoteMessage(message: RemoteMessage) {
        scope.launch {
            if (!foreground) return@launch
            val messageId = message.messageId ?: message.data["notificationId"] ?: UUID.randomUUID().toString()
            messageInbox.receive(
                NotificationMessage(
                    id = messageId,
                    data = message.data,
                ),
            )
        }
    }

    fun receiveToken(token: String) {
        if (token.isBlank()) return
        scope.launch { receiveTokenAndSchedule(token) }
    }

    suspend fun receiveTokenAndSchedule(token: String) {
        if (token.isBlank()) return
        tokenReceiptMutex.withLock {
            val persisted =
                try {
                    store.recordReceived(token)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Log.w("NotificationRuntime", "Failed to persist push token; synchronization will retry")
                    false
                }
            withContext(Dispatchers.Main.immediate) { coordinator.tokenReceived(token) }
            try {
                AndroidPushTokenSyncWorker.enqueueAndAwait(applicationContext, force = !persisted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w("NotificationRuntime", "Failed to schedule push token synchronization")
            }
        }
    }

    private suspend fun markPushSyncPending() {
        try {
            store.markPending()
            AndroidPushTokenSyncWorker.enqueueAndAwait(applicationContext)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("NotificationRuntime", "Failed to schedule push token synchronization")
        }
    }

    private suspend fun updateRevisitPlan(block: suspend () -> Unit) {
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("NotificationRuntime", "Failed to update revisit notification schedule")
        }
    }

    // The current BE FCM v1 sender targets `message.token`; FID registration uses `message.fid`.
    @Suppress("DEPRECATION")
    private fun requestCurrentSdkToken() {
        FirebaseMessaging
            .getInstance()
            .token
            .addOnSuccessListener { token -> if (token.isNotBlank()) receiveToken(token) }
    }

    suspend fun synchronizePushTokenInWorker(): PushTokenBackgroundSyncResult {
        val session = dependencies.session.restore()
        val ready = session as? DeviceSessionResult.Ready
        if (ready == null) {
            val failure = (session as? DeviceSessionResult.Failed)?.reason
            return when (failure?.kind) {
                DeviceSessionFailureKind.NETWORK,
                DeviceSessionFailureKind.SERVER,
                DeviceSessionFailureKind.RATE_LIMITED,
                DeviceSessionFailureKind.STORAGE,
                DeviceSessionFailureKind.UNEXPECTED,
                DeviceSessionFailureKind.SESSION_CHANGED,
                -> PushTokenBackgroundSyncResult.RETRY

                else -> PushTokenBackgroundSyncResult.DEFERRED
            }
        }
        if (!networkConnected) return PushTokenBackgroundSyncResult.RETRY
        // Receipt commits the token and delivers it to the coordinator under this same mutex.
        // Hold it only while capturing/adopting the snapshot, never while waiting for HTTP.
        val snapshot =
            tokenReceiptMutex.withLock {
                val version = store.version()
                withContext(Dispatchers.Main.immediate) {
                    val state = dependencies.session.state.value
                    val current = state as? DeviceSessionResult.Ready ?: return@withContext null
                    coordinator.sessionChanged(current.access.generation)
                    version to coordinator.requestSynchronization()
                }
            } ?: return PushTokenBackgroundSyncResult.DEFERRED
        val (version, request) = snapshot
        val result = withContext(Dispatchers.Main.immediate) { coordinator.awaitSynchronization(request) }
        if (result == PushTokenBackgroundSyncResult.DEFERRED && !networkConnected) {
            return PushTokenBackgroundSyncResult.RETRY
        }
        if (result == PushTokenBackgroundSyncResult.SYNCHRONIZED) store.acknowledge(version)
        return result
    }

    suspend fun hasPendingPushSync(): Boolean = store.isPending()
}

internal class AndroidPushTokenStore(
    context: Context,
    environment: String,
) : PushTokenStore {
    private val preferences = context.getSharedPreferences("push-token-$environment", Context.MODE_PRIVATE)
    private val lock = Any()
    private var latestReceived: String? = null

    override suspend fun read(): String? = withContext(Dispatchers.IO) { preferences.getString(TOKEN_KEY, null) }

    override suspend fun write(token: String) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (latestReceived != null && latestReceived != token) return@synchronized
                if (preferences.getString(TOKEN_KEY, null) != token) {
                    val nextVersion = preferences.getLong(VERSION_KEY, 0L) + 1
                    check(
                        preferences
                            .edit()
                            .putString(TOKEN_KEY, token)
                            .putLong(VERSION_KEY, nextVersion)
                            .putBoolean(PENDING_KEY, true)
                            .commit(),
                    )
                }
            }
        }
    }

    suspend fun recordReceived(token: String) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                latestReceived = token
                val nextVersion = preferences.getLong(VERSION_KEY, 0L) + 1
                check(
                    preferences
                        .edit()
                        .putString(TOKEN_KEY, token)
                        .putLong(VERSION_KEY, nextVersion)
                        .putBoolean(PENDING_KEY, true)
                        .commit(),
                )
            }
        }

    suspend fun markPending() =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val nextVersion = preferences.getLong(VERSION_KEY, 0L) + 1
                check(
                    preferences
                        .edit()
                        .putLong(VERSION_KEY, nextVersion)
                        .putBoolean(PENDING_KEY, true)
                        .commit(),
                )
            }
        }

    suspend fun isPending(): Boolean = withContext(Dispatchers.IO) { preferences.getBoolean(PENDING_KEY, false) }

    suspend fun version(): Long = withContext(Dispatchers.IO) { preferences.getLong(VERSION_KEY, 0L) }

    suspend fun acknowledge(version: Long) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (preferences.getLong(VERSION_KEY, 0L) == version) {
                    check(preferences.edit().putBoolean(PENDING_KEY, false).commit())
                }
            }
        }

    private companion object {
        const val TOKEN_KEY = "latest-token"
        const val VERSION_KEY = "version"
        const val PENDING_KEY = "pending"
    }
}

internal class AndroidPushTokenSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? PheeeewApplication ?: return Result.failure()
        val runtime = application.notificationRuntime
        if (!runtime.hasPendingPushSync() && !inputData.getBoolean(KEY_FORCE, false)) return Result.success()
        val result =
            try {
                runtime.synchronizePushTokenInWorker()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return Result.retry()
            }
        return when (result) {
            PushTokenBackgroundSyncResult.SYNCHRONIZED -> {
                try {
                    if (runtime.hasPendingPushSync()) enqueue(applicationContext)
                    Result.success()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Result.retry()
                }
            }

            PushTokenBackgroundSyncResult.DEFERRED,
            PushTokenBackgroundSyncResult.FAILED,
            -> {
                Result.success()
            }

            PushTokenBackgroundSyncResult.RETRY -> {
                Result.retry()
            }
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "push-token-sync"
        private const val KEY_FORCE = "force"

        fun enqueue(context: Context) {
            enqueueRequest(context)
        }

        suspend fun enqueueAndAwait(
            context: Context,
            force: Boolean = false,
        ) {
            val operation = enqueueRequest(context, force)
            withContext(Dispatchers.IO) { operation.result.get() }
        }

        private fun enqueueRequest(
            context: Context,
            force: Boolean = false,
        ): Operation {
            val request =
                OneTimeWorkRequestBuilder<AndroidPushTokenSyncWorker>()
                    .setInputData(workDataOf(KEY_FORCE to force))
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()
            return WorkManager
                .getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
