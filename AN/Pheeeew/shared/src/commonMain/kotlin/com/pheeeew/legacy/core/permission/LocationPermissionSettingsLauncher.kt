package com.pheeeew.legacy.core.permission

/** 운영체제 설정 앱에서 설정 화면을 엽니다. */
interface LocationPermissionSettingsLauncher {
    val canOpenLocationSettings: Boolean
        get() = true

    val locationServicesInstruction: String
        get() = "설정에서 위치 서비스를 켜주세요."

    val locationServicesDialogInstruction: String
        get() = locationServicesInstruction

    suspend fun openAppSettings(): Boolean

    suspend fun openLocationSettings(): Boolean
}
