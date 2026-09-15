package com.pheeeew.domain.exception

sealed class ApiException(
    val code: String,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : Exception(message) {
    class InvalidRequest(
        code: String,
        message: String,
    ) : ApiException(code, message)

    class Unauthorized(
        code: String,
        message: String,
    ) : ApiException(code, message)

    class Forbidden(
        code: String,
        message: String,
    ) : ApiException(code, message)

    class NotFound(
        code: String,
        message: String,
    ) : ApiException(code, message)

    class Gone(
        code: String,
        message: String,
    ) : ApiException(code, message)

    class Conflict(
        code: String,
        message: String,
    ) : ApiException(code, message)

    class Unknown(
        code: String,
        message: String,
        retryAfterSeconds: Long? = null,
    ) : ApiException(code, message, retryAfterSeconds)

    class Network(
        code: String,
        message: String,
    ) : ApiException(code, message)
}
