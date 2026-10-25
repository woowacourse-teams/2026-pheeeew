package com.pheeeew.core.notification

data class RevisitNotificationPlan(
    val lastAccessEpochMillis: Long,
    val fireTimesEpochMillis: List<Long>,
)

/** Elapsed-time policy: 48 hours first, then 24 hours apart, for 30 deliveries. */
class RevisitNotificationPolicy(
    private val firstDelayMillis: Long = 48L * 60 * 60 * 1000,
    private val intervalMillis: Long = 24L * 60 * 60 * 1000,
    private val count: Int = 30,
) {
    init {
        require(firstDelayMillis > 0)
        require(intervalMillis > 0)
        require(count in 1..30)
    }

    fun plan(lastAccessEpochMillis: Long): RevisitNotificationPlan {
        val lastOffset = firstDelayMillis + intervalMillis * (count - 1)
        require(lastOffset > 0 && lastAccessEpochMillis <= Long.MAX_VALUE - lastOffset)
        return RevisitNotificationPlan(
            lastAccessEpochMillis,
            List(count) { lastAccessEpochMillis + firstDelayMillis + intervalMillis * it },
        )
    }
}
