@file:Suppress("NonAsciiCharacters")

package com.pheeeew.core.permission

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class IosLocationPermissionControllerTest {
    @Test
    fun authorizedWhenInUseMapsToGranted() {
        assertEquals(
            LocationPermissionStatus.Granted,
            kCLAuthorizationStatusAuthorizedWhenInUse.toCommonStatus(locationServicesEnabled = true),
        )
    }

    @Test
    fun deniedMapsToPermanentlyDenied() {
        assertEquals(
            LocationPermissionStatus.PermanentlyDenied,
            kCLAuthorizationStatusDenied.toCommonStatus(locationServicesEnabled = true),
        )
    }

    @Test
    fun disabledLocationServicesMapsToServicesDisabled() {
        assertEquals(
            LocationPermissionStatus.ServicesDisabled,
            kCLAuthorizationStatusDenied.toCommonStatus(locationServicesEnabled = false),
        )
    }

    @Test
    fun notDeterminedMapsToDenied() {
        assertEquals(
            LocationPermissionStatus.Denied,
            kCLAuthorizationStatusNotDetermined.toCommonStatus(locationServicesEnabled = true),
        )
    }

    @Test
    fun `위치 서비스가 꺼져 있으면 ServicesDisabled를 변환한다`() {
        val result =
            kCLAuthorizationStatusAuthorizedWhenInUse
                .toCommonStatus(locationServicesEnabled = false)

        assertEquals(
            LocationPermissionStatus.ServicesDisabled,
            result,
        )
    }
}
