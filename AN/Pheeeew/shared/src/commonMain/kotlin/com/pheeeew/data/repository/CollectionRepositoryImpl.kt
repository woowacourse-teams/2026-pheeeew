package com.pheeeew.data.repository

import com.pheeeew.core.network.ApiResult
import com.pheeeew.core.network.MutationCertainty
import com.pheeeew.core.network.NetworkFailure
import com.pheeeew.data.remote.api.CollectionApi
import com.pheeeew.data.remote.mapper.CollectionContractException
import com.pheeeew.data.remote.mapper.CollectionMapper
import com.pheeeew.domain.model.collection.CollectionCard
import com.pheeeew.domain.model.collection.CollectionCardReference
import com.pheeeew.domain.model.collection.CollectionFailure
import com.pheeeew.domain.model.collection.CollectionHistory
import com.pheeeew.domain.model.collection.CollectionHistoryQuery
import com.pheeeew.domain.model.collection.CollectionResult
import com.pheeeew.domain.model.collection.CollectionToday
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.repository.CollectionRepository
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

@Inject
internal class CollectionRepositoryImpl(
    private val api: CollectionApi,
) : CollectionRepository {
    override suspend fun findToday(): CollectionResult<CollectionToday> =
        mapResult({ api.findToday() }, CollectionMapper::toDomain)

    override suspend fun claim(
        date: String,
        state: EmotionState,
    ): CollectionResult<CollectionCardReference> =
        mapResult({ api.claim(date, state.name) }, CollectionMapper::toDomain)

    override suspend fun findHistory(query: CollectionHistoryQuery): CollectionResult<CollectionHistory> =
        mapResult({ api.findHistory(query) }, CollectionMapper::toDomain)

    override suspend fun findCard(cardId: String): CollectionResult<CollectionCard> =
        mapResult({ api.findCard(cardId) }, CollectionMapper::toDomain)

    private suspend fun <Dto : Any, Domain : Any> mapResult(
        response: suspend () -> ApiResult<Dto>,
        map: (Dto) -> Domain,
    ): CollectionResult<Domain> =
        try {
            when (val result = response()) {
                is ApiResult.Success -> {
                    try {
                        CollectionResult.Success(map(result.value))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: CollectionContractException) {
                        CollectionResult.Failure(CollectionFailure(null, "COLLECTION-CONTRACT", null))
                    }
                }

                is ApiResult.Failure -> {
                    CollectionResult.Failure(result.reason.toCollectionFailure())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: CollectionContractException) {
            CollectionResult.Failure(CollectionFailure(null, "COLLECTION-CONTRACT", null))
        }

    private fun NetworkFailure.toCollectionFailure(): CollectionFailure =
        when (this) {
            is NetworkFailure.HttpStatus -> {
                CollectionFailure(
                    statusCode = statusCode,
                    code = error?.code,
                    message = error?.message,
                    isOutcomeUnknown = mutationCertainty == MutationCertainty.UNKNOWN,
                )
            }

            is NetworkFailure.Transport -> {
                CollectionFailure(
                    statusCode = null,
                    code = "NETWORK",
                    message = null,
                    isOutcomeUnknown = mutationCertainty == MutationCertainty.UNKNOWN,
                )
            }

            is NetworkFailure.Unexpected -> {
                CollectionFailure(
                    statusCode = null,
                    code = "NETWORK",
                    message = null,
                    isOutcomeUnknown = mutationCertainty == MutationCertainty.UNKNOWN,
                )
            }

            is NetworkFailure.Contract -> {
                CollectionFailure(
                    statusCode = null,
                    code = "CONTRACT",
                    message = null,
                    isOutcomeUnknown = mutationCertainty == MutationCertainty.UNKNOWN,
                )
            }

            is NetworkFailure.SessionUnavailable,
            is NetworkFailure.SessionProviderFailed,
            -> {
                CollectionFailure(statusCode = 401, code = "AUTH-001", message = null)
            }
        }
}
