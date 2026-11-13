package com.pheeeew.data.remote.mapper

import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.domain.model.device.DeviceSessionFailureKind

/** Preserve the cause of authentication failures instead of replacing it with authentication. */
internal fun NetworkFailure.deviceSessionFailureKind(): DeviceSessionFailureKind =
    when (this) {
        is NetworkFailure.Transport -> {
            DeviceSessionFailureKind.NETWORK
        }

        is NetworkFailure.Contract -> {
            DeviceSessionFailureKind.CONTRACT
        }

        is NetworkFailure.Unexpected -> {
            DeviceSessionFailureKind.UNEXPECTED
        }

        is NetworkFailure.SessionUnavailable -> {
            DeviceSessionFailureKind.AUTHENTICATION
        }

        is NetworkFailure.SessionProviderFailed -> {
            DeviceSessionFailureKind.entries.firstOrNull { it.name == details?.reason }
                ?: DeviceSessionFailureKind.UNEXPECTED
        }

        is NetworkFailure.HttpStatus -> {
            when {
                statusCode == 429 || error?.code == "DEVICE-007" -> DeviceSessionFailureKind.RATE_LIMITED
                statusCode >= 500 -> DeviceSessionFailureKind.SERVER
                error?.code in setOf("DEVICE-005", "DEVICE-006") -> DeviceSessionFailureKind.ATTESTATION
                statusCode == 401 || statusCode == 403 -> DeviceSessionFailureKind.AUTHENTICATION
                else -> DeviceSessionFailureKind.SERVER
            }
        }
    }
