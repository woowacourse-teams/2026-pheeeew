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
    fun `권한 요청 옵션이 꺼져 있으면 상태만 확인한다`() =
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

    @Test
    fun `영구 거절 상태에서도 시스템 요청을 먼저 시도한 뒤 위치 상태를 갱신한다`() =
        runTest {
            val events = mutableListOf<String>()
            val permission = FakePermissionController(LocationPermissionStatus.PermanentlyDenied, events)
            val repository = FakeLocationRepository(events)

            RefreshLocationUseCase(permission, repository)(requestPermission = true)

            assertEquals(listOf("request", "refresh"), events)
        }

    @Test
    fun `이미 허용된 권한은 시스템 요청 없이 위치를 갱신한다`() =
        runTest {
            val permission = FakePermissionController(LocationPermissionStatus.Granted)
            val repository = FakeLocationRepository()

            RefreshLocationUseCase(permission, repository)(requestPermission = true)

            assertEquals(0, permission.requests)
            assertEquals(1, repository.refreshes)
        }

    private class FakePermissionController(
        private val status: LocationPermissionStatus = LocationPermissionStatus.Denied,
        private val events: MutableList<String> = mutableListOf(),
    ) : LocationPermissionController {
        var statusChecks = 0
        var requests = 0

        override suspend fun currentStatus(): LocationPermissionStatus {
            statusChecks++
            return status
        }

        override suspend fun requestPermission(): LocationPermissionStatus {
            requests++
            events += "request"
            return LocationPermissionStatus.Granted
        }
    }

    private class FakeLocationRepository(
        private val events: MutableList<String> = mutableListOf(),
    ) : LocationRepository {
        override val state = MutableStateFlow<LocationState>(LocationState.Loading)
        var refreshes = 0

        override suspend fun refresh() {
            refreshes++
            events += "refresh"
        }
    }
}
