# Android 실기기 SSE 수정 확인 — 2026-11-08

**결과: 부분 확인.** 새 Debug 앱에서 SSE 첫 상태와 프레스 변경 이벤트를 받았습니다. 화면을 빠르게 나갔다 돌아오면 `429`가 다시 발생해 재접속 문제까지 해결됐다고 볼 수는 없습니다.

| 확인 항목 | Android 실기기 결과 |
|---|---|
| 기기·앱 | SM-G977N, Android 12 · Debug/dev 2.5.0 (103201) · SSE 수신 / HTTP 입력 |
| 첫 SSE 상태 | 1회: HTTP 200, 응답 헤더 267ms · 첫 상태 296ms |
| 프레스 입력 반영 | `답답` 1회 POST 적용 · 응답 69ms · SSE `data_changed` 수신 · 화면 20회 → 21회 |
| 화면 이탈 후 5초 뒤 재진입 | HTTP 200, 49ms (1회) |
| 재진입 반복 | 200 응답 85ms·52ms 각 1회. 다른 1회는 `429` 3회 후 화면을 나가 재시도 취소 |
| 빠른 이탈·재진입 | `429` 7회 후 HTTP 200. 첫 요청 후 상태 수신까지 23,785ms |
| 응답 헤더 전 취소 | 미측정. 해당 시도는 요청 후 51ms에 응답 헤더를 받아, 화면 이탈이 헤더 수신 뒤에 일어남 |
| 기기 간 종단 지연 | 미측정 |

## 진행 단계

1. Java 21과 SSE Debug 설정으로 앱을 빌드했습니다. 기존 `com.pheeeew.updatetest` 앱을 `adb install -r`로 교체했으며 앱 데이터를 지우지 않았습니다.
2. 앱에서 프레스 화면을 열어 SSE 첫 상태가 표시되는 것을 확인했습니다.
3. 같은 화면에서 `답답`을 한 번 눌렀습니다. HTTP 입력 응답이 적용됐고, SSE 변경 이벤트와 화면 점수 증가를 확인했습니다.
4. 화면 이탈·재진입을 시험했습니다. 5초 대기 뒤 재진입은 성공했지만, 빠른 재진입과 반복 시도에서 서버가 `429`를 반환했습니다.
5. 측정 종료 후 프레스 화면을 나가 SSE 연결을 닫았습니다.

## 판단과 한계

- 이번 빌드에서 SSE 응답 본문을 읽고 화면 상태를 갱신하는 경로는 실기기에서 동작했습니다. 앞선 실행에서 보였던 장시간 첫 응답 무응답은 이번 첫 연결에서 재현되지 않았습니다.
- 재접속 때의 `429`는 여전히 관측됩니다. 응답은 서버에서 왔지만, 서버 로그와 구독 식별 정보가 없어 이전 구독 정리 지연인지 다른 구독이 제한을 차지했는지는 확인하지 못했습니다.
- 빠른 이탈 실험은 응답 헤더를 받은 뒤 취소된 경우였습니다. Ktor의 응답 헤더 전 취소 문제를 직접 재현하거나 검증한 결과는 아닙니다.
- 입력 시각과 SSE 이벤트에 공통 식별자가 없어 입력→변경 이벤트 지연은 계산하지 않았습니다. 실제 패널 표시 완료 시각도 측정하지 않았습니다.

## 실행 환경

- 브랜치: `an/test/1115-polling-sse-integration`
- 앱 코드 커밋: `ec0b2a18d` (`fix(an): stream press race SSE events incrementally`)
- 앱 설정: `API_BASE_URL=https://api-dev.pheeeew.com`, `PRESS_INPUT_MODE=http`, `PRESS_RACE_RECEIVE_MODE=sse`
- 빌드: `:androidApp:assembleDebug -PappUpdateTestBuild=true -PpressDebugRaceReceiveMode=sse -PpressDebugInputMode=http` — 성공
- 원본 로그: [android-sse-stream-fix-2026-11-08.log](android-sse-stream-fix-2026-11-08.log)
