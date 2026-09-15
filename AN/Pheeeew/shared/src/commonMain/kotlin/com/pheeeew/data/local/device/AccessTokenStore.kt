package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.AccessToken

interface AccessTokenStore {
    val accessToken: AccessToken?
    val accessTokenExpiresAtEpochSeconds: Long?
        get() = null

    fun save(accessToken: AccessToken)

    fun save(accessToken: AccessToken, expiresAtEpochSeconds: Long) {
        save(accessToken)
    }

    fun clear()
}

class InMemoryAccessTokenStore : AccessTokenStore {
    override var accessToken: AccessToken? = null
        private set
    override var accessTokenExpiresAtEpochSeconds: Long? = null
        private set

    override fun save(accessToken: AccessToken) {
        this.accessToken = accessToken
    }

    override fun save(accessToken: AccessToken, expiresAtEpochSeconds: Long) {
        this.accessToken = accessToken
        this.accessTokenExpiresAtEpochSeconds = expiresAtEpochSeconds
    }

    override fun clear() {
        accessToken = null
        accessTokenExpiresAtEpochSeconds = null
    }
}
