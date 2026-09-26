package com.pheeeew.core.network

import io.ktor.http.HttpMethod

enum class RequestKind {
    READ,
    WRITE,
}

enum class AuthenticationRequirement {
    REQUIRED,
    NONE,
}

data class ApiRequest(
    val method: HttpMethod,
    val path: String,
    val kind: RequestKind,
    val authentication: AuthenticationRequirement = AuthenticationRequirement.REQUIRED,
    val queryParameters: Map<String, String> = emptyMap(),
    val body: Any? = null,
) {
    init {
        require(path.isNotBlank()) { "API path는 비어 있을 수 없습니다." }
        require(!path.startsWith("http://") && !path.startsWith("https://")) {
            "API 요청에는 base URL이 아닌 상대 경로를 사용해야 합니다."
        }
    }
}
