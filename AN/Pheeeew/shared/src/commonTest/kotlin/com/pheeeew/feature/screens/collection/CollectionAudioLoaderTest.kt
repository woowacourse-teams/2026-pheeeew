package com.pheeeew.feature.screens.collection

import com.pheeeew.domain.model.emotion.EmotionDetail
import com.pheeeew.domain.model.emotion.EmotionDetailResult
import com.pheeeew.domain.model.emotion.EmotionPlayback
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.EmotionAudioRepository
import com.pheeeew.domain.repository.EmotionDetailRepository
import com.pheeeew.feature.screens.collection.calendar.detail.CollectionAudioLoader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CollectionAudioLoaderTest {
    @Test
    fun `only expired audio requests its own emotion detail`() =
        runTest {
            val requestedIds = mutableListOf<Long>()
            val downloads = mutableListOf<String>()
            val details =
                object : EmotionDetailRepository {
                    override suspend fun findById(id: Long): EmotionDetailResult {
                        requestedIds += id
                        return EmotionDetailResult.Success(
                            EmotionDetail(
                                id,
                                EmotionState.ANGRY,
                                "나",
                                "2026-11-05T00:00:00Z",
                                null,
                                EmotionPlayback("https://example.com/fresh", "2026-11-05T03:00:00Z"),
                                isMine = true,
                                reactions = emptyList(),
                            ),
                        )
                    }

                    override suspend fun setReactionSelected(
                        emotionId: Long,
                        type: EmotionReactionType,
                        selected: Boolean,
                    ): Boolean = error("Unexpected reaction")
                }
            val loader =
                CollectionAudioLoader(
                    details,
                    EmotionAudioRepository {
                        downloads += it
                        byteArrayOf(1)
                    },
                )
            val now = Instant.parse("2026-11-05T02:00:00Z")
            loader.load(1, "https://example.com/valid", "2026-11-05T03:00:00Z", now)
            loader.load(51, "https://example.com/expired", "2026-11-05T01:00:00Z", now)

            assertEquals(listOf(51L), requestedIds)
            assertEquals(listOf("https://example.com/valid", "https://example.com/fresh"), downloads)
        }
}
