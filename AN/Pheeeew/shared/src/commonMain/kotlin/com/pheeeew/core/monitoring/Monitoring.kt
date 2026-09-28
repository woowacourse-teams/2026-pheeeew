package com.pheeeew.core.monitoring

/** Feature API. A call requests local collection, not server acknowledgement. */
interface Monitoring {
    fun context(
        screen: String,
        parent: EventContext? = null,
    ): EventContext

    fun track(
        event: MonitoringEvent,
        context: EventContext,
    )

    fun reportError(
        error: Throwable,
        context: EventContext,
    )
}

interface MonitoringEvent {
    val definition: EventDefinition

    fun properties(): Map<String, EventValue>
}

@ConsistentCopyVisibility
data class EventContext internal constructor(
    internal val owner: String,
    val sessionId: String,
    val operationId: String,
    val screen: String,
    val parentOperationId: String? = null,
)

sealed interface EventValue {
    data class Text(
        val value: String,
    ) : EventValue

    data class Integer(
        val value: Long,
    ) : EventValue

    data class Decimal(
        val value: Double,
    ) : EventValue

    data class Flag(
        val value: Boolean,
    ) : EventValue
}

object NoOpMonitoring : Monitoring {
    override fun context(
        screen: String,
        parent: EventContext?,
    ) = EventContext("", "", "", "unknown")

    override fun track(
        event: MonitoringEvent,
        context: EventContext,
    ) = Unit

    override fun reportError(
        error: Throwable,
        context: EventContext,
    ) = Unit
}

data class DefinedEvent(
    override val definition: EventDefinition,
    private val values: Map<String, EventValue> = emptyMap(),
) : MonitoringEvent {
    override fun properties(): Map<String, EventValue> = values
}
