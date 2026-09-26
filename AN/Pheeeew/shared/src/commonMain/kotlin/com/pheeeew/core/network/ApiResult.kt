package com.pheeeew.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ApiResult<T>

    data class Failure(
        val reason: NetworkFailure,
    ) : ApiResult<Nothing>
}

sealed interface NetworkFailure {
    data class SessionUnavailable(
        val mutationCertainty: MutationCertainty,
    ) : NetworkFailure

    data class SessionProviderFailed(
        val mutationCertainty: MutationCertainty,
        val details: SessionFailureDetails? = null,
    ) : NetworkFailure

    data class HttpStatus(
        val statusCode: Int,
        val error: ApiError? = null,
        val retryAfter: String? = null,
        val mutationCertainty: MutationCertainty,
    ) : NetworkFailure

    data class Transport(
        val reason: TransportFailureReason,
        val mutationCertainty: MutationCertainty,
    ) : NetworkFailure

    data class Unexpected(
        val exceptionType: String,
        val mutationCertainty: MutationCertainty,
    ) : NetworkFailure

    data class Contract(
        val reason: ContractFailureReason,
        val mutationCertainty: MutationCertainty,
    ) : NetworkFailure
}

data class ApiError(
    val code: String?,
    val message: String?,
)

enum class MutationCertainty {
    NOT_A_MUTATION,
    NOT_APPLIED,
    UNKNOWN,
}

enum class TransportFailureReason {
    TIMEOUT,
    CONNECTION,
}

enum class ContractFailureReason {
    MALFORMED_REQUEST_BODY,
    MALFORMED_SUCCESS_BODY,
    EMPTY_SUCCESS_BODY,
    UNEXPECTED_EMPTY_RESPONSE,
}
