package com.pheeeew.data.repository.audio

import com.pheeeew.domain.repository.audio.EmotionAudioRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Url

class EmotionAudioRepositoryImpl : EmotionAudioRepository {
    override suspend fun download(playbackUrl: String): ByteArray {
        require(Url(playbackUrl).protocol.name == "https")
        // The signed storage URL is fetched without the API's bearer token.
        return HttpClient {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }.use { client ->
            client.get(playbackUrl).bodyAsBytes().also { check(it.isNotEmpty()) }
        }
    }
}
