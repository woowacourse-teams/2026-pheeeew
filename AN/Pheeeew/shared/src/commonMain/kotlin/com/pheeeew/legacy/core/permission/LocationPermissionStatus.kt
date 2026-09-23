package com.pheeeew.legacy.core.permission

enum class LocationPermissionStatus {
    /** 위치 권한이 허용된 상태 */
    Granted,

    /** 사용자가 위치 권한을 거부한 상태 */
    Denied,

    /** 앱에서 권한 팝업을 다시 표시할 수 없어 설정에서 변경해야 하는 상태 */
    PermanentlyDenied,

    /** 기기의 시스템 위치 서비스가 비활성화된 상태 */
    ServicesDisabled,
}
