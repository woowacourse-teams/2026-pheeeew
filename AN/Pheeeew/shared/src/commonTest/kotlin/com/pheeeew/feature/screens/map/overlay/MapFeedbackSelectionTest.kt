package com.pheeeew.feature.screens.map.overlay

import com.pheeeew.domain.model.LocationError
import com.pheeeew.feature.screens.map.MapUiModel
import com.pheeeew.feature.screens.map.detail.EmotionDetailLoadUiModel
import com.pheeeew.feature.screens.map.record.RecordNoticeUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapFeedbackSelectionTest {
    private val groupError = RecordNoticeUiModel("그룹 목록을 불러오지 못했어요", true, suppressWhenOffline = true)
    private val detailError = EmotionDetailLoadUiModel.Failed("감정을 불러오지 못했어요", true)

    @Test
    fun `connection notice replaces simultaneous group and detail failures`() {
        assertEquals(
            MapFeedbackSource.Connection,
            selectMapFeedback(
                MapUiModel(isOffline = true, emotionPinsError = "핀 조회 실패"),
                recording = true,
                connectionMessage = "인터넷 연결이 끊겼어요",
                notice = groupError,
                message = null,
                detailError = detailError,
            ),
        )
    }

    @Test
    fun `offline errors stay hidden after the four second connection notice expires`() {
        assertNull(
            selectMapFeedback(
                MapUiModel(isOffline = true),
                recording = true,
                connectionMessage = null,
                notice = groupError,
                message = null,
                detailError = detailError,
            ),
        )
    }

    @Test
    fun `closed recording shows only the primary offline error`() {
        assertEquals(
            MapFeedbackSource.Map,
            selectMapFeedback(MapUiModel(isOffline = true), false, null, groupError, null, detailError),
        )
    }

    @Test
    fun `online group failure takes the single slot before other failures`() {
        assertEquals(
            MapFeedbackSource.Record,
            selectMapFeedback(MapUiModel(emotionPinsError = "핀 조회 실패"), true, null, groupError, "안내", detailError),
        )
    }

    @Test
    fun `registration success is not lost if the connection drops after saving`() {
        assertEquals(
            MapFeedbackSource.Record,
            selectMapFeedback(
                MapUiModel(isOffline = true),
                false,
                null,
                RecordNoticeUiModel("감정을 남겼어요", false),
                null,
                null,
            ),
        )
    }

    @Test
    fun `recovery message takes precedence without showing old errors alongside it`() {
        assertEquals(
            MapFeedbackSource.Connection,
            selectMapFeedback(MapUiModel(), true, "인터넷이 다시 연결됐어요", groupError, null, null),
        )
    }

    @Test
    fun `transient notice yields to detail retry and then map settings action`() {
        val map = MapUiModel(locationError = LocationError.PermissionDenied)
        assertEquals(MapFeedbackSource.Message, selectMapFeedback(map, false, null, null, "안내", detailError))
        assertEquals(MapFeedbackSource.Detail, selectMapFeedback(map, false, null, null, null, detailError))
        assertEquals(MapFeedbackSource.Map, selectMapFeedback(map, false, null, null, null, null))
    }

    @Test
    fun `offline suppression does not discard unrelated recording failures`() {
        assertEquals(
            MapFeedbackSource.Record,
            selectMapFeedback(
                MapUiModel(isOffline = true),
                true,
                null,
                RecordNoticeUiModel("녹음 파일을 확인할 수 없어요", true),
                null,
                null,
            ),
        )
    }

    @Test
    fun `healthy idle screen has no snackbar`() {
        assertNull(selectMapFeedback(MapUiModel(), false, null, null, null, null))
    }
}
