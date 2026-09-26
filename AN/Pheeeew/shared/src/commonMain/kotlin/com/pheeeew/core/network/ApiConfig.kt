package com.pheeeew.core.network

/** Environment settings supplied by the platform composition root. */
data class ApiConfig(
    val baseUrl: String,
    val timeouts: NetworkTimeouts = NetworkTimeouts(),
) {
    init {
        require(baseUrl.startsWith("https://")) { "API base URL은 HTTPS를 사용해야 합니다." }
        require(baseUrl.substringAfter("https://").isNotBlank()) { "API base URL이 비어 있습니다." }
    }
}

data class NetworkTimeouts(
    val requestMillis: Long = 20_000,
    val connectMillis: Long = 10_000,
    val socketMillis: Long = 15_000,
) {
    init {
        require(requestMillis > 0L)
        require(connectMillis > 0L)
        require(socketMillis > 0L)
    }
}
