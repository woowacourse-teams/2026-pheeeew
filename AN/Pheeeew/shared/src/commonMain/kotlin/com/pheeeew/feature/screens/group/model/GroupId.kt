package com.pheeeew.feature.screens.group.model

import kotlin.jvm.JvmInline

@JvmInline
value class GroupId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "그룹 ID는 비어 있을 수 없습니다." }
    }
}
