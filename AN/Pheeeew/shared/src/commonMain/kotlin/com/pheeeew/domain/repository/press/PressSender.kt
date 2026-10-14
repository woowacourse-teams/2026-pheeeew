package com.pheeeew.domain.repository.press

import com.pheeeew.domain.model.press.PressBatch
import com.pheeeew.domain.model.press.PressSendResult

/** Client-internal send boundary; its payload is not the server request schema. */
fun interface PressSender {
    /** Null until server duplicate prevention and its retention period are confirmed. */
    val idempotencyWindowMillis: Long?
        get() = null

    suspend fun send(batch: PressBatch): PressSendResult
}
