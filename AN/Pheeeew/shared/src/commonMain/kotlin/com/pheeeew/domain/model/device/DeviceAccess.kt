package com.pheeeew.domain.model.device

/** Secret values deliberately have no generated toString. */
class DeviceAccess(
    val value: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val generation: Long,
) {
    init {
        require(value.isNotBlank())
        require(expiresAtMillis > issuedAtMillis)
    }

    fun isUsable(now: Long): Boolean {
        val margin = minOf(60_000L, (expiresAtMillis - issuedAtMillis) / 10)
        return now >= issuedAtMillis && now < expiresAtMillis - margin
    }

    override fun toString(): String = "DeviceAccess(<redacted>)"
}
