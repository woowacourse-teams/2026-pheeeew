package com.pheeeew.legacy.data.remote.common.dto

import kotlinx.serialization.Serializable

/**
 * API 오류 응답 DTO
 *
 * [code]는 서버 오류 코드이고,
 * [message]는 사용자 또는 로그에 사용할 오류 설명입니다.
 */
@Serializable
data class ErrorResponseDto(
    val code: String? = null,
    val message: String? = null,
)
