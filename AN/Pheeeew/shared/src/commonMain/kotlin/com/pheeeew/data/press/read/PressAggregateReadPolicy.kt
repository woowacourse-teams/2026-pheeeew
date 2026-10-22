package com.pheeeew.data.press.read

/** A detached GET must not replace a newer read or race an active/uncertain write. */
internal object PressAggregateReadPolicy {
    fun canApply(
        requestedGeneration: Long,
        currentGeneration: Long,
        writeInFlight: Boolean,
        writeOutcomeUnknown: Boolean,
    ): Boolean = requestedGeneration == currentGeneration && !writeInFlight && !writeOutcomeUnknown
}
