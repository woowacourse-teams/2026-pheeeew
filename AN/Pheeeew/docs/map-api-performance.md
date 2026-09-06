# 지도 이동 시 한숨 목록 API 호출 최적화 비교

## 범위

- 이슈: #252
- 브랜치: `an/perf/252-map-api-opt`
- 대상: 지도 이동 및 카메라 idle 이벤트 이후 한숨 목록 조회

## 변경 전후

| 항목 | 변경 전 | 변경 후 |
| --- | --- | --- |
| 연속 이벤트 | 이벤트마다 조회 작업 생성 | 마지막 bounds 기준으로 250ms 디바운스 |
| 동일 bounds | 중복 조회 가능 | 마지막 요청 bounds와 같으면 요청 생략 |
| 최신 결과 보장 | 이전 요청 결과가 늦게 도착할 수 있음 | `Long` requestId로 오래된 응답·오류 폐기 |
| 생명주기 | 화면 이탈 시 진행 중인 작업 정리 기준 부족 | 백그라운드 전환 시 작업 취소, 복귀 시 마지막 bounds 재조회 |
| 응답 데이터 | 서버 결과를 그대로 반영 | `SighPin.id` 기준 중복 제거 후 반영 |
| 진단 | 호출 흐름 확인용 로그 없음 | Android Debug 빌드에서 `Pheeeew.MapPerf` 이벤트 기록 |

## 자동화 검증

- `./gradlew :shared:testAndroidHostTest` 통과
- `./gradlew :shared:iosSimulatorArm64Test` 통과
- `./gradlew :androidApp:assembleDebug` 통과
- 동일 bounds 중복 생략, 마지막 bounds 우선 처리, 오래된 응답 폐기, 백그라운드 복귀 재조회 테스트 추가

## 실기기 확인

### Android

- 기기: Samsung SM-S911N (`R3CW10R6PLP`)
- 시나리오: 지도 화면에서 빠르게 4회 이동
- 관찰 로그:

```text
bounds_received
request_started id=4
response_applied id=4
```

4회의 연속 조작이 하나의 최신 bounds 요청으로 수렴했고, 최신 응답만 반영되었다. 지도 화면과 마커 표시에도 이상이 없었으며 크래시는 발생하지 않았다.

### iOS

- 기기: `POS - FF7R7PWXK2`
- OS: iOS 26.6.1
- `xcodebuild` 실기기 빌드 성공
- 앱 설치 및 실행 성공
- 지도 화면 진입 및 지도 조작 확인

iOS는 현재 공통 로거에 플랫폼별 출력 sink를 연결하지 않았으므로 Android와 같은 호출 횟수 로그는 수집하지 않았다. 네트워크 호출 횟수의 플랫폼별 수치 비교가 필요하면 다음 단계에서 iOS `OSLog` sink를 추가한다.

## 해석 및 한계

변경 전에는 해당 흐름에 측정 로그가 없어 동일 시나리오의 실기기 기준 호출 횟수를 정량 기록하지 못했다. 따라서 위 비교는 변경 전 소스 동작과 변경 후 Android Debug 로그를 기준으로 작성했다.

현재는 bounds가 조금 움직였을 때의 재조회 생략 기준은 적용하지 않았다. 지도 영역이 실제로 달라졌는데 결과가 갱신되지 않을 위험이 있어, 향후 실제 bounds 변화량과 UX를 측정한 뒤 별도 기준으로 도입하는 것이 안전하다.
