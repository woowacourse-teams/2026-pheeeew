package com.pheeeew.feature.map

import com.pheeeew.domain.model.geo.Coordinate

const val MAX_MEMO_LENGTH = 50

data class PendingSighDraft(
    val requestId: String,
    val coordinate: Coordinate,
)

object MemoPolicy {
    fun normalize(rawMemo: String): String? {
        val memo = rawMemo.trim()
        require(memo.length <= MAX_MEMO_LENGTH) { "메모는 ${MAX_MEMO_LENGTH}자 이내로 작성해주세요." }
        return memo.ifBlank { null }
    }
}
