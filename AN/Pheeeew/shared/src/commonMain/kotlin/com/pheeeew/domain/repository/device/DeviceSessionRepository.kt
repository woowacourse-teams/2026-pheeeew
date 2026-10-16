package com.pheeeew.domain.repository.device

import com.pheeeew.domain.model.device.DeviceSessionResult

interface DeviceSessionRepository {
    /** Restores or registers a device. Never returns access before durable credential storage. */
    suspend fun prepare(): DeviceSessionResult

    /** Restores an existing session without registering a new device as a side effect. */
    suspend fun restore(): DeviceSessionResult = prepare()

    /** Registers a new device only after the user has supplied a nickname. */
    suspend fun register(nickname: String): DeviceSessionResult = prepare()
}
