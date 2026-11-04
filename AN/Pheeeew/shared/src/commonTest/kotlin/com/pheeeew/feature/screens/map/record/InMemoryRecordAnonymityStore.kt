package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.repository.RecordAnonymityStore

internal class InMemoryRecordAnonymityStore : RecordAnonymityStore {
    private var value: Boolean? = null

    override fun read(): Boolean? = value

    override suspend fun save(isAnonymous: Boolean) {
        value = isAnonymous
    }
}
