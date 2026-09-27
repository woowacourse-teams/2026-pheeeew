package com.pheeeew.data.repository.emotion

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.emotion.EmotionApi
import com.pheeeew.data.remote.emotion.EmotionResponseMapper
import com.pheeeew.domain.model.emotion.EmotionBounds
import com.pheeeew.domain.model.emotion.EmotionReaction
import com.pheeeew.domain.repository.emotion.EmotionFailure
import com.pheeeew.domain.repository.emotion.EmotionRepository
import com.pheeeew.domain.repository.emotion.EmotionResult
import kotlinx.coroutines.CancellationException

internal class EmotionRepositoryImpl(
    private val api: EmotionApi,
) : EmotionRepository {
    override suspend fun firstPage(
        bounds: EmotionBounds,
        groupId: String?,
    ) = api
        .list(
            buildMap {
                put("minLongitude", bounds.west.toString())
                put("minLatitude", bounds.south.toString())
                put("maxLongitude", bounds.east.toString())
                put("maxLatitude", bounds.north.toString())
                groupId?.let { put("groupId", it) }
            },
        ).mapped(EmotionResponseMapper::page)

    override suspend fun nextPage(cursor: String) =
        api.list(mapOf("cursor" to cursor)).mapped(EmotionResponseMapper::page)

    override suspend fun detail(id: Long) = api.detail(id).mapped(EmotionResponseMapper::emotion)

    override suspend fun react(
        id: Long,
        type: EmotionReaction,
        selected: Boolean,
    ) = api.react(id, type.name, selected).mapped {
        it
    }

    override suspend fun block(id: Long) = api.block(id).mapped { it }
}

private inline fun <T, R> ApiResult<T>.mapped(transform: (T) -> R): EmotionResult<R> =
    when (this) {
        is ApiResult.Success -> {
            try {
                EmotionResult.Success(transform(value))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                EmotionResult.Failure(EmotionFailure.UNAVAILABLE)
            }
        }

        is ApiResult.Failure -> {
            EmotionResult.Failure(
                when (val failure = reason) {
                    is NetworkFailure.SessionUnavailable, is NetworkFailure.SessionProviderFailed -> {
                        EmotionFailure.AUTHENTICATION
                    }

                    is NetworkFailure.HttpStatus -> {
                        when (failure.statusCode) {
                            404 -> EmotionFailure.NOT_FOUND
                            400 -> EmotionFailure.INVALID_REQUEST
                            401 -> EmotionFailure.AUTHENTICATION
                            403, 409 -> EmotionFailure.FORBIDDEN
                            else -> EmotionFailure.UNAVAILABLE
                        }
                    }

                    else -> {
                        EmotionFailure.UNAVAILABLE
                    }
                },
            )
        }
    }
