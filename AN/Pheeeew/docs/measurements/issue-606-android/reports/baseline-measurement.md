# #606 Android 현재 방식 1차 실기기 측정

## 조건

- 일시: 2026-10-02 (KST)
- 코드: `532e31bd830c80476c9c0a94b1d0bb04cb47c80d`에 이 폴더의 계측 변경을 적용한 빌드
- 빌드: `:androidApp:assembleDebug`, 버전 2.1.0(7), 개발 API. Release 성능 수치로 취급하지 않는다.
- 기기: Samsung SM-S711N, Android 15, 1080×2340. 기존 로그인 데이터는 유지했다.
- 시나리오: 앱 프로세스 강제 종료 후 지도 탭이 열린 상태에서 앱 시작. 각 실행의 **첫 viewport 조회**를 비교한다. 지도 타일의 디스크 캐시, 서버 부하, 네트워크와 기기 온도는 고정하지 못했다. 1·2회차에는 카메라가 이어서 움직여 두 번째 viewport 조회가 발생했다.
- 원본: [`cold-run-1.log`](../raw-measurements/cold-run-1.log), [`cold-run-2.log`](../raw-measurements/cold-run-2.log), [`cold-run-3.log`](../raw-measurements/cold-run-3.log). 시간은 기기 `elapsedRealtime` 밀리초이고 구간 시간은 각 측정 코드의 경과 시간이다.

## 첫 viewport 측정값

| 항목 | 1회차 | 2회차 | 3회차 |
| --- | ---: | ---: | ---: |
| 첫 API 결과의 핀 수 | 139 | 139 | 166 |
| viewport 이벤트 → API 시작¹ | 706ms | 702ms | 705ms |
| API 전체(요청·응답·디코드 포함) | 621ms | 278ms | 480ms |
| 그중 DTO 디코드 | 63ms | 55ms | 75ms |
| DTO → 도메인 변환 | 8ms | 7ms | 9ms |
| 전체 핀 정렬·UI 모델 변환 | 7ms | 7ms | 8ms |
| 고유 이미지 수 | 14 | 14 | 12 |
| 고유 이미지 래스터화 합계² | 139ms | 129ms | 118ms |
| RGBA → Android Bitmap 변환 합계 | 38ms | 37ms | 38ms |
| 핀 feature 생성(첫 전체 집합) | 4ms | 4ms | 4ms |
| viewport 이벤트 → 첫 MapLibre idle 표시 확인 | 2,386ms (133개) | 2,203ms (133개) | 2,102ms (166개) |
| 마지막 source 설정 → 첫 idle 표시 확인³ | 745ms | 713ms | 683ms |

¹ `api-total` 종료 시각에서 API 전체 시간을 뺀 값과 첫 `viewport` 이벤트의 차이. 코드의 700ms 디바운스와 부합한다.  
² 첫 조회 후 로그에 남은 각 `raster-image` 시간의 합계. 로그상 메인 스레드에서 실행됐다.  
³ 1·2회차는 첫 idle 전에 보이는 핀이 133개로 다시 걸러져 그 source 설정 시각을 사용했다. 이 구간에는 MapLibre의 비동기 처리, 지도 타일, idle 감지 등이 섞여 있으며 순수 핀 렌더 시간은 아니다.

## 관찰

- **첫 핀 표시 대기에서 확실히 큰 구간**은 700ms 디바운스와 278~621ms API 전체 시간이다. API 내부의 네트워크/서버와 디코드 비용은 별도 서버 계측 없이 완전히 분리할 수 없다.
- **클라이언트 메인 스레드 작업**으로 고유 이미지 래스터화 합계 118~139ms와 Bitmap 변환 합계 37~38ms가 반복됐다. 긴 프레임을 만들 수 있는 구간이지만, 이 로그만으로 실제 프레임 손실의 원인이라고 단정하지 않는다.
- `GeoJsonSource.setGeoJson()` 호출은 측정상 0ms 수준이었으나 실제 source 처리·GPU 업로드는 비동기다. 첫 idle까지 683~745ms가 걸렸다는 사실만으로 그 지연을 핀 렌더링에 귀속할 수 없다.
- 3회차 앱 전체 `dumpsys gfxinfo` 스냅샷은 2,403프레임 중 deadline을 놓친 68프레임(2.83%)이었다. 지도 SDK의 프레임만 분리한 값이 아니고 수집 구간에 첫 진입 외 화면 상태 변화도 포함되어 지도 FPS로 사용하지 않는다.
- 지도를 이동했다가 돌아온 시도는 원래 bounds와 정확히 일치하지 않아 `origin=network`였다. 캐시 적중 성능 측정으로 채택하지 않는다. 원본은 [`viewport-revisit.log`](../raw-measurements/viewport-revisit.log)에 있다.

## 남은 검증

## 후속 검증 (2026-10-02)

### 첫 핀 프레임과 전체 응답 프레임

MapLibre `OnDidFinishRenderingFrameListener`에서 현재 load ID의 핀 feature를 조회해 `first-pin-frame`을 기록한다. 응답을 받은 load에 대해 모든 페이지 수신, 필요한 이미지 등록, `fullyRendered=true`, 현재 응답의 모든 핀 ID가 렌더 질의에 포함된 프레임은 `all-pages-frame`으로 따로 기록한다. 선행 캐시 스냅샷에는 전체 응답 마커를 찍지 않도록 최종 코드를 수정했다. 핀이 화면 바깥에 있는 영역은 전체 마커가 없을 수 있다. `map-idle-rendered`는 비교용으로 유지한다. 이는 SDK의 렌더 프레임 콜백과 feature 조회로 확인한 시점이며, 실제 디스플레이의 픽셀 표시 시각을 광학적으로 측정한 값은 아니다.

디버그 빌드의 첫 진입 1회(`system-trace-startup.log`, 171핀·17이미지)에서 첫 viewport부터 첫 핀 프레임까지 1,822ms, 전체 응답 프레임까지 1,823ms, 첫 MapLibre idle까지 2,131ms였다. `source-set`부터 첫 핀 프레임까지는 59ms였다. 이 응답은 `hasNext=false`인 한 페이지이므로 첫 핀과 전체 응답 시각이 거의 같다. 여러 페이지를 받는 실제 사례는 이번 기기 영역에서 재현되지 않았다.

고정된 화면에서 새로고침한 1회(`fixed-bbox-refresh-1.log`, 165핀)는 `refresh`부터 API 결과까지 780ms, 첫 핀 프레임까지 878ms, 전체 응답 프레임까지 880ms였다. 이미지 생성 로그는 없었다. 새로고침 중 이전 핀이 계속 표시되므로 이 값은 빈 지도에서 첫 핀까지의 시간과 다르다.

### System Trace

SM-S711N에서 25초 Perfetto trace를 수집했다. 원본은 작업 호스트의 `/private/tmp/issue606-android-20261002.perfetto-trace`(약 50MiB), 압축본은 같은 경로의 `.gz`(약 12MiB)에 있다. 앱 로그 원본은 [`system-trace-startup.log`](../raw-measurements/system-trace-startup.log). 분석은 공식 `trace_processor`로 수행했다.

- 같은 첫 진입에서 17개 이미지 래스터화 로그 합계는 153ms이고, 첫 이미지 시작부터 마지막 이미지 종료까지 약 168ms였다. 모두 앱 메인 스레드에 찍혔다.
- trace의 `map.bitmap`은 17회 합계 18.9ms, 최대 7.6ms였다. 앱 로그의 `bitmap-convert` 13ms와 차이가 나는 것은 각 이미지 시간을 정수 ms로 내림해 더하기 때문이다. `map.addImage`는 trace에서 합계 2.0ms였다.
- 앱 프레임 타임라인에 37.0ms와 43.8ms의 `App Deadline Missed` 프레임이 확인됐다. 두 프레임의 시작 사이 약 207ms에 이미지 래스터화가 겹쳤고, 그 사이 새 앱 프레임이 기록되지 않았다. 43.8ms 프레임은 Bitmap 변환과 feature 생성 구간에 겹쳤다. 이는 해당 메인 스레드 작업이 프레임 지연의 유력한 원인이라는 시간상 근거다. 다른 메인 스레드 작업과 기기 상태의 영향까지 배제한 인과 실험은 아니다.
- `source.setGeoJson()` 자체는 trace에서 약 0.05ms였다. 이후 59ms 만에 첫 핀 프레임 콜백이 왔고 RenderThread와 GPU completion 작업이 있었다. 이 trace만으로 그 59ms를 GeoJSON 처리, 타일, GPU 작업으로 정확히 나눌 수 없다.

### 최적화 빌드와 캐시의 한계

R8 최적화가 켜지고 `debuggable=false`인 임시 `benchmark` 변형을 개발 API·디버그 서명으로 빌드했다. Sentry 매핑 업로드 작업만 제외했다. 첫 실행 로그 [`optimized-run-pre-fix.log`](../raw-measurements/optimized-run-pre-fix.log)에서 완료 마커의 조기 판정을 발견했고, 다음 실행 [`optimized-run-frame-check.log`](../raw-measurements/optimized-run-frame-check.log)에서 `fullyRendered=true`만으로도 전체 핀 140개 중 86개만 질의되는 프레임이 있음을 확인했다. 이에 모든 핀 ID를 확인하도록 수정했다.

수정 후 실기기 실행 원본은 [`optimized-run-final.log`](../raw-measurements/optimized-run-final.log)이다(2026-10-03, 140핀·14이미지, 한 페이지). 첫 viewport부터 첫 핀 프레임까지 1,142ms(8개), 전체 핀 140개 프레임까지 1,445ms, 첫 idle까지 1,754ms였다. API 전체는 327ms, 래스터화 로그 합계는 39ms였다. `source-set`부터는 각각 33ms, 336ms, 645ms였다. bbox와 핀 수, 서버 응답 시간이 디버그 trace와 달라 **빌드 간 성능 개선율로 해석하지 않는다**.

`fixed-bbox-refresh-2.log`는 기기 위치·카메라가 움직여 bounds와 핀 수가 바뀌었다. 이전 이동·재방문 측정도 정확히 동일한 bbox가 아니어서 영역 응답 캐시 적중 성능으로 채택할 수 없다. 현재 캐시는 완전히 수신한 영역이 요청 영역을 포함할 때만 적중하며 TTL은 180초다. 새로고침은 캐시를 우회하고 겹치는 영역을 무효화한다.

실기기에서 최근 조회한 부모 영역의 내부로 확대·이동해 `origin=cache`를 재현했다. 핀 2개인 영역의 [`cache-contained-2-pins.log`](../raw-measurements/cache-contained-2-pins.log)에서 **응답 load ID**의 `page-result=0ms`, `source-set`→전체 핀 프레임 315ms였다. 같은 **정확한 bbox**를 새로고침한 [`same-bbox-refresh-2-pins.log`](../raw-measurements/same-bbox-refresh-2-pins.log)는 `origin=network`, API 862ms, `source-set`→전체 핀 프레임 320ms였다. 캐시 로그에는 응답보다 앞선 스냅샷 load의 프레임 마커도 있어 비교에서 제외했다. 캐시 조회에는 viewport 700ms 디바운스가 있고 새로고침에는 없으므로 두 이벤트의 전체 소요 시간을 동일 조건 A/B 값으로 비교하지 않는다. 이 두 시도 사이의 타일 캐시와 네트워크 상태도 통제하지 않았다.

## 다음 측정 조건

1. 위치·카메라를 고정한 재현 가능한 시나리오에서 동일 bbox·핀 수·이미지 수의 디버그/최적화 빌드를 각각 반복한다.
2. 복수 페이지를 내려주는 영역에서 첫 핀과 전체 페이지 반영 시각을 분리한다.
3. 타일 캐시의 냉시작·웜시작을 따로 통제해 source 등록 이후의 native/RenderThread/GPU 비용을 좁힌다.
