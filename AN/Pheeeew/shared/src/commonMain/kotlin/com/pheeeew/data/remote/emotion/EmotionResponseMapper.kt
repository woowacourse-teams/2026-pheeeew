package com.pheeeew.data.remote.emotion

import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.Emotion
import com.pheeeew.domain.model.emotion.EmotionAudio
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionPage
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.GroupStampFrame
import com.pheeeew.domain.model.group.StampColor
import kotlin.time.Instant

internal object EmotionResponseMapper {
    fun page(dto: EmotionPageDto): EmotionPage {
        require(!dto.hasNext || !dto.nextCursor.isNullOrBlank())
        return EmotionPage(dto.items.map(::emotion), dto.nextCursor.takeIf { dto.hasNext })
    }

    fun feedPage(dto: EmotionPageDto): EmotionPage {
        val page = page(dto)
        require(
            page.items.all {
                it.contentType == EmotionContentType.MEMO || it.contentType == EmotionContentType.AUDIO
            },
        )
        require(page.items.all { it.contentType != EmotionContentType.MEMO || it.memo != null })
        require(page.items.all { it.contentType != EmotionContentType.AUDIO || it.audio != null })
        return page
    }

    fun emotion(dto: EmotionDto): Emotion {
        require(dto.id > 0)
        val p = dto.properties
        val reactions =
            p.emojis.map {
                require(it.count >= 0)
                ReactionCount(EmotionReactionType.valueOf(it.type), it.count, it.selected)
            }
        require(reactions.map { it.type }.distinct().size == reactions.size)
        val audio =
            p.audio?.let {
                require(it.playbackUrl.startsWith("https://"))
                EmotionAudio(it.playbackUrl, Instant.parse(it.expiresAt))
            }
        return Emotion(
            dto.id,
            EmotionState.valueOf(p.state),
            p.nickname,
            Instant.parse(p.createdAt),
            p.isMine,
            EmotionContentType.valueOf(p.contentType),
            p.memo,
            EmotionReactionType.entries.map { type ->
                reactions.find { it.type == type }
                    ?: ReactionCount(type, 0, false)
            },
            p.groupStamp?.let {
                GroupStamp(
                    it.text,
                    requireNotNull(StampColor.parseServerValue(it.textColor)),
                    requireNotNull(StampColor.parseServerValue(it.backgroundColor)),
                    GroupStampFrame.valueOf(it.frame),
                )
            },
            audio,
            dto.geometry?.toCoordinateOrNull(),
        )
    }

    private fun EmotionPointDto.toCoordinateOrNull(): GeoCoordinate? {
        if (type != null && type != "Point") return null
        if (coordinates.size != 2) return null
        val longitude = coordinates[0]
        val latitude = coordinates[1]
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
        return GeoCoordinate(latitude, longitude)
    }
}
