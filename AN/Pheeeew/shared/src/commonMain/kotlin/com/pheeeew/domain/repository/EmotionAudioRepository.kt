package com.pheeeew.domain.repository

fun interface EmotionAudioRepository {
    suspend fun download(playbackUrl: String): ByteArray
}
