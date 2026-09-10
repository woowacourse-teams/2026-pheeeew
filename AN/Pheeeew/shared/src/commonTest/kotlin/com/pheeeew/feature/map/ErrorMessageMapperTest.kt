@file:Suppress("NonAsciiCharacters")

package com.pheeeew.feature.map

import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.location.LocationError
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.feature.map.map.MapError
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMessageMapperTest {
    @Test
    fun `네트워크 예외는 사용자 안내 문구로 변환한다`() {
        val exception = ApiException.Network(code = "NETWORK_ERROR", message = "연결 실패")

        assertEquals(
            "네트워크 연결이 불안정해요. 인터넷 연결을 확인한 후 다시 시도해 주세요.",
            exception.toUserMessage(),
        )
    }

    @Test
    fun `네트워크 외 예외는 서버 메시지를 유지한다`() {
        val exception = ApiException.Unknown(code = "UNKNOWN_ERROR", message = "저장에 실패했습니다.")

        assertEquals("저장에 실패했습니다.", exception.toUserMessage())
    }

    @Test
    fun `지도 렌더러 오류는 사용자 안내 문구로 변환한다`() {
        assertEquals(
            "지도를 불러오지 못했어요. 잠시 후 재시도해 주세요.",
            MapError.RendererUnavailable.toUserMessage(),
        )
    }

    @Test
    fun `지도 스타일 오류는 사용자 안내 문구로 변환한다`() {
        assertEquals(
            "지도 화면을 불러오지 못했어요. 인터넷 연결 상태를 확인해 주세요.",
            MapError.StyleLoadFailed.toUserMessage(),
        )
    }

    @Test
    fun `배너 메시지는 등록 지도 조회 위치 오류 순으로 우선한다`() {
        val locationError =
            MapUiState(
                location = MapLocationUiState(state = LocationState.Unavailable(LocationError.GpsUnavailable)),
            )
        assertEquals("현재 위치를 확인할 수 없습니다.", locationError.toBannerMessage())

        val refreshError =
            locationError.copy(
                errors = MapErrorUiState(refreshMessage = "조회 오류"),
            )
        assertEquals("조회 오류", refreshError.toBannerMessage())

        val renderError =
            refreshError.copy(
                errors = refreshError.errors.copy(renderMessage = "지도 오류"),
            )
        assertEquals("지도 오류", renderError.toBannerMessage())

        val releaseError =
            renderError.copy(
                sighRelease = SighReleaseState.Error(message = "등록 오류", canRetry = true),
            )
        assertEquals("등록 오류", releaseError.toBannerMessage())
    }
}
