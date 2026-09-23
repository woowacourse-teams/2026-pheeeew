package com.pheeeew.legacy.core.network

import io.ktor.client.HttpClient

expect fun createPlatformHttpClient(config: ApiConfig): HttpClient
