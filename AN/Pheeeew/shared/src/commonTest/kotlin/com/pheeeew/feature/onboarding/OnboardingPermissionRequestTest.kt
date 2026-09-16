@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.onboarding

import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.core.permission.PermissionSettingsTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                    requestMicrophonePermission = {
                        requests += "microphone"
                        true
                    },
                )

            assertEquals(listOf("location", "microphone"), requests)
            assertTrue(result.allPermissionsGranted)
            assertNull(result.deniedPermissions)
        }

    @Test
    fun `위치 권한이 거부되어도 마이크 권한을 이어서 요청한다`() =
        runTest {
            val requests = mutableListOf<String>()

            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = {
                        requests += "location"
                        LocationPermissionStatus.Denied
                    },
                    requestMicrophonePermission = {
                        requests += "microphone"
                        true
                    },
                )

            assertEquals(listOf("location", "microphone"), requests)
            assertFalse(result.allPermissionsGranted)
            assertEquals(PermissionSettingsTarget.Location, result.deniedPermissions)
        }

    @Test
    fun `마이크 권한만 거부되면 마이크 안내 대상을 반환한다`() =
        runTest {
            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = { LocationPermissionStatus.Granted },
                    requestMicrophonePermission = { false },
                )

            assertEquals(PermissionSettingsTarget.Microphone, result.deniedPermissions)
        }

    @Test
    fun `위치와 마이크 권한이 모두 거부되면 통합 안내 대상을 반환한다`() =
        runTest {
            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = { LocationPermissionStatus.PermanentlyDenied },
                    requestMicrophonePermission = { false },
                )

            assertEquals(PermissionSettingsTarget.LocationAndMicrophone, result.deniedPermissions)
        }

    @Test
    fun `위치 권한 요청 오류도 마이크 요청을 막지 않는다`() =
        runTest {
            var microphoneRequested = false
            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = { error("location request failed") },
                    requestMicrophonePermission = {
                        microphoneRequested = true
                        true
                    },
                )

            assertTrue(microphoneRequested)
            assertEquals(PermissionSettingsTarget.Location, result.deniedPermissions)
        }

    @Test
    fun `마이크 권한 요청 오류는 거부 상태로 처리한다`() =
        runTest {
            val result =
                requestOnboardingPermissions(
                    requestLocationPermission = { LocationPermissionStatus.Granted },
                    requestMicrophonePermission = { error("microphone request failed") },
                )

            assertEquals(PermissionSettingsTarget.Microphone, result.deniedPermissions)
        }
}
