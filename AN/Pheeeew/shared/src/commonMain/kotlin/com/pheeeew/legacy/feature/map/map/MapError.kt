package com.pheeeew.legacy.feature.map.map

/**
 * 지도 컴포넌트가 상위 UI에 전달하는 오류 유형입니다.
 *
 * 오류에는 좌표나 URL 응답 본문을 넣지 않아 사용자의 정확한 위치가
 * 로그 또는 오류 리포트로 전파되지 않게 합니다.
 */
enum class MapError {
    RendererUnavailable,
    StyleLoadFailed,
}
