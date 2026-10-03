# #606 클라이언트 현재 경로 조사

## 조사 기준

- 코드 기준: `69d9ec55dc446c9682d15097bc42a4dbfe01b522` (2026-10-02 조사)
- 범위: Android/iOS 지도 감정 핀 조회, 이미지 생성, MapLibre 등록, 현재 캐시
- 이 문서는 정적 코드 조사 결과다. 단계별 시간이나 기기 부하는 아직 측정하지 않았다.

## 현재 데이터·표시 경로

1. Android `NativeMap.publishViewport()`와 iOS `FoundationMapRenderer.publishViewportIfReady()`가 보이는 영역의 bounds를 공통 `MapViewModel.onViewportChanged()`에 전달한다.
2. ViewModel은 기존 스냅샷을 먼저 표시하고, 700ms 디바운스 후 `FindEmotionMapPageUseCase`를 호출한다. 이전 영역의 결과는 generation으로 표시에서 제외한다. 실행 중 요청이 있으면 최신 요청을 대기시킨다.
3. `EmotionMapApi`는 `GET /api/v1/emotions/map`에 bounds를 전송한다. 다음 페이지는 cursor만 전송한다. `MapViewModel.loadEmotionPins()`는 `hasNext`가 끝날 때까지 페이지를 순회하고 ID로 합친 뒤, 각 페이지가 올 때마다 UI 핀 목록을 갱신한다.
4. `MapScreen`은 핀 목록에서 고유 `symbolImageKey()`별로 40dp 이미지를 Compose로 래스터화한다. 감정 아이콘 또는 그룹 스탬프의 모양·색·문구가 이미지 키를 결정한다. 결과는 RGBA 바이트 배열이다.
5. Android는 RGBA를 `Bitmap`으로 변환해 MapLibre style에 등록하고 핀 목록으로 GeoJSON source를 교체한다. iOS는 RGBA를 `UIImage`로 변환해 style에 등록하고 `MLNShapeSource`를 교체한다. 두 플랫폼 모두 이미지가 준비된 핀만 source에 넣고, 현재 필요하지 않은 style 이미지는 제거한다.
6. 핀 선택은 지도 feature의 ID를 사용한다. 회전, 초점 크기, 표시 우선순위는 feature 속성과 symbol layer에서 처리한다.

## 현재 캐시와 갱신

- `EmotionMapCache`는 메모리에 최대 4개 영역을 보관하며 TTL은 180초다. groupId와 bounds를 기준으로 완성된 페이지를 재사용하고, 겹치는 영역의 스냅샷을 먼저 표시할 수 있다. 강제 새로고침은 해당 영역을 무효화한다.
- Compose `captured` 이미지 맵과 MapLibre style 이미지 집합은 현재 핀 목록의 고유 키만 유지한다. 별도의 지속 이미지 캐시는 이 경로에서 확인되지 않았다.
- 지도 타일·HTTP의 SDK 내부 캐시 상태는 코드만으로 확정할 수 없다. #606 실험에서는 각 실행의 실제 상태를 따로 기록해야 한다.
- `MapViewModel`의 지도 조회 호출은 `groupId`를 별도로 전달하지 않아 기본 `null`을 사용한다. 근처 목록의 그룹 필터는 별도 경로이므로, 서버 이미지 실험 전에 지도 표시 범위와 필터 계약을 확인해야 한다.

## 측정 전 병목 후보 (가설)

- 모든 페이지를 끝까지 받는 네트워크 왕복과 전송량. 페이지마다 합쳐진 전체 핀을 정렬하고 UI 및 지도 source를 갱신한다.
- 고유 스탬프 이미지마다 Compose draw, 픽셀 읽기, RGBA 배열 생성, 플랫폼 bitmap/UIImage 변환, MapLibre style 등록을 수행한다. 고유 이미지 수와 기기 픽셀 비율에 따라 비용이 달라진다.
- 페이지 추가나 영역 변경 때 전체 feature 컬렉션을 만들어 source를 교체한다. 핀 수가 많을 때 직렬화·지도 처리 비용이 커질 수 있다.
- 기존 영역 캐시 적중, MapLibre 타일 캐시, 이미지 키 재사용은 위 비용을 줄일 수 있다. 미적중·적중을 분리해 측정해야 한다.

## 기존 관측과 다음 확인

- `ContentLoad`는 요청 시작, 응답 준비, MapLibre 표시 완료 이벤트를 기록한다. Android/iOS renderer는 지도 idle 시 현재 load ID의 렌더된 feature를 검사한 뒤 표시 완료를 알린다. 이 값만으로 파싱·래스터화·이미지 변환·style 등록 시간이나 첫 핀 표시 시점은 분리되지 않는다.
- 다음 단계는 서버 이미지의 단위와 메타데이터 계약을 합의하고, 비교 실험을 위한 단계별 계측 지점을 정하는 것이다. 계약이 정해지기 전에는 이미지 형식이나 추가 캐시 구현을 확정하지 않는다.

## 주요 코드

- 조회·누적: `shared/src/commonMain/kotlin/com/pheeeew/feature/screens/map/MapViewModel.kt`, `data/remote/emotion/EmotionMapApi.kt`, `data/repository/EmotionMapRepositoryImpl.kt`
- 영역 캐시: `shared/src/commonMain/kotlin/com/pheeeew/data/cache/EmotionMapCache.kt`
- 이미지 생성: `shared/src/commonMain/kotlin/com/pheeeew/feature/screens/map/EmotionPinSymbolImage.kt`
- Android 등록: `shared/src/androidMain/kotlin/com/pheeeew/feature/screens/map/renderer/EmotionPinSymbolLayer.android.kt`
- iOS 등록: `iosApp/iosApp/Map/FoundationMapRenderer.swift`
- 표시 관측: `shared/src/commonMain/kotlin/com/pheeeew/feature/screens/map/monitoring/ExplorationMonitoring.kt`
