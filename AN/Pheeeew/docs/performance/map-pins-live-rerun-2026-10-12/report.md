# 개발 서버 8개 핀 재측정 — 2026-10-12

- Galaxy S23 FE(SM-S711N), Android 15, Wi-Fi, 확대 14, 고정 카메라
- 첫 실험과 같은 배포된 개발 API 및 v1 지도 API, 같은 공유 렌더러와 계측 코드
- 준비 실행 3회 제외, 본 측정 **30/30 성공**, 매번 응답 8개 및 화면 feature ID 8개 확인
- 개수·원시 기록·단일 HTTP 시도·페이지 수·구간 시간 합계 검증 통과

## 첫 실험의 실제 개발 서버 항목과 비교

단위: ms. 각 값은 중앙값 / P95.

| 구간 | 첫 실험(10/11) | 재측정(10/12) | 중앙값 차이 |
|---|---:|---:|---:|
| 요청→응답 전체 수신 | 241.6 / 302.0 | 48.5 / 110.0 | -193.0 |
| 응답 수신→화면 표시 | 46.6 / 347.8 | 41.5 / 343.3 | -5.1 |
| 요청→전체 표시 | 332.7 / 611.5 | 92.5 / 390.1 | -240.2 |

응답 중앙값은 48.5ms로, 첫 실험 대비 -79.9% 변화했다. 이번 응답 최대값은 288.7ms다.

## 이번 측정 범위

- 중심: 위도 37.3973086623786, 경도 127.10407313796357, 확대 14
- API bounds: minLongitude=127.09583339197923, minLatitude=37.38312429924282, maxLongitude=127.11231288400757, maxLatitude=37.41149034148752
- 현재 서버에서 실제로 8개를 반환하는 범위를 선택한 뒤, native 지도의 실제 범위로 다시 확인했다. 응답 개수를 잘라내거나 서버 데이터를 수정하지 않았다.
- 범위 탐색·인증 세션 준비·지도와 이미지 준비 시간은 계측에서 제외했다. 첫 실험과 같은 HTTP 시작 및 본문 전체 수신 지점을 사용한다.

## 비교 해석

- 첫 실험에는 정확한 지도 bounds와 핀 ID가 저장돼 있지 않았다. 같은 기기·8개 응답·확대 수준·API·렌더러를 맞췄지만 동일한 데이터와 범위의 재생은 아니다.
- 배포된 개발 서버의 코드·DB·부하, 실제 네트워크 및 핀 모양이 날짜에 따라 달라질 수 있어 차이의 원인을 서버 구현 변경으로 단정할 수 없다.
- 표시 구간은 JSON 해석·데이터 변환·화면 갱신·이미지 준비·native 프레임 대기를 포함하며 순수 GPU 시간이 아니다.
- 구간별 중앙값은 서로 더해 전체 중앙값으로 계산할 수 없다. P95는 30회에서 산출한 탐색적 값이다.
- 측정 앱은 별도의 난독화 없는 release 빌드다. 화면 feature ID 개수와 육안으로 구별되는 핀 개수는 다를 수 있다.

## 재현

`6de92800` 체크아웃에 `client-live-only.patch`를 적용하고 Android SDK 경로를 설정해 빌드한다. 범위 선택 절차와 v1 API만 실행하며 30회 측정한다.

```sh
cd AN/Pheeeew
./gradlew :mapPinBenchmarkTarget:assembleRelease
adb -s R5CX11AKCYJ install -r mapPinBenchmarkTarget/build/outputs/apk/release/mapPinBenchmarkTarget-release.apk
adb -s R5CX11AKCYJ shell am force-stop com.pheeeew.mappinprofile
adb -s R5CX11AKCYJ shell am start -n com.pheeeew.mappinprofile/.MapPinBenchmarkActivity --ei repetitions 30 --ez live true
adb -s R5CX11AKCYJ pull /sdcard/Android/data/com.pheeeew.mappinprofile/files/map-pin-profile.jsonl measurements.jsonl
```

서버 데이터가 바뀌면 준비 단계에서 선택되는 8개 범위도 바뀔 수 있다. `viewport_selected`에 실제 범위와 대상 핀 ID를 기록한다.

[원시 기록](measurements.jsonl) · [요약](summary.json) · [환경](environment.json) · [첫 실험 보고서](../map-pins-2026-10-11/report.md)
