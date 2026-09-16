package com.pheeeew.core.network

data class ApiConfig(
    val baseUrl: String,
) {
    init {
        require(baseUrl.startsWith("https://")) {
            "API base URL은 HTTPS를 사용해야 합니다."
        }
    }
}
