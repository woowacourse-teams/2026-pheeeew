package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.CollectionActivityProgressDto
import com.pheeeew.data.remote.dto.CollectionCardDto
import com.pheeeew.data.remote.dto.CollectionCardReferenceDto
import com.pheeeew.data.remote.dto.CollectionCountsDto
import com.pheeeew.data.remote.dto.CollectionHistoryDto
import com.pheeeew.data.remote.dto.CollectionImagesDto
import com.pheeeew.data.remote.dto.CollectionTodayDto
import com.pheeeew.domain.model.collection.CollectionActivityProgress
import com.pheeeew.domain.model.collection.CollectionCard
import com.pheeeew.domain.model.collection.CollectionCardReference
import com.pheeeew.domain.model.collection.CollectionCounts
import com.pheeeew.domain.model.collection.CollectionHistory
import com.pheeeew.domain.model.collection.CollectionImages
import com.pheeeew.domain.model.collection.CollectionToday
import com.pheeeew.domain.model.collection.CollectionTodayItem
import com.pheeeew.domain.model.emotion.EmotionState

internal class CollectionContractException(
    field: String,
) : IllegalArgumentException("컬렉션 응답 계약이 올바르지 않습니다: $field")

internal object CollectionMapper {
    fun toDomain(dto: CollectionTodayDto): CollectionToday {
        requireDate(dto.date, "date")
        if (dto.items.map { state(it.state) }.toSet() != EmotionState.entries.toSet() ||
            dto.items.size != EmotionState.entries.size
        ) {
            throw CollectionContractException("items")
        }
        return CollectionToday(
            date = dto.date,
            items =
                dto.items.map { item ->
                    val emotion = state(item.state)
                    if (item.stamps.target != STAMP_TARGET || item.presses.target != PRESS_TARGET) {
                        throw CollectionContractException("items.target")
                    }
                    val card = item.card?.let(::toDomain)
                    if (card != null && (card.date != dto.date || card.state != emotion)) {
                        throw CollectionContractException("items.card")
                    }
                    CollectionTodayItem(
                        state = emotion,
                        stamps = item.stamps.toDomain("stamps"),
                        presses = item.presses.toDomain("presses"),
                        card = card,
                    )
                },
        )
    }

    fun toDomain(dto: CollectionHistoryDto): CollectionHistory {
        if (dto.nickname != null && dto.nickname.isBlank()) throw CollectionContractException("nickname")
        if (dto.nextCursor != null && dto.nextCursor.isBlank()) throw CollectionContractException("nextCursor")
        val nextCursor = dto.nextCursor
        if (dto.hasNext != (nextCursor != null)) throw CollectionContractException("nextCursor")
        val counts = dto.counts.toDomain()
        return CollectionHistory(
            nickname = dto.nickname,
            cards = dto.items.map(::toDomain),
            counts = counts,
            hasNext = dto.hasNext,
            nextCursor = nextCursor,
        )
    }

    fun toDomain(dto: CollectionCardDto): CollectionCard {
        val stampCount = dto.stampCount.requireCount("stampCount")
        val pressCount = dto.pressCount.requireCount("pressCount")
        return CollectionCard(
            reference = dto.toReference(),
            stampCount = stampCount,
            pressCount = pressCount,
        )
    }

    fun toDomain(dto: CollectionCardReferenceDto): CollectionCardReference = dto.toReference()

    private fun CollectionCardDto.toReference(): CollectionCardReference {
        requireDate(date, "date")
        return CollectionCardReference(
            cardId = cardId.requireCardId(),
            date = date,
            state = state(state),
            images = images.toDomain(),
        )
    }

    private fun CollectionCardReferenceDto.toReference(): CollectionCardReference {
        requireDate(date, "date")
        return CollectionCardReference(
            cardId = cardId.requireCardId(),
            date = date,
            state = state(state),
            images = images.toDomain(),
        )
    }

    private fun CollectionCountsDto.toDomain(): CollectionCounts {
        if (total < 0L) throw CollectionContractException("counts.total")
        if (byEmotion.keys != EmotionState.entries.map(EmotionState::name).toSet()) {
            throw CollectionContractException("counts.byEmotion")
        }
        val counts =
            EmotionState.entries.associateWith { emotion ->
                (byEmotion[emotion.name] ?: throw CollectionContractException("counts.byEmotion.${emotion.name}"))
                    .requireCount("counts.byEmotion.${emotion.name}")
            }
        if (total != counts.values.sum()) throw CollectionContractException("counts.total")
        return CollectionCounts(total, counts)
    }

    private fun CollectionActivityProgressDto.toDomain(field: String): CollectionActivityProgress {
        val validCount = count.requireCount("$field.count")
        if (target < 0L) throw CollectionContractException("$field.target")
        val expectedRemaining = (target - validCount).coerceAtLeast(0L)
        if (remaining != expectedRemaining) throw CollectionContractException("$field.remaining")
        return CollectionActivityProgress(validCount, target, remaining)
    }

    private fun CollectionImagesDto.toDomain(): CollectionImages {
        listOf(characterUrl, roundStampUrl, straightStampUrl).forEach { url ->
            if (!url.startsWith("https://") || url.length <= "https://".length) {
                throw CollectionContractException("images")
            }
        }
        return CollectionImages(characterUrl, roundStampUrl, straightStampUrl)
    }

    private fun state(value: String): EmotionState =
        runCatching { EmotionState.valueOf(value) }.getOrElse { throw CollectionContractException("state") }

    private fun requireDate(
        value: String,
        field: String,
    ) {
        if (!DATE_PATTERN.toRegex().matches(value)) throw CollectionContractException(field)
    }

    private fun String.requireCardId(): String =
        takeIf(UUID_PATTERN::matches) ?: throw CollectionContractException("cardId")

    private fun Long.requireCount(field: String): Long = takeIf { it >= 0L } ?: throw CollectionContractException(field)

    private const val DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}"
    private const val STAMP_TARGET = 1L
    private const val PRESS_TARGET = 100L
    private val UUID_PATTERN =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}
