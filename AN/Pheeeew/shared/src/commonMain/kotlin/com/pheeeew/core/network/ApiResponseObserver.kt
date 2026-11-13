package com.pheeeew.core.network

/** Observes only response status, never the URL, headers, body or exception. */
fun interface ApiResponseObserver {
    fun completed(statusCode: Int?)
}
