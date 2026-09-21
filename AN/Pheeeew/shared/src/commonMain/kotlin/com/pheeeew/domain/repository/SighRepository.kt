package com.pheeeew.domain.repository

import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighLikeState
import com.pheeeew.domain.model.sigh.SighPage
import com.pheeeew.domain.model.sigh.SighPin

interface SighRepository {
    suspend fun getMapSighs(bounds: SighBounds): List<SighPin>

    suspend fun getFirstPage(bounds: SighBounds): SighPage

    suspend fun getNextPage(cursor: String): SighPage

    suspend fun getById(id: Long): Sigh

    suspend fun create(command: CreateSighCommand): Sigh

    suspend fun updateLike(
        id: Long,
        liked: Boolean,
    ): SighLikeState = error("좋아요 API가 구현되지 않았습니다.")
}
