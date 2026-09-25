package com.pheeeew.feature.screens.group.join

/** 코드 정책이 확정되기 전까지 공백 제거와 대소문자 정규화만 적용합니다. */
object GroupCodeRules {
    fun normalize(value: String): String = value.trim().uppercase()

    fun isValid(value: String): Boolean = normalize(value).isNotEmpty()
}
