# 그룹 감정 입력 대기열과 화면 성능 검증

작성일: 2026-10-04 · 이슈: [#575](https://github.com/woowacourse-teams/2026-pheeeew/issues/575) · 브랜치: `feat/575-emotion-queue-limits` · 측정 제품 코드: `95860d68`

## 요약

그룹 감정 입력을 빠르게 연속으로 받아들이고 요청을 묶어 보내는 클라이언트 구조를 구현했다. 가짜 서버 응답으로 입력 대기열과 요청 수를 검증했고, Samsung Galaxy 실기기에서 로컬 감정 입력 중 그룹 상세 화면의 프레임과 메모리를 측정했다. Android cadence 실행 순서를 바꿔 반복했지만 150ms 입력이 일관되게 더 느리다는 결과는 재현되지 않았다. 안정적으로 반복되는 화면 코드 병목을 찾지 못해 제품 UI 최적화나 성능 개선율을 주장하지 않는다.

측정 화면은 production `GroupDetailScreen` composable을 fixture Activity에서 표시한다. 감정 입력 callback은 로컬 상태만 바꾸며 실제 서버 요청, 서버 확정, 여러 기기 동기화, 물리 터치와 햅틱을 포함하지 않는다. 이 결과는 성능 기준선과 검증 경로이며, 전체 앱의 실사용 성능 보증은 아니다.

## 변경한 입력 처리

클라이언트는 탭을 즉시 수용한 뒤 100ms 동안 입력을 모아 감정별 개수로 묶어 보낸다. 요청 하나의 합계는 최대 100회이고, 전송 중인 입력을 포함한 미확정 대기열은 최대 300개다. 정상 연타는 debounce로 버리지 않는다. 응답이 429이면 `Retry-After` 동안 전송을 멈추고 허용량 안의 후속 입력은 대기열에 둔다. POST 결과가 불명확하면 같은 입력을 자동 재전송하지 않고 조회 결과로 조정한다.

서버의 입력별 멱등성이나 batch 일부 성공을 이 구조만으로 보장하지는 않는다. 해당 계약은 서버와 별도로 확인해야 한다.

## 합성 부하 결과

같은 coroutine test scheduler에서 100회 입력을 5ms 간격으로 발생시키고 fake action이 요청마다 250ms 뒤 응답하도록 했다. 기존 단건 전송과 100ms 묶음 전송을 비교했다.

| 지표 | 단건 전송 | 묶음 전송 |
| --- | ---: | ---: |
| 수락 입력 / fake 성공 snapshot 확인 입력 | 100 / 100 | 100 / 100 |
| POST 횟수 | 100 | 3 |
| 관측 최대 pending | 99 | 79 |
| 수락부터 fake 확정까지 p50 / p95 | 12,255 / 23,280ms | 370 / 485ms |

시간은 실제 네트워크 시간이 아니라 테스트 scheduler의 가상 시간이다. 반복 429와 대기열 포화도 fake action으로 확인했으며 운영 서버의 429, HTTP RTT, 실제 확정 지연을 측정한 결과가 아니다.

## Android 실기기 화면 측정

Samsung SM-G977N, Android 12 / API 31, 60Hz에서 `GroupDetailScreen`을 로컬 fixture로 실행했다. 매 iteration 전에 같은 cadence로 10회 입력해 화면과 상태 변경 경로를 워밍업한 뒤 fixture를 초기화했다. 측정은 40회 입력 × 5 iteration이다. 좌표 입력 cadence를 150ms와 250ms로 맞추고, 150→250ms 및 250→150ms 순서로 각각 한 번씩 실행했다. Perfetto의 `AndroidOwner:onTouch` 기록에서 실제 간격을 확인했다.

| 실행 순서 | cadence | 실제 입력 간격 p50 범위 | CPU frame duration p50 / p90 / p95 / p99 | frame overrun p50 / p90 / p95 / p99 |
| --- | --- | --- | --- | --- |
| 150 → 250ms | 150ms | 150.16–150.82ms* | 10.99 / 23.79 / 25.31 / 29.18ms | 0.68 / 17.14 / 17.41 / 18.88ms |
| 150 → 250ms | 250ms | 250.61–250.99ms | 8.85 / 12.32 / 13.58 / 17.80ms | 0.38 / 1.50 / 2.28 / 3.70ms |
| 250 → 150ms | 250ms | 250.76–251.02ms | 10.12 / 13.87 / 15.47 / 19.14ms | 0.48 / 1.74 / 2.61 / 4.35ms |
| 250 → 150ms | 150ms | 150.37–150.66ms | 9.50 / 14.70 / 16.74 / 20.82ms | 0.50 / 1.99 / 2.83 / 5.94ms |

`*` 첫 정방향 세션의 150ms 세 번째 trace는 touch slice 309개로 비정상적이어서 입력 간격 계산에서 제외했다. 해당 trace는 프레임 집계에는 포함했다. 역순 세션에서는 10개 trace 모두 예상 touch slice 80개를 기록해 이상이 재현되지 않았다.

정방향 실행에서 150ms CPU p95 / overrun p95는 25.31 / 17.41ms였지만, 역순에서는 16.74 / 2.83ms로 낮아졌다. 250ms는 각각 13.58 / 2.28ms에서 15.47 / 2.61ms로 변했다. 회차별 결과가 겹치므로 두 cadence 간 차이는 반복 확인되지 않았다. 실행 순서와 기기 상태의 영향을 두 세션만으로 분리할 수도 없다.

역순 실행의 iteration 최대 RSS 중앙값은 150ms에서 익명/파일/공유 134,444/186,408/2,068KiB, 250ms에서 130,608/183,528/1,300KiB였다. 표본과 실행 횟수가 제한돼 이 차이를 메모리 개선이나 누수로 해석하지 않는다. FrameTimeline에서는 `Buffer Stuffing`이 반복됐지만 해당 app frame은 `on_time_finish=1`이었다. 표시 큐 상태만으로 앱 코드가 frame deadline을 놓쳤다고 판단할 수 없고, 반복되는 단일 production hotspot은 찾지 못했다.

결론적으로 “성능이 완전히 괜찮다”거나 “150ms 연타가 병목이다”라고 단정하지 않는다. 관측된 프레임 tail은 남아 있지만 특정 코드 원인은 확인되지 않았다. 같은 코드의 최적화 전후 측정도 없어 개선율은 미측정이다.

## iOS Simulator 측정

iPhone 17 Pro Simulator, iOS 26.5에서 local fixture로 상세 화면 callback을 200회 실행했다. CADisplayLink frame interval p50/p95는 16.67/16.67ms, 기록된 hitch는 0회, sampled resident memory peak 변화는 +44.9MB였다. 단일 Debug Simulator 실행이며 iPhone 실기기 성능이나 앱 고유 메모리 증가량으로 해석하지 않는다.

## 재현과 증거

Android Macrobenchmark 구현은 [`GroupDetailMacrobenchmark.kt`](../groupDetailBenchmark/src/main/kotlin/com/pheeeew/groupdetailbenchmark/GroupDetailMacrobenchmark.kt)에 있다. 측정 당시 제품 코드의 HEAD는 `95860d68`이었고, cadence별 사전 워밍업·입력 간격·AB/BA 순서를 설정하는 Macrobenchmark harness는 해당 worktree에서 함께 사용했다. 이 문서는 측정 결과를 요약하며 raw Perfetto trace는 기기별 평가 자료로 저장소에 포함하지 않는다.

Android Macrobenchmark 실행 명령:

```bash
cd AN/Pheeeew
./gradlew :groupDetailBenchmark:connectedBenchmarkAndroidTest \
  --no-configuration-cache \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  --console=plain
```

정방향·역순 Macrobenchmark test는 각 2개 모두 통과했고, `ktlintCheck`도 통과했다. raw Perfetto trace와 기기 전용 로그는 용량과 재현 환경 차이 때문에 개인 평가 자료로 로컬에 보관했다.

## 아직 검증하지 않은 것

- 실제 사용자 입력부터 화면 피드백·햅틱까지의 지연과 실기기 전력
- 실제 API RTT, 서버 확정 시간, 운영 429·`Retry-After` 동작
- 서로 다른 기기의 동시 입력, 입력별 서버 중복 방지, 공유 합계 갱신 시간
- iPhone 실기기의 frame·메모리와 장시간 메모리 변화
- 전체 앱 navigation/ViewModel을 포함한 Android end-to-end 화면 성능

따라서 이 자료는 클라이언트 대기열의 합성 부하와 fixture 기반 화면 기준선에 대한 결과다. 서버 계약 및 실사용 입력 경험은 별도의 협의와 측정이 필요하다.
