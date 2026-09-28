package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent
import com.pheeeew.domain.model.emotion.EmotionRegistrationResult
import com.pheeeew.domain.repository.EmotionRegistrationRepository
import com.pheeeew.domain.repository.group.GroupStampListLoadResult
import com.pheeeew.domain.repository.group.GroupStampListRepository
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.record.location.RECORD_RADIUS_METERS
import com.pheeeew.feature.screens.map.record.location.constrainToRecordRadius
import com.pheeeew.feature.screens.map.record.location.destination
import com.pheeeew.feature.screens.map.record.location.distance
import com.pheeeew.feature.screens.map.record.location.recordCameraBounds
import com.pheeeew.feature.screens.map.record.sheet.RecordFlowStepUiModel
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecordLocationTest {
    private val origin = GeoCoordinate(37.5665, 126.9780)
    private val unusedGroups = GroupStampListRepository { GroupStampListLoadResult.Loaded(emptyList()) }

    @Test
    fun clampsOutsideCoordinatesTo500Meters() {
        for (angle in listOf(0.0, 1.0, -2.0, 3.0)) {
            val outside = destination(origin, 900.0, angle)
            val constrained = constrainToRecordRadius(origin, outside)
            assertTrue(distance(origin, constrained) <= 500.0)
            assertTrue(abs(distance(origin, constrained) - 500.0) < 0.01)
            assertTrue(IsWithinEmotionRecordRadiusUseCase()(origin, constrained))
        }
        val inside = destination(origin, 400.0, 1.0)
        assertEquals(inside, constrainToRecordRadius(origin, inside))
    }

    @Test
    fun cameraBoundsRejectOutsideCoordinatesWithoutCorrectingThem() {
        val bounds = recordCameraBounds(origin)
        assertTrue(bounds.contains(origin))
        for (angle in listOf(0.0, 1.5708, 3.1416, -1.5708)) {
            assertTrue(bounds.contains(destination(origin, 499.0, angle)))
            assertFalse(bounds.contains(destination(origin, 501.0, angle)))
        }
        assertTrue(bounds.contains(GeoCoordinate(bounds.north, bounds.east)))
        assertFalse(bounds.contains(GeoCoordinate(bounds.north + 0.000001, bounds.east)))
        assertFalse(bounds.contains(GeoCoordinate(Double.NaN, origin.longitude)))
        assertFalse(bounds.contains(GeoCoordinate(origin.latitude, Double.NaN)))
        val coordinate = destination(origin, 550.0, 1.0)
        assertTrue(bounds.contains(coordinate))
        assertTrue(distance(origin, constrainToRecordRadius(origin, coordinate)) < RECORD_RADIUS_METERS)
    }

    @Test
    fun confirmsMemoThroughRepository() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository = FakeRegistrationRepository()
                val model = initializedModel(repository)
                advanceUntilIdle()
                model.onNext(CurrentLocation(origin.latitude, origin.longitude, 1f, 0L), null)
                model.onMemoChange("기록")
                val outside = destination(origin, 800.0, 1.0)
                model.onLocationSelected(outside.latitude, outside.longitude)
                model.onConfirmLocation()
                assertTrue(model.uiModel.value.isSubmitting)
                advanceUntilIdle()
                assertEquals("기록", (repository.lastRegistration?.content as EmotionRegistrationContent.Memo).text)
                assertTrue(distance(origin, repository.lastRegistration!!.coordinate) <= 500.0)
                assertEquals(RecordFlowStepUiModel.Closed, model.uiModel.value.step)
                assertEquals("선택한 위치에 감정을 남겼어", model.notice.value?.message)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun recordingWithoutUploadedAudioKeepsFlowOpen() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository = FakeRegistrationRepository()
                val model =
                    MapRecordViewModel(
                        IsWithinEmotionRecordRadiusUseCase(),
                        repository,
                        unusedGroups,
                        InMemoryLastRecordedGroupRepository(),
                    )
                model.open(EmotionTypeUiModel.FRUSTRATED)
                advanceUntilIdle()
                model.onInputModeChange(RecordInputModeUiModel.Recording)
                model.onNext(null, "/tmp/voice.m4a")
                model.onConfirmLocation()
                assertFalse(model.uiModel.value.isSubmitting)
                model.onOriginLocationAvailable(CurrentLocation(origin.latitude, origin.longitude, 1f, 0L))
                model.onLocationSelected(Double.NaN, 0.0)
                model.onLocationSelected(91.0, 0.0)
                assertEquals(origin, model.uiModel.value.selectedCoordinate)
                model.onConfirmLocation()
                advanceUntilIdle()
                assertEquals(RecordFlowStepUiModel.LocationSelection, model.uiModel.value.step)
                assertTrue(model.notice.value?.isError == true)
                assertEquals(EmotionRegistrationContent.Audio("/tmp/voice.m4a"), repository.lastRegistration?.content)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun initializedModel(repository: EmotionRegistrationRepository): MapRecordViewModel =
        MapRecordViewModel(
            IsWithinEmotionRecordRadiusUseCase(),
            repository,
            unusedGroups,
            InMemoryLastRecordedGroupRepository(),
        ).also {
            it.open(EmotionTypeUiModel.FRUSTRATED)
        }

    private class FakeRegistrationRepository : EmotionRegistrationRepository {
        var lastRegistration: EmotionRegistration? = null

        override suspend fun register(registration: EmotionRegistration): EmotionRegistrationResult {
            lastRegistration = registration
            return if (registration.content is EmotionRegistrationContent.Audio) {
                EmotionRegistrationResult.AudioUnavailable
            } else {
                EmotionRegistrationResult.Success(42)
            }
        }
    }
}
