@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.onboarding

import com.pheeeew.core.permission.LocationPermissionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingPermissionRequestTest {
    @Test
    fun `위치 권한과 마이크 권한을 순서대로 요청한다`() =
        runTest {
            val requests = mutableListOf<String>()

            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = {
                        requests += "location"
                        LocationPermissionStatus.Granted
                    },
                    requestMicrophonePermission = { requests += "microphone" },
                )

            assertEquals(listOf("location", "microphone"), requests)
            assertEquals(LocationPermissionStatus.Granted, result)
        }

    @Test
    fun `위치 권한이 거부되면 안내 전에는 마이크 권한을 요청하지 않는다`() =
        runTest {
            val requests = mutableListOf<String>()

            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = {
                        requests += "location"
                        LocationPermissionStatus.Denied
                    },
                    requestMicrophonePermission = { requests += "microphone" },
                )

            assertEquals(listOf("location"), requests)
            assertEquals(LocationPermissionStatus.Denied, result)
        }

    @Test
    fun `위치 권한 요청 오류는 거부 상태로 처리한다`() =
        runTest {
            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = { error("location request failed") },
                    requestMicrophonePermission = { error("must not be called") },
                )

            assertEquals(LocationPermissionStatus.Denied, result)
        }
}
