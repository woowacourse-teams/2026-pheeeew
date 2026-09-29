package com.pheeeew.feature.screens.group.monitoring

import com.pheeeew.core.monitoring.DefinedEvent
import com.pheeeew.core.monitoring.EventDefinition
import com.pheeeew.core.monitoring.EventValue
import com.pheeeew.core.monitoring.Monitoring
import com.pheeeew.core.monitoring.PropertyRule
import com.pheeeew.core.monitoring.ValueType
import com.pheeeew.feature.screens.group.create.CreateGroupAction
import com.pheeeew.feature.screens.group.create.CreateGroupResult
import com.pheeeew.feature.screens.group.create.GroupCreateDraft
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

object GroupMonitoringEvents {
    val started = EventDefinition("group_create_started")
    val finished =
        EventDefinition(
            "group_create_finished",
            properties =
                mapOf(
                    "outcome" to
                        PropertyRule(
                            ValueType.TEXT,
                            required = true,
                            allowed =
                                setOf(
                                    "created",
                                    "duplicate_name",
                                    "invalid_input",
                                    "rate_limited",
                                    "unavailable",
                                    "unknown",
                                    "cancelled",
                                    "unexpected",
                                ),
                        ),
                    "duration_ms" to PropertyRule(ValueType.INTEGER, required = true, minimum = 0.0),
                ),
        )
    val definitions = listOf(started, finished)
}

/** One operation per user submission, regardless of HTTP retries. Never records draft content. */
class MonitoredCreateGroupAction(
    private val delegate: CreateGroupAction,
    private val monitoring: Monitoring,
) : CreateGroupAction {
    override suspend fun create(draft: GroupCreateDraft): CreateGroupResult {
        val context = runCatching { monitoring.context("group_create") }.getOrNull()
        val start = TimeSource.Monotonic.markNow()
        var outcome = "unexpected"
        context?.let { runCatching { monitoring.track(DefinedEvent(GroupMonitoringEvents.started), it) } }
        try {
            return delegate.create(draft).also {
                outcome =
                    when (it) {
                        is CreateGroupResult.Created -> "created"
                        CreateGroupResult.DuplicateName -> "duplicate_name"
                        CreateGroupResult.InvalidInput -> "invalid_input"
                        CreateGroupResult.RateLimited -> "rate_limited"
                        CreateGroupResult.Unavailable -> "unavailable"
                        CreateGroupResult.OutcomeUnknown -> "unknown"
                    }
            }
        } catch (cancelled: CancellationException) {
            outcome = "cancelled"
            throw cancelled
        } catch (error: Exception) {
            context?.let { runCatching { monitoring.reportError(error, it) } }
            throw error
        } finally {
            context?.let {
                runCatching {
                    monitoring.track(
                        DefinedEvent(
                            GroupMonitoringEvents.finished,
                            mapOf(
                                "outcome" to EventValue.Text(outcome),
                                "duration_ms" to EventValue.Integer(start.elapsedNow().inWholeMilliseconds),
                            ),
                        ),
                        it,
                    )
                }
            }
        }
    }
}
