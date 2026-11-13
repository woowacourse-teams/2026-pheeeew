package com.pheeeew.core.network

/** No URL, headers, body or exception objects cross this boundary. */
fun interface ApiAttemptObserver {
    fun started(
        endpoint: String,
        method: String,
    ): ApiAttempt
}

fun interface ApiAttempt {
    fun completed(
        outcome: HttpAttemptOutcome,
        statusCode: Int?,
        durationMs: Long,
    )
}

enum class HttpAttemptOutcome { RESPONSE, TIMEOUT, CONNECTION, CONTRACT, UNEXPECTED, CANCELLED }
