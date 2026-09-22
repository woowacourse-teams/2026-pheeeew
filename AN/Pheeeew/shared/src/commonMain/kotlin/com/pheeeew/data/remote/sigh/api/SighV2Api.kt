package com.pheeeew.data.remote.sigh.api

import com.pheeeew.data.remote.sigh.dto.SighCreateV2RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighLikeResponseDto
import com.pheeeew.data.remote.sigh.dto.SighPageResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV2PropertiesDto
import com.pheeeew.domain.model.sigh.SighBounds

interface SighV2Api {
    suspend fun getFirstPage(bounds: SighBounds): SighPageResponseDto

    suspend fun getNextPage(cursor: String): SighPageResponseDto

    suspend fun getById(id: Long): SighFeatureDto<SighV2PropertiesDto>

    suspend fun create(request: SighCreateV2RequestDto): SighFeatureDto<SighV2PropertiesDto>

    suspend fun updateLike(
        id: Long,
        liked: Boolean,
    ): SighLikeResponseDto = error("좋아요 API가 구현되지 않았습니다.")
}
