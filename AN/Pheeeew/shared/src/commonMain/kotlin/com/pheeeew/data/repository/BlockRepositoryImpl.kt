package com.pheeeew.data.repository

import com.pheeeew.data.remote.api.BlockApi
import com.pheeeew.data.remote.mapper.toModerationResult
import com.pheeeew.domain.repository.BlockRepository
import dev.zacsweers.metro.Inject

@Inject
class BlockRepositoryImpl(
    private val api: BlockApi,
) : BlockRepository {
    override suspend fun blockEmotion(emotionId: Long) = api.blockEmotion(emotionId).toModerationResult()

    override suspend fun blockUser(emotionId: Long) = api.blockUser(emotionId).toModerationResult()
}
