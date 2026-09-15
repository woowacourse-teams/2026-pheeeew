package com.pheeeew.data.remote.report.dto

import kotlinx.serialization.Serializable

@Serializable
data class SighReportCreateRequestDto(
    val sighId: Long,
    val deviceId: String,
    val reason: String,
)
