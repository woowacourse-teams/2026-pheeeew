package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.api.EmotionApi
import com.pheeeew.data.remote.dto.EmotionDetailDto
import com.pheeeew.domain.model.emotion.EmotionDetail
import com.pheeeew.domain.model.emotion.EmotionDetailResult
import com.pheeeew.domain.model.emotion.EmotionPlayback
import com.pheeeew.domain.model.emotion.EmotionReaction
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.EmotionDetailRepository
import dev.zacsweers.metro.Inject
import kotlin.time.Instant

@Inject
class EmotionDetailRepositoryImpl(
    private val api: EmotionApi,
) : EmotionDetailRepository {
    override suspend fun findById(id: Long): EmotionDetailResult {
        if (id <= 0) return EmotionDetailResult.NotFound
        return when (val result = api.findById(id)) {
            is ApiResult.Success -> {
                runCatching {
                    require(result.value.id == id)
                    EmotionDetailResult.Success(result.value.toDomain())
                }.getOrElse { EmotionDetailResult.InvalidResponse }
            }

            is ApiResult.Failure -> {
                when (val reason = result.reason) {
                    is NetworkFailure.HttpStatus -> {
                        when (reason.statusCode) {
                            404 -> EmotionDetailResult.NotFound
                            401 -> EmotionDetailResult.Unauthorized
                            else -> EmotionDetailResult.Unavailable
                        }
                    }

                    is NetworkFailure.SessionUnavailable, is NetworkFailure.SessionProviderFailed -> {
                        EmotionDetailResult.Unauthorized
                    }

                    is NetworkFailure.Contract -> {
                        EmotionDetailResult.InvalidResponse
                    }

                    else -> {
                        EmotionDetailResult.Unavailable
                    }
                }
            }
        }
    }

    override suspend fun setReactionSelected(
        emotionId: Long,
        type: EmotionReactionType,
        selected: Boolean,
    ): Boolean = api.react(emotionId, type, selected) is ApiResult.Success

    private fun EmotionDetailDto.toDomain(): EmotionDetail {
        require(id > 0 && geometry.coordinates.size == 2)
        require(geometry.coordinates[0].isFinite() && geometry.coordinates[0] in -180.0..180.0)
        require(geometry.coordinates[1].isFinite() && geometry.coordinates[1] in -90.0..90.0)
        require(geometry.type == null || geometry.type == "Point")
        Instant.parse(properties.createdAt)
        val contentType = properties.contentType
        require(contentType in setOf("NONE", "MEMO", "AUDIO"))
        val audio =
            if (contentType == "AUDIO") {
                val value = requireNotNull(properties.audio)
                require(value.playbackUrl.startsWith("https://"))
                Instant.parse(value.expiresAt)
                EmotionPlayback(value.playbackUrl, value.expiresAt)
            } else {
                null
            }
        val reactions =
            properties.emojis.map {
                require(it.count >= 0)
                EmotionReaction(EmotionReactionType.valueOf(it.type), it.count, it.selected)
            }
        require(reactions.map { it.type }.toSet() == EmotionReactionType.entries.toSet())
        require(reactions.size == EmotionReactionType.entries.size)
        return EmotionDetail(
            id,
            EmotionState.valueOf(properties.state),
            properties.nickname,
            properties.createdAt,
            properties.memo.takeIf { contentType == "MEMO" },
            audio,
            properties.isMine,
            reactions,
        )
    }
}
