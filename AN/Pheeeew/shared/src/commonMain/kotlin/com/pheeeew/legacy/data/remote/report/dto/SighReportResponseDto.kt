package com.pheeeew.legacy.data.remote.report.dto

import kotlinx.serialization.Serializable

@Serializable
data class SighReportResponseDto(
    val id: Long,
    val sighId: Long,
    val reason: String,
    val createdAt: String,
)
