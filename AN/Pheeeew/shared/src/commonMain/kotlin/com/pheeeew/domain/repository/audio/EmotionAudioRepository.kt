package com.pheeeew.domain.repository.audio

fun interface EmotionAudioRepository {
    suspend fun download(playbackUrl: String): ByteArray
}
