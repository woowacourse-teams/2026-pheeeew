package com.pheeeew.legacy.feature.setting

import com.pheeeew.legacy.core.permission.LocationPermissionController
import com.pheeeew.legacy.core.permission.LocationPermissionSettingsLauncher
import com.pheeeew.legacy.core.permission.LocationPermissionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationPermissionSettingsHandlerTest {
    @Test
    fun `requestable permission opens the system permission prompt`() =
        runTest {
            val permissionController = FakePermissionController(LocationPermissionStatus.Denied)
            val settingsLauncher = FakeSettingsLauncher()

            handleLocationPermissionSettingsClick(permissionController, settingsLauncher)

            assertEquals(1, permissionController.requestCalls)
            assertEquals(0, settingsLauncher.openCalls)
        }

    @Test
    fun `granted permission opens app settings so the user can change it`() =
        runTest {
            val permissionController = FakePermissionController(LocationPermissionStatus.Granted)
            val settingsLauncher = FakeSettingsLauncher()

            handleLocationPermissionSettingsClick(permissionController, settingsLauncher)

            assertEquals(0, permissionController.requestCalls)
            assertEquals(1, settingsLauncher.openCalls)
        }

    @Test
    fun `permanently denied permission opens app settings`() =
        runTest {
            val permissionController = FakePermissionController(LocationPermissionStatus.PermanentlyDenied)
            val settingsLauncher = FakeSettingsLauncher()

            handleLocationPermissionSettingsClick(permissionController, settingsLauncher)

            assertEquals(0, permissionController.requestCalls)
            assertEquals(1, settingsLauncher.openCalls)
        }

    @Test
    fun `disabled location services opens location settings`() =
        runTest {
            val permissionController = FakePermissionController(LocationPermissionStatus.ServicesDisabled)
            val settingsLauncher = FakeSettingsLauncher()

            handleLocationPermissionSettingsClick(permissionController, settingsLauncher)

            assertEquals(0, settingsLauncher.openCalls)
            assertEquals(1, settingsLauncher.openLocationSettingsCalls)
        }

    private class FakePermissionController(
        private val status: LocationPermissionStatus,
    ) : LocationPermissionController {
        var requestCalls = 0

        override suspend fun currentStatus(): LocationPermissionStatus = status

        override suspend fun requestPermission(): LocationPermissionStatus {
            requestCalls += 1
            return status
        }
    }

    private class FakeSettingsLauncher : LocationPermissionSettingsLauncher {
        var openCalls = 0
        var openLocationSettingsCalls = 0

        override suspend fun openAppSettings(): Boolean {
            openCalls += 1
            return true
        }

        override suspend fun openLocationSettings(): Boolean {
            openLocationSettingsCalls += 1
            return true
        }
    }
}
