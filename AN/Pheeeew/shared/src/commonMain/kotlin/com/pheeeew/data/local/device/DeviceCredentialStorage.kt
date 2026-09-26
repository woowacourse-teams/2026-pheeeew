package com.pheeeew.data.local.device

import com.pheeeew.domain.model.device.DeviceCredentials

sealed interface CredentialRead {
    class Found(
        val credentials: DeviceCredentials,
    ) : CredentialRead

    data object Missing : CredentialRead

    data class Failure(
        val legacyEnvironmentUnknown: Boolean = false,
    ) : CredentialRead
}

interface DeviceCredentialStorage {
    suspend fun read(): CredentialRead

    /** Atomically replaces the credential record and confirms durable storage. */
    suspend fun write(credentials: DeviceCredentials): Boolean
}

/** Explicit migration decision supplied only after verifying the old install's environment. */
enum class LegacyCredentialPolicy { UNCONFIRMED, IMPORT_CURRENT_ENVIRONMENT, BELONGS_TO_OTHER_ENVIRONMENT }

/** Copy only; callers never delete the legacy entry, including on failed persistence. */
internal suspend fun migrateLegacyCredentials(
    token: String?,
    policy: LegacyCredentialPolicy,
    persist: suspend (DeviceCredentials) -> Boolean,
): CredentialRead {
    if (token == null || policy == LegacyCredentialPolicy.BELONGS_TO_OTHER_ENVIRONMENT) return CredentialRead.Missing
    if (policy == LegacyCredentialPolicy.UNCONFIRMED) return CredentialRead.Failure(true)
    if (token.isBlank()) return CredentialRead.Failure()
    val migrated = DeviceCredentials(refreshToken = token, generation = 1, preservesLegacyIdentity = true)
    return if (persist(migrated)) CredentialRead.Found(migrated) else CredentialRead.Failure()
}
