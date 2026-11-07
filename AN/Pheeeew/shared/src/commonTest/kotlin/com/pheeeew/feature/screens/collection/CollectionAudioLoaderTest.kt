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
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class CollectionAudioLoaderTest {
    @Test
    fun `expired or malformed audio requests its own emotion detail`() =
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

            loader.load(52, "https://", "2026-11-05T03:00:00Z", now)

            assertEquals(listOf(51L, 52L), requestedIds)
            assertEquals(
                listOf("https://example.com/valid", "https://example.com/fresh", "https://example.com/fresh"),
                downloads,
            )
        }

    @Test
    fun `invalid refreshed detail never downloads audio`() =
        runTest {
            val detail =
                EmotionDetail(
                    51,
                    EmotionState.ANGRY,
                    "나",
                    "2026-11-05T00:00:00Z",
                    null,
                    EmotionPlayback("https://example.com/fresh", "2026-11-05T03:00:00Z"),
                    isMine = true,
                    reactions = emptyList(),
                )
            val invalidDetails =
                listOf(
                    detail.copy(id = 52),
                    detail.copy(isMine = false),
                    detail.copy(audio = EmotionPlayback("https://example.com/expired", "2026-11-05T01:00:00Z")),
                    detail.copy(audio = EmotionPlayback("https://", "2026-11-05T03:00:00Z")),
                )
            for (invalid in invalidDetails) {
                val details =
                    object : EmotionDetailRepository {
                        override suspend fun findById(id: Long) = EmotionDetailResult.Success(invalid)

                        override suspend fun setReactionSelected(
                            emotionId: Long,
                            type: EmotionReactionType,
                            selected: Boolean,
                        ): Boolean = error("Unexpected reaction")
                    }
                var downloads = 0
                val loader =
                    CollectionAudioLoader(
                        details,
                        EmotionAudioRepository {
                            downloads++
                            byteArrayOf(1)
                        },
                    )
                assertFailsWith<IllegalStateException> {
                    loader.load(51, null, null, Instant.parse("2026-11-05T02:00:00Z"))
                }
                assertEquals(0, downloads)
            }
        }
}
