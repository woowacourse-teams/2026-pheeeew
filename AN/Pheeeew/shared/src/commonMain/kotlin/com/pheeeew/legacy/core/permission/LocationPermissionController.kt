package com.pheeeew.legacy.core.permission

/** 플랫폼의 현재 위치 권한을 조회하고 요청하는 계약입니다. */
interface LocationPermissionController {
    suspend fun currentStatus(): LocationPermissionStatus

    suspend fun requestPermission(): LocationPermissionStatus
}
