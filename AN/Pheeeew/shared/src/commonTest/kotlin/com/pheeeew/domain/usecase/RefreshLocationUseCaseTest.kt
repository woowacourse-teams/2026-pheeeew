package com.pheeeew.domain.usecase

import com.pheeeew.core.permission.LocationPermissionController
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.domain.model.LocationState
import com.pheeeew.domain.repository.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshLocationUseCaseTest {
    @Test
    fun `앱 시작 시 권한 상태를 확인하지만 시스템 권한을 요청하지 않는다`() =
        runTest {
            val permission = FakePermissionController()
            val repository = FakeLocationRepository()

            RefreshLocationUseCase(permission, repository)(requestPermission = false)

            assertEquals(1, permission.statusChecks)
            assertEquals(0, permission.requests)
            assertEquals(1, repository.refreshes)
        }

    @Test
    fun `현재 위치 버튼에서는 권한이 없을 때 시스템 권한을 요청한다`() =
        runTest {
            val permission = FakePermissionController()
            val repository = FakeLocationRepository()

            RefreshLocationUseCase(permission, repository)(requestPermission = true)

            assertEquals(1, permission.requests)
            assertEquals(1, repository.refreshes)
        }

    private class FakePermissionController : LocationPermissionController {
        var statusChecks = 0
        var requests = 0

        override suspend fun currentStatus(): LocationPermissionStatus {
            statusChecks++
            return LocationPermissionStatus.Denied
        }

        override suspend fun requestPermission(): LocationPermissionStatus {
            requests++
            return LocationPermissionStatus.Granted
        }
    }

    private class FakeLocationRepository : LocationRepository {
        override val state = MutableStateFlow<LocationState>(LocationState.Loading)
        var refreshes = 0

        override suspend fun refresh() {
            refreshes++
        }
    }
}
