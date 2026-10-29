package com.pheeeew.data.repository

import com.pheeeew.data.local.config.AppConfigStore
import com.pheeeew.data.local.config.CachedAppConfig
import com.pheeeew.domain.model.config.AppConfig
import com.pheeeew.domain.model.config.AppConfigSnapshot
import com.pheeeew.domain.model.config.AppConfigSource
import com.pheeeew.domain.model.config.AppConfigState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource

/** One instance per app graph and API environment; UI sessions only borrow it. */
class AppConfigRepository(
    private val store: AppConfigStore,
    private val scope: CoroutineScope,
    private val load: suspend () -> AppConfigSnapshot,
    private val now: () -> String = { Clock.System.now().toString() },
    private val elapsedMillis: () -> Long = monotonicClock(),
) {
    private val mutable = MutableStateFlow(AppConfigState())
    val state = mutable.asStateFlow()
    private val initialization = Mutex()
    private val requests = Mutex()
    private val foreground = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var inFlight: Deferred<Unit>? = null
    private var lastAttempt: Long? = null

    suspend fun initialize() =
        initialization.withLock {
            if (mutable.value.initialized) return@withLock
            try {
                val raw = store.read()
                if (raw != null) {
                    val cached = json.decodeFromString<CachedAppConfig>(raw)
                    require(cached.schema == 1)
                    cached.snapshot.validated()
                    Instant.parse(cached.fetchedAt)
                    mutable.update {
                        it.copy(
                            snapshot = cached.snapshot,
                            normalSource = AppConfigSource.CACHE,
                            lastFetchSucceededAt = cached.fetchedAt,
                            lastAppliedAt = now(),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = "캐시 읽기 실패: 기본값 사용") }
            }
            mutable.update { it.copy(initialized = true) }
        }

    suspend fun refresh(force: Boolean = true) {
        initialize()
        val task =
            requests.withLock {
                inFlight?.takeUnless { it.isCompleted } ?: run {
                    if (!force && lastAttempt?.let { elapsedMillis() - it < INTERVAL_MILLIS } == true) {
                        return@withLock null
                    }
                    lastAttempt = elapsedMillis()
                    scope.async(start = CoroutineStart.LAZY) { fetch() }.also { inFlight = it }
                }
            }
        task?.start()
        task?.await()
    }

    /** Only one lifecycle owner polls; cancellation stops its in-flight request too. */
    suspend fun runForeground() =
        foreground.withLock {
            try {
                while (currentCoroutineContext().isActive) {
                    try {
                        refresh(force = false)
                    } catch (cancelled: CancellationException) {
                        // Clearing only the cache may cancel the shared request, not this lifecycle.
                        currentCoroutineContext().ensureActive()
                    }
                    val remaining = lastAttempt?.let { INTERVAL_MILLIS - (elapsedMillis() - it) } ?: INTERVAL_MILLIS
                    delay(remaining.coerceAtLeast(1))
                }
            } finally {
                withContext(NonCancellable) {
                    val request = requests.withLock { inFlight }
                    request?.cancelAndJoin()
                }
            }
        }

    suspend fun clearCache() =
        initialization.withLock {
            requests.withLock {
                inFlight?.cancelAndJoin()
                try {
                    store.clear()
                    mutable.update {
                        val next = it.override ?: AppConfig()
                        AppConfigState(
                            initialized = true,
                            override = it.override,
                            lastAppliedAt = if (next != it.config) now() else it.lastAppliedAt,
                        )
                    }
                    lastAttempt = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutable.update { it.copy(error = "설정 캐시 초기화 실패") }
                }
            }
        }

    fun setOverride(value: AppConfig?) {
        require(value == null || value.noticeMessage.count { !it.isLowSurrogate() } <= 200)
        mutable.update {
            val next = value ?: it.snapshot?.config ?: AppConfig()
            it.copy(override = value, lastAppliedAt = if (next != it.config) now() else it.lastAppliedAt)
        }
    }

    private suspend fun fetch() {
        mutable.update { it.copy(refreshing = true) }
        try {
            withTimeout(REQUEST_TIMEOUT_MILLIS) {
                val snapshot = load().validated()
                val old = mutable.value.snapshot
                require(old == null || snapshot.revision >= old.revision)
                require(old == null || snapshot.revision != old.revision || snapshot == old)
                val received = now()
                var storageFailed = false
                try {
                    store.write(json.encodeToString(CachedAppConfig(1, snapshot, received)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    storageFailed = true
                }
                // A suspended storage write must not publish a cancelled response.
                currentCoroutineContext().ensureActive()
                mutable.update {
                    val next = it.override ?: snapshot.config
                    it.copy(
                        snapshot = snapshot,
                        normalSource = AppConfigSource.SERVER,
                        lastFetchSucceededAt = received,
                        lastAppliedAt = if (next != it.config) now() else it.lastAppliedAt,
                        error = null,
                        storageError = storageFailed,
                    )
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            mutable.update { it.copy(error = "설정 조회 시간 초과: 기존값 유지") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutable.update { it.copy(error = "설정 조회 또는 검증 실패: 기존값 유지") }
        } finally {
            mutable.update { it.copy(refreshing = false) }
        }
    }

    companion object {
        const val INTERVAL_MILLIS = 60_000L
        const val REQUEST_TIMEOUT_MILLIS = 3_000L
    }
}

private fun monotonicClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
