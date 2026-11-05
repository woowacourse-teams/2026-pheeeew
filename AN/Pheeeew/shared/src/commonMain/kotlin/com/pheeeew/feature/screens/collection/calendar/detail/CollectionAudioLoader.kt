package com.pheeeew.feature.screens.collection.calendar.detail

import com.pheeeew.domain.model.emotion.EmotionDetailResult
import com.pheeeew.domain.repository.EmotionAudioRepository
import com.pheeeew.domain.repository.EmotionDetailRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock
import kotlin.time.Instant

@Inject
internal class CollectionAudioLoader(
    private val details: EmotionDetailRepository,
    private val audio: EmotionAudioRepository,
) {
    suspend fun load(
        emotionId: Long,
        playbackUrl: String?,
        expiresAt: String?,
        now: Instant = Clock.System.now(),
    ): ByteArray {
        val url =
            if (playbackUrl != null && expiresAt != null && Instant.parse(expiresAt) > now) {
                playbackUrl
            } else {
                val detail = (details.findById(emotionId) as? EmotionDetailResult.Success)?.detail
                check(detail != null && detail.id == emotionId && detail.isMine)
                val playback = checkNotNull(detail.audio)
                check(Instant.parse(playback.expiresAt) > now)
                playback.url
            }
        currentCoroutineContext().ensureActive()
        return audio.download(url)
    }
}
