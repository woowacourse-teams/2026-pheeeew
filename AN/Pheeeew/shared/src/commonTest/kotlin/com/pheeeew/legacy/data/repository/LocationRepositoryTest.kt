package com.pheeeew.legacy.data.repository

import com.pheeeew.legacy.core.location.LocationFreshnessPolicy
import com.pheeeew.legacy.core.location.PlatformLocationProvider
import com.pheeeew.legacy.core.location.PlatformLocationResult
import com.pheeeew.legacy.core.permission.LocationPermissionController
import com.pheeeew.legacy.core.permission.LocationPermissionStatus
import com.pheeeew.legacy.domain.model.location.CurrentLocation
import com.pheeeew.legacy.domain.model.location.LocationError
import com.pheeeew.legacy.domain.model.location.LocationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocationRepositoryTest {
    @Test
    fun initialStateIsLoading() {
        val repository = createRepository()

        assertSame(LocationState.Loading, repository.locationState.value)
    }

    @Test
    fun grantedPermissionAndFreshLocationProduceAvailable() =
        runTest {
            val location = currentLocation(capturedAtMillis = NOW_MILLIS - 1_000L)
            val provider = FakePlatformLocationProvider(PlatformLocationResult.Success(location))
            val repository = createRepository(locationProvider = provider)

            repository.refreshCurrentLocation()

            val state = assertIs<LocationState.Available>(repository.locationState.value)
            assertSame(location, state.location)
            assertEquals(1, provider.getCurrentLocationCalls)
        }

    @Test
    fun deniedPermissionProducesPermissionDeniedWithoutReadingGps() =
        runTest {
            val permissionController =
                FakeLocationPermissionController(LocationPermissionStatus.Denied)
            val provider = FakePlatformLocationProvider(PlatformLocationResult.GpsUnavailable)
            val repository =
                createRepository(
                    permissionController = permissionController,
                    locationProvider = provider,
                )

            repository.refreshCurrentLocation()

            assertEquals(
                LocationState.Unavailable(LocationError.PermissionDenied),
                repository.locationState.value,
            )
            assertEquals(0, provider.getCurrentLocationCalls)
            assertEquals(0, permissionController.requestPermissionCalls)
        }

    @Test
    fun permanentlyDeniedPermissionAlsoProducesPermissionDenied() =
        runTest {
            val repository =
                createRepository(
                    permissionController =
                        FakeLocationPermissionController(
                            LocationPermissionStatus.PermanentlyDenied,
                        ),
                )

            repository.refreshCurrentLocation()

            assertEquals(
                LocationState.Unavailable(LocationError.PermissionDenied),
                repository.locationState.value,
            )
        }

    @Test
    fun gpsUnavailableProducesGpsUnavailable() =
        runTest {
            val repository =
                createRepository(
                    locationProvider =
                        FakePlatformLocationProvider(PlatformLocationResult.GpsUnavailable),
                )

            repository.refreshCurrentLocation()

            assertEquals(
                LocationState.Unavailable(LocationError.GpsUnavailable),
                repository.locationState.value,
            )
        }

    @Test
    fun providerThatDoesNotCompleteProducesLocationTimeout() =
        runTest {
            val provider =
                FakePlatformLocationProvider {
                    delay(Long.MAX_VALUE)
                    PlatformLocationResult.GpsUnavailable
                }
            val repository =
                createRepository(
                    locationProvider = provider,
                    locationTimeoutMillis = 1_000L,
                )

            repository.refreshCurrentLocation()

            assertEquals(
                LocationState.Unavailable(LocationError.LocationTimeout),
                repository.locationState.value,
            )
        }

    @Test
    fun staleLocationIsNeverExposedAsCurrentLocation() =
        runTest {
            val staleLocation = currentLocation(capturedAtMillis = NOW_MILLIS - 60_001L)
            val repository =
                createRepository(
                    locationProvider =
                        FakePlatformLocationProvider(
                            PlatformLocationResult.Success(staleLocation),
                        ),
                    freshnessPolicy = LocationFreshnessPolicy(maximumAgeMillis = 60_000L),
                )

            repository.refreshCurrentLocation()

            assertEquals(
                LocationState.Unavailable(LocationError.LocationTimeout),
                repository.locationState.value,
            )
        }

    @Test
    fun unexpectedProviderFailureProducesGpsUnavailable() =
        runTest {
            val repository =
                createRepository(
                    locationProvider =
                        FakePlatformLocationProvider {
                            error("platform failure")
                        },
                )

            repository.refreshCurrentLocation()

            assertEquals(
                LocationState.Unavailable(LocationError.GpsUnavailable),
                repository.locationState.value,
            )
        }

    @Test
    fun everyRefreshPublishesLoadingBeforeItsResult() =
        runTest {
            var request: suspend () -> PlatformLocationResult = {
                PlatformLocationResult.Success(currentLocation(capturedAtMillis = NOW_MILLIS))
            }
            val provider = FakePlatformLocationProvider { request() }
            val repository = createRepository(locationProvider = provider)
            repository.refreshCurrentLocation()
            assertIs<LocationState.Available>(repository.locationState.value)

            val nextResult = CompletableDeferred<PlatformLocationResult>()
            request = { nextResult.await() }

            val refreshJob = launch { repository.refreshCurrentLocation() }
            runCurrent()

            assertSame(LocationState.Loading, repository.locationState.value)

            nextResult.complete(
                PlatformLocationResult.Success(currentLocation(capturedAtMillis = NOW_MILLIS)),
            )
            advanceUntilIdle()

            assertIs<LocationState.Available>(repository.locationState.value)
            assertTrue(refreshJob.isCompleted)
        }

    @Test
    fun currentLocationStringNeverContainsExactCoordinates() {
        val text = currentLocation(capturedAtMillis = NOW_MILLIS).toString()

        assertFalse(text.contains("37.5505"))
        assertFalse(text.contains("127.0373"))
        assertEquals("CurrentLocation([redacted])", text)
    }

    private fun createRepository(
        permissionController: LocationPermissionController =
            FakeLocationPermissionController(LocationPermissionStatus.Granted),
        locationProvider: PlatformLocationProvider =
            FakePlatformLocationProvider(
                PlatformLocationResult.Success(currentLocation(capturedAtMillis = NOW_MILLIS)),
            ),
        freshnessPolicy: LocationFreshnessPolicy =
            LocationFreshnessPolicy(maximumAgeMillis = 60_000L),
        locationTimeoutMillis: Long = 10_000L,
    ): LocationRepositoryImpl =
        LocationRepositoryImpl(
            permissionController = permissionController,
            locationProvider = locationProvider,
            freshnessPolicy = freshnessPolicy,
            locationTimeoutMillis = locationTimeoutMillis,
            currentTimeMillis = { NOW_MILLIS },
        )

    private class FakeLocationPermissionController(
        var status: LocationPermissionStatus,
    ) : LocationPermissionController {
        var requestPermissionCalls = 0

        override suspend fun currentStatus(): LocationPermissionStatus = status

        override suspend fun requestPermission(): LocationPermissionStatus {
            requestPermissionCalls += 1
            return status
        }
    }

    private class FakePlatformLocationProvider(
        private val result: suspend () -> PlatformLocationResult,
    ) : PlatformLocationProvider {
        constructor(result: PlatformLocationResult) : this({ result })

        var getCurrentLocationCalls = 0

        override suspend fun getCurrentLocation(): PlatformLocationResult {
            getCurrentLocationCalls += 1
            return result()
        }
    }

    companion object {
        private const val NOW_MILLIS = 1_000_000L

        private fun currentLocation(capturedAtMillis: Long) =
            CurrentLocation(
                latitude = 37.5505,
                longitude = 127.0373,
                accuracyMeters = 8.5f,
                capturedAtMillis = capturedAtMillis,
            )
    }
}

class LocationFreshnessPolicyTest {
    private val policy = LocationFreshnessPolicy(maximumAgeMillis = 10_000L)

    @Test
    fun acceptsLocationAtMaximumAgeBoundary() {
        assertTrue(
            policy.isFresh(
                location = location(capturedAtMillis = 10_000L),
                currentTimeMillis = 20_000L,
            ),
        )
    }

    @Test
    fun rejectsLocationOlderThanMaximumAge() {
        assertFalse(
            policy.isFresh(
                location = location(capturedAtMillis = 9_999L),
                currentTimeMillis = 20_000L,
            ),
        )
    }

    @Test
    fun rejectsLocationCapturedInTheFuture() {
        assertFalse(
            policy.isFresh(
                location = location(capturedAtMillis = 20_001L),
                currentTimeMillis = 20_000L,
            ),
        )
    }

    private fun location(capturedAtMillis: Long) =
        CurrentLocation(
            latitude = 37.0,
            longitude = 127.0,
            accuracyMeters = 5f,
            capturedAtMillis = capturedAtMillis,
        )
}
