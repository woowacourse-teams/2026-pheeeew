package com.pheeeew.data.press.race.transport

import com.pheeeew.core.network.AccessToken
import com.pheeeew.core.network.ApiConfig

/** Owns a dedicated WS client. The app-session owner closes it, never an individual screen. */
internal enum class PressRaceEndpoint { Competition, RecoveryOnly }

internal interface PressRaceTransport {
    /** Cancellation while opening must dispose the entire attempt, including a late Upgrade. */
    suspend fun connect(
        accessToken: AccessToken,
        endpoint: PressRaceEndpoint = PressRaceEndpoint.Competition,
    ): PressRaceConnection

    fun close()
}

/** One text-only connection, with one receive consumer. No JSON, retry or settlement policy here. */
internal interface PressRaceConnection {
    /** Enqueues a complete UTF-8 message; returning does not imply server receipt or an ACK. */
    suspend fun send(text: String)

    /** Returns null on a normal channel end; transport failures remain exceptions. */
    suspend fun receive(): String?

    /** Preserves server close codes such as 1008 / AUTH_EXPIRED for the session layer. */
    suspend fun awaitCloseReason(): PressRaceClose?

    suspend fun close()
}

/** Upgrade status only: no response body, headers or credentials retained in the exception. */
internal class PressRaceUpgradeException(
    val statusCode: Int,
) : Exception("Press race Upgrade failed (HTTP $statusCode)")

internal data class PressRaceClose(
    val code: Short,
    val reason: String,
)

internal expect fun createPlatformPressRaceTransport(config: ApiConfig): PressRaceTransport
