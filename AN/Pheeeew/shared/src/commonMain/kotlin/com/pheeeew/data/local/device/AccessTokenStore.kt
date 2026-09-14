package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.AccessToken

interface AccessTokenStore {
    val accessToken: AccessToken?

    fun save(accessToken: AccessToken)

    fun clear()
}

class InMemoryAccessTokenStore : AccessTokenStore {
    override var accessToken: AccessToken? = null
        private set

    override fun save(accessToken: AccessToken) {
        this.accessToken = accessToken
    }

    override fun clear() {
        accessToken = null
    }
}
