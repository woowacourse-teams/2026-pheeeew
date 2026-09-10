package com.pheeeew.data.remote.sigh.dto

import com.pheeeew.domain.model.sigh.SighPage
import kotlinx.serialization.Serializable

@Serializable
data class SighPageResponseDto(
    val items: List<SighFeatureDto<SighV2PropertiesDto>>,
    val hasNext: Boolean,
    val nextCursor: String? = null,
)

fun SighPageResponseDto.toSighPage(): SighPage {
    require(hasNext == (nextCursor != null)) {
        "다음 페이지 여부와 커서가 일치하지 않습니다."
    }

    return SighPage(
        items = items.map { it.toSigh() },
        nextCursor = nextCursor,
    )
}
