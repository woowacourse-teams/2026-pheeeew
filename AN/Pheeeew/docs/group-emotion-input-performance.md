# 그룹 감정 입력 대기열과 화면 성능 검증

작성일: 2026-10-04 · 이슈: [#575](https://github.com/woowacourse-teams/2026-pheeeew/issues/575) · 브랜치: `feat/575-emotion-queue-limits` · 측정 제품 코드: `95860d68`

## 요약

그룹 감정 입력을 FIFO 대기열에 보관하고 한 건씩 서버에 보내는 클라이언트 구조를 구현했다. 현재 `an/dev` 백엔드는 감정 상태 하나를 받는 단건 계약이므로, 클라이언트도 요청마다 `state` 하나를 보내도록 맞췄다. 가짜 서버 응답으로 대기열·요청 제한을 검증했고, Samsung Galaxy 실기기에서 로컬 감정 입력 중 그룹 상세 화면의 프레임과 메모리를 측정했다. Android cadence 실행 순서를 바꿔 반복했지만 150ms 입력이 일관되게 더 느리다는 결과는 재현되지 않았다. 안정적으로 반복되는 화면 코드 병목을 찾지 못해 제품 UI 최적화나 성능 개선율을 주장하지 않는다.

측정 화면은 production `GroupDetailScreen` composable을 fixture Activity에서 표시한다. 감정 입력 callback은 로컬 상태만 바꾸며 실제 서버 요청, 서버 확정, 여러 기기 동기화, 물리 터치와 햅틱을 포함하지 않는다. 이 결과는 성능 기준선과 검증 경로이며, 전체 앱의 실사용 성능 보증은 아니다.

## 변경한 입력 처리

클라이언트는 탭을 즉시 수용하고 요청이 끝날 때까지 FIFO 대기열에 둔다. 현재 API에는 상태 하나만 보내며, 서버는 요청마다 해당 감정을 1회 증가시킨다. 한 요청에 여러 감정이나 증가 횟수를 담지 않는다. 결과가 미확정인 입력은 최대 300개까지 보관하고, 초과 입력은 화면에서 거절한다. 묶음 전송을 전제로 한 100ms 대기와 요청당 입력 수 설정은 제거했다.

429 응답을 받으면 그 단건 입력은 다시 보내지 않고 `Retry-After` 동안 다음 입력 전송을 멈춘다. 제한 중 새로 수용된 입력은 대기열에서 기다렸다가 제한이 풀리면 전송한다. POST 결과가 불명확하면 같은 입력을 자동 재전송하지 않고 조회 결과로 조정한다. 이 클라이언트 변경은 입력별 서버 멱등성이나 여러 기기 동기화를 보장하지 않는다.

요청 계약은 저장소의 `BE/src/main/java/com/pheeeew/groups/presentation/dto/GroupPressRequest.java` 및 `GroupController.press`와 일치한다. 서버는 `{"state":"ANGRY"}`를 받고 `GroupService.press`에서 1회 증가한 전체 집계를 반환한다.

## 합성 부하 검증

같은 coroutine test scheduler에서 단일 입력 요청이 즉시 시작되는지, 감정 입력 100회를 5ms 간격으로 수용했을 때 단건 API 호출 100회가 FIFO로 끝나는지 확인했다. 가짜 요청은 건당 250ms 뒤 응답하고, 429 probe에서는 첫 두 요청이 `Retry-After: 500ms`를 반환한다.

| 시나리오 | 수락 / 결과 | 요청 수 | 최대 미확정 입력 | 수락부터 결과까지 p50 / p95 |
| --- | ---: | ---: | ---: | ---: |
| 단일 탭 | 1 / 확정 1 | 1 | 1 | 250 / 250ms |
| 100회 연타, 5ms 간격 | 100 / 확정 100 | 100 | 99 | 12,255 / 23,280ms |
| 200회 입력, 첫 두 요청 429 | 200 / 확정 198·제한 2 | 200 | 199 | 확정 25,995ms / 제한 응답 995ms(p50) |
| 1,000회 입력, 1ms 간격 | 303 / 확정 303·용량 초과 697 | 303 | 300 | 미측정 |

가상 시간은 실제 네트워크 지연이 아니다. 이 결과는 단건 계약에서 요청 수가 입력 수와 같고, 느린 응답이 연타 대기 시간을 키운다는 점을 보여준다. 이전 문서의 “100회가 3개 batch 요청으로 처리됐다”는 probe는 fake action에 batch 지원을 가정했으므로 현재 API 구현의 성능 근거에서 제외했다. 묶음 요청으로 서버 호출을 줄이는 최적화와 실제 확정 지연은 백엔드가 batch 계약을 제공하고 운영 조건에서 검증하기 전까지 주장하지 않는다.

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

iPhone 17 Pro Simulator, iOS 26.5에서 local fixture로 상세 화면 callback을 200회 실행했다. 이전 측정 harness는 화면 최대 주사율을 hitch 기준으로 사용했고, Kotlin 쪽 입력 루프도 각 탭마다 Compose 프레임을 기다렸다. 리뷰에서 이 두 기준이 실제 CADisplayLink callback cadence와 입력 cadence를 왜곡할 수 있음을 확인해 측정 코드를 수정했다. 따라서 이전에 기록한 frame interval p50/p95 16.67/16.67ms 및 hitch 0회는 유효한 결과로 사용하지 않으며, 수정된 harness 재측정은 아직 하지 않았다. 기존 sampled resident memory peak 변화 +44.9MB는 단일 Debug Simulator 실행 참고값이며, 새 harness에서 재확인하기 전까지 확정 결과로 해석하지 않는다. iPhone 실기기 성능이나 앱 고유 메모리 증가량으로도 해석할 수 없다.

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
