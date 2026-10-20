package com.pheeeew.data.repository

import com.pheeeew.domain.repository.EmotionAudioRepository
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Url

class EmotionAudioRepositoryImpl(
    private val engineFactory: (() -> HttpClientEngine)? = null,
) : EmotionAudioRepository {
    override suspend fun download(playbackUrl: String): ByteArray {
        require(Url(playbackUrl).protocol.name == "https")
        // Signed storage requests never receive the API's bearer token.
        val configure: HttpClientConfig<*>.() -> Unit = {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
        val engine = engineFactory?.invoke()
        val client = if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
        return try {
            client.use { it.get(playbackUrl).bodyAsBytes().also { bytes -> check(bytes.isNotEmpty()) } }
        } finally {
            engine?.close()
        }
    }
}
