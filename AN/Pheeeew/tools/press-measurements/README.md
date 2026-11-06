# Android 프레스 기준선 준비

**현재 상태: Android 실기기 SM-G977N의 WS 기준과 후속 시나리오를 측정했다.** Android 결과는 [#1114 측정 보고서](../../measurements/press-ws-baseline/1114/physical-2026-11-06/REPORT.md)에 정리했다. iOS 시뮬레이터는 보조 입력에만 사용했으며, iOS 실기기 성능 측정은 아직 하지 않았다.

## 준비한 것

- `EmotionTapDrawProbe`는 새 앱 프로세스에서 콜백 550개까지 기록한다. 회차당 예열 50개와 측정 500개에 해당한다. 각 종료 로그에 `callbackStartUs`를 남긴다. 이 값은 프로브가 만들어진 뒤의 기기 단조 시계 값이며 같은 프로세스 안의 콜백 간격 계산에만 쓴다.
- 대기 표본은 종전처럼 최대 84개다. 표본 한도나 대기 한도를 넘으면 `outcome=overflow`가 출력된다. 빠진 값은 성공 표본으로 보충하지 않는다.
- `PressRaceMeasurements`는 메모리에 최근 표본 128개를 보관하고 로그 sink에는 최대 4,096개를 출력한다. sink 한도에 도달하면 `SinkLimitReached`를 한 번 남기고 일반 출력이 중단된다. 해당 표시는 회차의 계측 누락으로 취급한다.
- `android_runner.py`는 실행 전에 앱 PID 한 개를 확인한다. unfiltered logcat backlog를 읽어 nonce가 포함된 고유 shell marker를 확인한 시점을 시작 경계로 삼고, 그 다음부터 해당 app PID 줄만 보관한다. marker가 확인되지 않거나 log stream이 끝까지 읽히지 않으면 입력 전에 중단하거나 회차를 무효화한다. 측정 도중 PID가 바뀌거나 표본 ID 1–550이 맞지 않아도 무효다. 50회 예열 후 HTTP `FinalAck Applied`의 고유 표본 `presses` 합계가 50이고 콜백 1–50이 수락됐을 때만 측정을 시작한다. 마지막 ACK gate 이후 기기의 main 로그 버퍼에 고유 종료 marker를 쓰고, reader가 그 marker까지 읽은 것을 확인한 뒤 logcat을 종료한다. 시작·종료 marker 사이에 수집된 전체 로그를 다시 검사하므로 늦게 기록된 CoverageGap, SinkLimitReached, reject 및 overflow도 성공 판정을 취소한다. 기존 ACK timeout/failure는 최종 검사 결과로 덮어쓰지 않는다. 끝에서는 합계 550과 콜백 1–550이 모두 확인될 때까지 기다린다. 고정 20초 안에 확인되지 않으면 무효다. 이 gate는 클라이언트 ACK만 확인하며 서버 저장이나 화면 최신 상태를 증명하지 않는다.
- `PressTapInjector.java`는 지속 실행되는 Android `app_process` 안에서 입력을 예약한다. down/up injection 반환값은 앱 입력 수락과 다르므로, 실제 callback 수와 cadence는 계속 앱 로그로 확인한다. `--injector-dex`를 생략하면 각 탭마다 ADB shell 프로세스를 시작한다.

## 지표와 관측 한계

| 지표 | 관측 구간 | 해석과 한계 |
|---|---|---|
| 입력 횟수·간격 | Android 콜백 진입 시각 간 차이 | `callbackStartUs`로 측정한다. ADB 명령 발행 간격과 별도로 보고한다. 호스트 시각과 기기 시각을 빼지 않는다. |
| 콜백→그리기 | 탭 콜백 시작부터 Compose `drawContent` 완료까지 | 화면 갱신 코드가 그리기를 마친 시각이다. 패널에 실제 픽셀이 표시된 시각은 아니다. |
| HTTP 입력 대기·전송·재시도·응답 | 실제 큐 batch, 전송 attempt와 응답 처리 | 클라이언트 관측이다. 서버 저장 완료 시각을 뜻하지 않는다. 기존 500ms flush와 재시도 정책을 바꾸지 않는다. |
| WS 수신→UI·그리기, 재진입·복구 | snapshot 수신·게시와 검증된 기준 상태 게시 | UI·그리기 코드는 패널 표시 완료가 아니다. ACK만으로 서버 변경이 화면에 반영됐다고 단정하지 않는다. |
| 네트워크 상태·복구 | Android의 경로 관찰, socket loss, 검증된 재연결 | `Available`은 인터넷이나 서버 도달 성공을 뜻하지 않는다. 서버 터널 proxy 차단은 실제 Android 네트워크 단절 실험과 다르다. |
| 실제 패널 표시 시각·서버 수신 시각·기기 간 지연 | 미측정 | 현재 앱 계측과 ADB runner만으로는 관측하지 않는다. Perfetto FrameTimeline은 보조 자료로 수집할 수 있지만 패널 표시 시각으로 대체 해석하지 않는다. |

모든 실험 빌드에서 Debug 계측을 켜고 같은 서버, 입력 모드, 응답 timeout, 기기, 화면 방향과 앱 설정을 기록한다. HTTP와 WebSocket 배치·입력 정책 및 UI·애니메이션은 변경하지 않는다. 한 표본의 `CoverageGap`, probe `overflow`, 표본 ID 누락 또는 `SinkLimitReached`가 있으면 해당 지표의 회차를 완전 표본으로 보고하지 않는다.

## 로컬 도구 확인

아래 명령은 adb를 호출하거나 기기에 입력을 보내지 않는다.

```sh
python3 android_runner.py self-test
python3 android_runner.py plan
python3 android_runner.py run --dry-run \
  --condition-label normal --round-id 1 --interval-ms 500
```

`self-test`는 로그 파싱, 예열·측정 구분 및 간격 요약만 확인한다. `plan`은 과거 이슈의 조건 목록만 출력한다. 둘 다 측정 결과를 만들지 않는다.

## 한 회차 실행

1. 변경된 Debug 앱을 빌드·설치하고 앱에서 측정할 Press 화면을 연다. 기본 패키지는 `com.pheeeew`이며 별도 테스트 패키지를 설치했다면 `--package`로 지정한다. 실험 전에 앱 버전, A/B 입력 모드, 서버, 고정 HTTP request timeout 20초를 기록한다.
2. 각 회차는 새 앱 프로세스로 시작한다. runner는 앱을 종료하거나 화면을 탐색하지 않는다. 앱을 직접 강제 종료한 뒤 다시 열고 Press 화면으로 이동한다. 이동을 위한 탭은 runner 시작 전에 마친다. 화면이 열린 뒤 runner가 현재 PID를 확인하고, 콜백 ID가 1부터 시작하는지 검증한다.
3. 기본 입력 좌표는 SM-G977N의 현재 1080×2280 화면에서 `짜증` 입력 버튼의 중앙 `(694, 1160)`이다. 해상도나 레이아웃이 다르면 화면에서 실제 버튼 bounds를 확인해 `--x`, `--y`로 지정한다. `(575, 2030)`은 Press 탭 내비게이션 위치이므로 입력 좌표로 쓰지 않는다. 이 runner는 화면 탐색을 하지 않는다.
4. ADB serial은 `adb devices`에서 확인한다. 기본 회차는 측정 전 30초 무입력 대기, 500ms 간격 예열 50회, HTTP ACK gate 통과 후 같은 간격의 측정 500회, 전체 550회 ACK gate 대기 순서다. 각 ACK gate는 앱 설정과 같은 20초 제한을 쓴다. 회차마다 output JSON과 PID 한정 `.logcat.txt`가 분리된다.

```sh
python3 android_runner.py run \
  --serial <SM-G977N_SERIAL> \
  --condition-label normal-500ms \
  --round-id 1 \
  --interval-ms 500 \
  --package com.pheeeew \
  --send-inputs --confirm-press-screen \
  --output "${TMPDIR:-/tmp}/1114-physical-2026-11-06-normal-r1.json"
```

다음 반복 전에 새 프로세스로 다시 시작하고 `--round-id 2`, `3` 및 출력 파일명을 바꾼다. runner는 50회 예열의 앱 수락 및 최종 HTTP ACK를 기다린 뒤에만 측정 입력을 시작한다. 앱 로그의 측정 콜백 간격은 `device_callback_summary.measurement.callback_interval_us`에 있고, 예열·측정 ACK press 합계는 `http_ack_gates`에 있다. ADB shell 방식의 host 발행 간격은 `host_dispatch.*.host_dispatch_interval_ms`에 있다. 서로 다른 시계의 값을 직접 비교하거나 빼지 않는다.

100ms에는 지속 injector를 쓴다. Java SDK와 Android SDK가 설치된 환경에서 source를 임시 디렉터리에 빌드한 뒤 DEX를 기기에 복사한다. 모든 산출물은 저장소 밖에 둔다.

```sh
SDK=${ANDROID_SDK_ROOT:-$ANDROID_HOME}
ANDROID_JAR="$SDK/platforms/android-36/android.jar"
OUT="${TMPDIR:-/tmp}/1114-physical-2026-11-06-injector-build"
mkdir -p "$OUT/classes" "$OUT/dex"
javac -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$OUT/classes" PressTapInjector.java
d8 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes/PressTapInjector.class"
adb -s <SM-G977N_SERIAL> push "$OUT/dex/classes.dex" /data/local/tmp/1114-tap-injector.dex
```

별도 새 프로세스에서 20회 사전 실행을 먼저 하고, injector의 실제 발행 간격 p50/p95와 목표 간격을 비교한다. 앱 callback 간격은 결과로 보존하고 제외 기준으로 쓰지 않는다. 본 실행에도 같은 `--injector-dex`를 준다.

```sh
python3 android_runner.py run \
  --serial <SM-G977N_SERIAL> --package com.pheeeew \
  --condition-label fast-100ms-preflight --round-id 1 \
  --interval-ms 100 --warmup-count 0 --count 20 --idle-before-seconds 0 \
  --injector-dex /data/local/tmp/1114-tap-injector.dex \
  --send-inputs --confirm-press-screen \
  --output "${TMPDIR:-/tmp}/1114-physical-2026-11-06-fast-preflight.json"
```

일반 입력 회차에도 `--injector-dex /data/local/tmp/1114-tap-injector.dex`를 주면 동일한 지속 injector를 쓸 수 있다. injector `downInjected`/`upInjected` 수와 uptime 간격은 앱 callback 수락·cadence와 별도 기록된다.

실행 결과 JSON의 `round_integrity.valid_configured_measurement_round`가 `true`여야 같은 PID가 유지되고, ACK 합계 50/550, callback ID 1–550, 500개 측정 수락, callback timestamp를 모두 충족한 회차다. 앱 callback 간격은 처리 지연을 포함하는 결과이므로 회차 제외 기준으로 사용하지 않는다. 입력 조건 충족 여부는 별도로 보존한 device_injector_interval_ms와 목표 간격을 비교하며, 간격이 다른 실행을 같은 조건으로 합치지 않는다. HTTP reject, coverage gap, probe overflow, sink limit, timeout, 누락·중복 callback 또는 PID 변경은 무효화한다. `false`이면 상세 gate와 원본 발췌 로그에서 이유를 확인한다. runner는 입력 실행 시 `--send-inputs`와 `--confirm-press-screen`을 모두 요구한다. dry-run은 adb 없이 실행 계획만 출력한다.

## 이슈 시나리오 재현 순서

아래 각 조건은 3회 반복한다. **서로 다른 조건 블록 사이에는 최소 60초 기다린다.** 표에 지정한 반복 횟수와 B 입력을 임의로 합치지 않는다. 앱·기기 로그, 회차 JSON, Perfetto 파일은 회차별 경로에 보존한다.

| 조건 | 회차별 절차 | 자동화 준비 상태 |
|---|---|---|
| Idle | 입력 없이 5분 관찰. 이 블록은 입력 예열을 하지 않는다. | Perfetto·앱 로그를 별도 수집 |
| 보통 입력 | 위 기본 회차, 500ms 간격 50 예열 + 500 측정 | Android runner |
| 빠른 입력 | 100ms 간격 50 예열 + 500 측정. 사전 cadence 확인 필수 | 지속 injector 발행 간격 확인 및 callback 간격 기록 |
| 다른 입력 B | B에서 500ms 간격 500회 | 보조 클라이언트 입력 도구 필요 |
| A/B 동시 | A 100ms 500회와 B 500ms 100회를 함께 시작 | 외부 조정 필요. 각 기기 내부 간격만 비교하고 기기 시계를 직접 맞추지 않음 |
| B 변경 반영 | 알려진 변경을 만든 B 입력 50회, 응답 timeout은 고정 | 변경의 기준 이벤트와 B 입력 로그를 별도 보존 |
| 화면 이탈 후 재진입 | Press 화면에서 다른 앱 탭으로 이동해 10초 뒤 Press로 돌아온다. B에서 500ms 간격 5회, 블록마다 10회 반복 | 화면 이동과 B 입력 수동/외부 기록 |
| 앱 background 후 복귀 | 앱을 background로 보내 10초 뒤 foreground로 돌아온다. B에서 500ms 간격 5회, 블록마다 10회 반복 | 앱 lifecycle 전환과 B 입력 수동/외부 기록 |
| 실제 네트워크 단절 | Android 실제 연결을 15초 끊고 B에서 500ms 간격 5회. 회차마다 10회 반복 | Android 연결 상태·시간 기록 필요. 개발 서버 proxy 차단만으로 대체하지 않음 |

Android runner는 한 회차의 Android Press 입력을 기록한다. idle, A/B 동시 입력 조율, background 전환, 다른 클라이언트 입력과 실제 네트워크 차단은 자동화하지 않는다. 해당 이벤트의 호스트 조작 시각은 앱의 기기 단조 시각과 합치지 말고 입력 callback, HTTP/WS 관측과 함께 별도 기록한다. Android 물리 측정이 끝나기 전 iOS 시뮬레이터 결과를 기준선으로 사용하지 않는다.

## 기존 보조 도구

- `dev_connect_proxy.py`와 `measure_proxy_restore.py`는 개발 서버 CONNECT 터널을 제어하고 호스트 로그에서 복구 신호를 관찰한다. 전체 Android 네트워크를 끊지 않는다.
- `Perfetto` FrameTimeline/CPU trace는 프레임과 프로세스 활동의 보조 관측이다. Android 버전에서 data source가 실제 수집되는지 회차 전에 확인하고, trace가 비거나 파싱에 실패하면 미측정으로 기록한다.
- `PressRaceMeasurements`의 최근 128개 메모리 표본은 앱 내부 디버깅용이다. 회차 전체 자료는 logcat sink 출력 및 외부 runner JSON을 사용한다.
