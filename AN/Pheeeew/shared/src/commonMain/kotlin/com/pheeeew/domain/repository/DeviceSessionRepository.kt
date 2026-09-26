package com.pheeeew.domain.repository

import com.pheeeew.domain.model.device.DeviceSessionResult

interface DeviceSessionRepository {
    /** Restores or registers a device. Never returns access before durable credential storage. */
    suspend fun prepare(): DeviceSessionResult
}
