package com.pheeeew.core.permission

/** 플랫폼의 현재 위치 권한 상태를 확인하고 권한을 요청합니다. */
interface LocationPermissionController {
    suspend fun currentStatus(): LocationPermissionStatus

    suspend fun requestPermission(): LocationPermissionStatus
}
