package com.pheeeew.domain.model.sigh

data class SighPage(
    val items: List<Sigh>,
    val nextCursor: String?,
) {
    init {
        require(nextCursor == null || nextCursor.isNotBlank()) {
            "다음 페이지 커서는 비어 있을 수 없습니다."
        }
    }

    val hasNext: Boolean
        get() = nextCursor != null
}
