package com.pheeeew.feature.screens.map.record

import com.pheeeew.domain.model.CurrentLocation
import com.pheeeew.domain.model.GeoCoordinate
import com.pheeeew.domain.usecase.IsWithinEmotionRecordRadiusUseCase
import com.pheeeew.feature.screens.map.record.location.RECORD_RADIUS_METERS
import com.pheeeew.feature.screens.map.record.location.constrainToRecordRadius
import com.pheeeew.feature.screens.map.record.location.destination
import com.pheeeew.feature.screens.map.record.location.distance
import com.pheeeew.feature.screens.map.record.location.recordCameraBounds
import com.pheeeew.feature.screens.map.record.sheet.RecordInputModeUiModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordLocationTest {
    private val origin = GeoCoordinate(37.5665, 126.9780)

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
            assertTrue(bounds.contains(destination(origin, 600.0, angle)))
            assertFalse(bounds.contains(destination(origin, 700.0, angle)))
        }
        assertTrue(bounds.contains(GeoCoordinate(bounds.north, bounds.east)))
        assertFalse(bounds.contains(GeoCoordinate(bounds.north + 0.000001, bounds.east)))
        assertFalse(bounds.contains(GeoCoordinate(Double.NaN, origin.longitude)))
        assertFalse(bounds.contains(GeoCoordinate(origin.latitude, Double.NaN)))
        val coordinate = destination(origin, 600.0, 1.0)
        assertTrue(bounds.contains(coordinate))
        assertTrue(distance(origin, constrainToRecordRadius(origin, coordinate)) < RECORD_RADIUS_METERS)
    }

    @Test
    fun confirmsMemoAndLocksSelectedCoordinate() {
        val model = initializedModel()
        model.onMemoChange("기록")
        model.onLocationSelected(destination(origin, 800.0, 1.0).latitude, destination(origin, 800.0, 1.0).longitude)
        model.onConfirmLocation()
        val confirmed = assertNotNull(model.uiModel.value.confirmedRecord)
        assertEquals("기록", confirmed.memo)
        assertEquals(EmotionTypeUiModel.Stuck, confirmed.emotion)
        assertTrue(distance(origin, confirmed.coordinate) <= 500.0)
        model.onLocationSelected(origin.latitude, origin.longitude)
        assertEquals(confirmed.coordinate, model.uiModel.value.selectedCoordinate)
        model.onConfirmLocation()
        assertEquals(confirmed, model.uiModel.value.confirmedRecord)
    }

    @Test
    fun confirmationRequiresOriginAndRejectsInvalidCoordinates() {
        val model = MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase())
        model.open(EmotionTypeUiModel.Stuck)
        model.onInputModeChange(RecordInputModeUiModel.Recording)
        model.onNext(null, "/tmp/voice.m4a")
        model.onConfirmLocation()
        assertNull(model.uiModel.value.confirmedRecord)
        model.onOriginLocationAvailable(CurrentLocation(origin.latitude, origin.longitude, 1f, 0L))
        model.onLocationSelected(Double.NaN, 0.0)
        model.onLocationSelected(91.0, 0.0)
        assertEquals(origin, model.uiModel.value.selectedCoordinate)
        model.onConfirmLocation()
        val confirmed = assertNotNull(model.uiModel.value.confirmedRecord)
        assertEquals("/tmp/voice.m4a", confirmed.recordingFilePath)
        assertNull(confirmed.memo)
    }

    private fun initializedModel(): MapRecordViewModel =
        MapRecordViewModel(IsWithinEmotionRecordRadiusUseCase()).also {
            it.open(EmotionTypeUiModel.Stuck)
            it.onNext(CurrentLocation(origin.latitude, origin.longitude, 1f, 0L), null)
        }
}
