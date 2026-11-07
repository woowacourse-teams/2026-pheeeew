package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.CollectionCalendarDto
import com.pheeeew.data.remote.dto.CollectionCardDto
import com.pheeeew.data.remote.dto.CollectionImagesDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionMapperDateTest {
    @Test
    fun `calendar requires real dates and supports the leap day`() {
        val url = "https://example.test/image.png"
        val card =
            CollectionCardDto(
                cardId = "00000000-0000-0000-0000-000000000001",
                date = "2024-02-29",
                state = "EXHAUSTED",
                stampCount = 1,
                pressCount = 100,
                images = CollectionImagesDto(url, url, url),
            )
        val calendar = CollectionMapper.toDomain(CollectionCalendarDto(listOf(card)), "2024-02")
        assertEquals(
            card.date,
            calendar.cards
                .single()
                .reference.date,
        )
        for (date in listOf("2026-02-29", "2026-02-31", "0000-01-01")) {
            assertFailsWith<CollectionContractException> {
                CollectionMapper.toDomain(CollectionCalendarDto(listOf(card.copy(date = date))), date.take(7))
            }
        }
    }
}
