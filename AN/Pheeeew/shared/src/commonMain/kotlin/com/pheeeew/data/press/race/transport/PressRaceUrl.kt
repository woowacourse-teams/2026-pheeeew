package com.pheeeew.data.press.race.transport

import com.pheeeew.core.network.ApiConfig
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath

/** Uses the API environment, not the Swagger host. Credentials and queries never belong in this URL. */
internal fun pressRaceUrl(config: ApiConfig): Url {
    val base = Url(config.baseUrl)
    require(base.protocol == URLProtocol.HTTPS && base.host.isNotBlank())
    require(base.encodedPath.isEmpty() || base.encodedPath == "/") { "API base URL must be an origin" }
    require(
        base.parameters.isEmpty() && base.fragment.isEmpty(),
    ) { "API base URL must not contain a query or fragment" }
    require(base.encodedUser.isNullOrEmpty() && base.encodedPassword.isNullOrEmpty()) {
        "API base URL must not contain credentials"
    }
    return URLBuilder(base)
        .apply {
            protocol = URLProtocol.WSS
            encodedPath = "/api/v1/press-race"
        }.build()
}
