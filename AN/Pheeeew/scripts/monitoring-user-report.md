# 최소 이벤트 리포트 집계

`monitoring-user-report.py`는 `meaningful_activity_day` 이벤트로 주간 WAU, 주간 반복 감정 표현율, W+1~W+4를 계산한다. 출력은 집계 JSON이며 사용자 ID는 출력하지 않는다. 실제 사람 대신 설치 익명 ID를 사용한다.

## 입력

PostHog에서 내보낸 이벤트를 한 줄에 JSON 객체 하나인 JSONL로 준비한다. 각 객체에는 `event`와 `properties`가 필요하다. CSV를 사용하는 경우 먼저 JSONL로 변환한다.

```json
{"event":"meaningful_activity_day","properties":{"anonymous_id":"example-install","activity_date":"2026-10-12","activity_type":"personal_press","environment":"prod","audience":"unknown","measurement_version":"user_report_v1"}}
```

별도 비공개 CSV로 사용자 분류를 관리한다.

```csv
anonymous_id,audience
example-install,external
example-team-install,internal
example-qa-install,test
```

앱 설정에서 버전 행을 7번 누르면 분석용 익명 ID를 확인하고 선택·복사할 수 있다. 개발 빌드는 test, 일반 운영 빌드는 unknown으로 전송한다. 외부 사용자라고 확인한 ID만 CSV에 external로 등록한다. 미등록 ID는 unknown으로 보고하고 외부 지표에서는 제외한다. CSV의 internal/test 분류 또는 이벤트 자체의 internal/test 표시는 external보다 우선한다. 수정된 CSV로 다시 실행하면 과거 기간에서도 팀원을 제외할 수 있다.

실제 이벤트 파일과 ID 분류 파일은 저장소 밖에 보관한다. 실제 사용자 ID나 토큰을 Git에 추가하지 않는다.

## 실행

`AN/Pheeeew`에서 실행한다. coverage는 수집이 정상적으로 이루어진 **연속된 날짜 구간**이며 양 끝을 포함한다. 수집 장애 날짜를 포함해 실행하면 결측을 0으로 오해할 수 있으므로 구간을 분리한다. `as-of`는 집계 시점의 KST 날짜다.

```sh
python3 scripts/monitoring-user-report.py \
  /private/tmp/events.jsonl /private/tmp/audiences.csv \
  --coverage-start 2026-10-12 \
  --coverage-through 2026-11-15 \
  --as-of 2026-11-16
```

- 주간 구간은 KST 월요일부터 일요일까지다.
- 중복 이벤트나 같은 날 다른 종류의 행동은 활성 일수를 늘리지 않는다.
- `repeat_rate`는 주간 2일 이상 활동한 설치 수 / WAU다.
- 리텐션은 기준 주 사용자 중 정확히 k주 뒤 활동한 비율이다.
- `complete=false` 주는 잠정값이다. 관측이 완료되지 않은 리텐션과 분모가 0인 비율은 null이다.
- 데이터가 늦게 수신되면 이전 주 값도 달라질 수 있으므로 결과의 as_of를 남긴다.
- 가이드 fixture 검증: `python3 scripts/test_monitoring_user_report.py`.

## 배포 전 남은 운영 작업

새 프로젝트 토큰은 이 변경에 포함하지 않았다. 로컬·CI에 새 토큰을 주입한 뒤 Android·iOS 실제 배포 설정으로 수신을 확인한다. SDK 큐 정리는 SDK 초기화 전에 한 번 실행되며, 정리에 실패하면 해당 실행에서는 PostHog만 시작하지 않고 Sentry는 유지한다. 다음 앱 실행에서 정리를 다시 시도한다.

기존 큐가 있는 앱의 실제 업그레이드와 새 프로젝트 수신은 실기기에서 확인해야 한다. Android 큐 경로는 posthog-android 3.59.0, iOS 큐 경로는 posthog-ios 3.64.1 소스를 기준으로 구현했으므로 SDK 업데이트 때 다시 확인한다.
