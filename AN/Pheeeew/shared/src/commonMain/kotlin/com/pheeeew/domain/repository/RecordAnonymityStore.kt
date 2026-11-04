package com.pheeeew.domain.repository

interface RecordAnonymityStore {
    fun read(): Boolean?

    suspend fun save(isAnonymous: Boolean)
}
