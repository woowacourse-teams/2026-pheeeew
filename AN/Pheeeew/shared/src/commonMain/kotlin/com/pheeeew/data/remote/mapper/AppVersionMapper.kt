package com.pheeeew.data.remote.mapper

import com.pheeeew.data.remote.dto.AppVersionResponseDto
import com.pheeeew.domain.model.version.AppVersionPolicy

fun AppVersionResponseDto.toPolicy(): AppVersionPolicy = AppVersionPolicy(minSupportedVersion, latestVersion, storeUrl)
