package com.pheeeew.legacy.core.network

import com.pheeeew.legacy.domain.exception.ApiException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

fun Throwable.toApiException(): ApiException =
    when (this) {
        is CancellationException -> {
            throw this
        }

        is ConnectTimeoutException,
        is SocketTimeoutException,
        is IOException,
        -> {
            ApiException.Network(
                code = "NETWORK_ERROR",
                message = message ?: "네트워크 오류",
            )
        }

        else -> {
            ApiException.Unknown(
                code = "UNKNOWN_ERROR",
                message = message ?: "알 수 없는 오류",
            )
        }
    }
