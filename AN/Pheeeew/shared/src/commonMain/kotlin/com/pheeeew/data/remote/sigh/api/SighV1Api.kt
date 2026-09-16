package com.pheeeew.data.remote.sigh.api

import com.pheeeew.data.remote.sigh.dto.SighCreateV1RequestDto
import com.pheeeew.data.remote.sigh.dto.SighFeatureDto
import com.pheeeew.data.remote.sigh.dto.SighMapResponseDto
import com.pheeeew.data.remote.sigh.dto.SighV1PropertiesDto
import com.pheeeew.domain.model.sigh.SighBounds

interface SighV1Api {
    suspend fun getSighs(bounds: SighBounds): SighMapResponseDto

    suspend fun registerSigh(request: SighCreateV1RequestDto): SighFeatureDto<SighV1PropertiesDto>
}
