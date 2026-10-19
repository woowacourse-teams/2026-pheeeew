package com.pheeeew.domain.repository

/** The last group used by a successful registration. Null means no group. */
interface LastRecordedGroupRepository {
    suspend fun readGroupId(): String?

    suspend fun writeGroupId(groupId: String?)
}
