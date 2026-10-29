package com.pheeeew.data.repository

import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.NoOpMonitoring
import com.pheeeew.data.press.delivery.PressDeliveryPolicy
import com.pheeeew.data.press.retry.PressRetryPolicy
import com.pheeeew.data.press.session.PressSession
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.press.PressAcceptance
import com.pheeeew.domain.press.time.PressDelayMillis
import com.pheeeew.domain.press.time.PressEpochMillis
import com.pheeeew.domain.press.time.PressMonotonicMillis
import com.pheeeew.domain.repository.PressRepository
import com.pheeeew.domain.repository.PressSender
import com.pheeeew.domain.repository.PressStatisticsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.TimeSource

/** Repository adapter; the app-session runtime owns all mutable state and coroutine work. */
internal class PressRepositoryImpl(
    statistics: PressStatisticsDataSource,
    sender: PressSender,
    sessionScope: CoroutineScope,
    batchMaxPerEmotion: Int = 30,
    batchMaxTotal: Int = 100,
    flushDelayMillis: PressDelayMillis = PressDelayMillis(500L),
    retryPolicy: PressRetryPolicy = PressRetryPolicy(),
    deliveryPolicy: PressDeliveryPolicy = PressDeliveryPolicy(),
    aggregateRefreshMillis: PressDelayMillis = PressDelayMillis(1_000L),
    connectivity: Flow<Boolean>? = null,
    nowMillis: () -> PressMonotonicMillis = monotonicClock(),
    monitoring: Monitoring = NoOpMonitoring,
    wallClock: () -> PressEpochMillis = {
        PressEpochMillis(
            Clock.System.now().toEpochMilliseconds(),
        )
    },
) : PressRepository {
    private val session =
        PressSession(
            statistics = statistics,
            sender = sender,
            sessionScope = sessionScope,
            batchMaxPerEmotion = batchMaxPerEmotion,
            batchMaxTotal = batchMaxTotal,
            flushDelayMillis = flushDelayMillis,
            retryPolicy = retryPolicy,
            deliveryPolicy = deliveryPolicy,
            aggregateRefreshMillis = aggregateRefreshMillis,
            connectivity = connectivity,
            nowMillis = nowMillis,
            monitoring = monitoring,
            wallClock = wallClock,
        )

    override val state = session.state

    override fun accept(emotion: EmotionState): PressAcceptance = session.accept(emotion)

    override fun refreshToday(force: Boolean) {
        session.refreshToday(force)
    }

    override fun retryUnsent() {
        session.retryUnsent()
    }

    override fun onForeground() {
        session.onForeground()
    }

    private companion object {
        fun monotonicClock(): () -> PressMonotonicMillis {
            val origin = TimeSource.Monotonic.markNow()
            return { PressMonotonicMillis(origin.elapsedNow().inWholeMilliseconds) }
        }
    }
}
