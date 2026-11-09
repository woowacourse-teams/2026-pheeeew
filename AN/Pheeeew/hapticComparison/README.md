# 별도 패키지 햅틱 실기기 비교 앱

기존 설치 앱과 다른 두 패키지에서 실제 `rememberEmotionHapticFeedback()`와 `dispatchEmotionTapFeedback()`을 호출한다. 실제 프로젝트의 `EmotionPad`를 그대로 배치한 모드도 제공한다. 로그인·서버·카운트 저장을 제외한 햅틱 경로 비교용이며 전체 앱의 종단간 검증을 대신하지 않는다.

| 구현 | 설치 패키지 | 런처 이름 |
| --- | --- | --- |
| 기존 Android 시스템 효과 | `com.pheeeew.hapticcomparison.native` | 히유 햅틱 native |
| Jindong 1.1.0 | `com.pheeeew.hapticcomparison.jindong` | 히유 햅틱 jindong |

앱과 계측 APK는 각기 별도 UID로 설치된다. `com.pheeeew` 및 기존 테스트 앱을 업데이트하거나 삭제하는 명령은 사용하지 않는다. 비교 코드는 `-PhapticComparisonBuild=true`일 때만 settings와 shared Android 소스에 포함된다. iOS 코드는 이 앱에서 실행하지 않는다.

## 빌드

아래 스크립트는 두 빌드를 순서대로 생성하고 app/test APK와 소스·APK 해시 manifest를 보존한다. 실행기는 설치 전에 이 manifest와 실제 파일이 같은지 검사한다.

```sh
python3 hapticComparison/scripts/build-comparison-apks.py
```

`AN/Pheeeew`에서 실행한다. 같은 shared 출력을 사용하므로 두 옵션을 동시에 빌드하지 않는다. 아래 명령으로 만든 app/test APK를 `hapticComparison/build/comparison-apks/<native 또는 jindong>/app.apk`, `test.apk`로 각각 복사해 보존한 뒤 테스트한다.

```sh
./gradlew :hapticComparison:assembleDebug :hapticComparison:assembleDebugAndroidTest \
  -PhapticComparisonBuild=true -PemotionHapticBackend=native

./gradlew :hapticComparison:assembleDebug :hapticComparison:assembleDebugAndroidTest \
  -PhapticComparisonBuild=true -PemotionHapticBackend=jindong
```

빌드 출력은 `hapticComparison/build/outputs/apk/debug/hapticComparison-debug.apk`와 `hapticComparison/build/outputs/apk/androidTest/debug/hapticComparison-debug-androidTest.apk`다. 보존된 APK의 패키지 ID는 설치 전에 `aapt2 dump badging`으로 검증한다.

## 실기기 실행과 집계

```sh
python3 hapticComparison/scripts/run-device-comparison.py \
  --serial DEVICE_SERIAL --adb /absolute/path/to/adb --aapt /absolute/path/to/aapt2 \
  --session NEW_SESSION_NAME

python3 hapticComparison/scripts/aggregate-device-comparison.py \
  measurements/haptics/jindong/device/NEW_SESSION_NAME
```

실행기는 비교용 APK 두 쌍만 설치한다. 테스트 동안 시스템 햅틱 설정을 ON/OFF로 바꾸고 `finally`에서 원래 값으로 복원한다. 기존 앱들의 UID·설치 경로·버전·설치/업데이트 시각을 전후 비교한다. 같은 기기에서 설정을 바꾸는 다른 테스트를 동시에 실행하지 않는다.

- 첫 입력은 각 앱을 강제 종료한 뒤 재실행하여 구현별 10회 확인한다. 실행 순서를 번갈아 배치한다.
- 이후 구현별 일반 입력 30회, 빠른 입력 20회 × 3세트, 화면 복귀 10회, 앱 복귀 10회, 입력 거절·시각 효과 전용·View 햅틱 OFF 각 10회, View 햅틱 복원 1회를 확인한다.
- 실제 프로젝트 `EmotionPad` 모드에서도 일반 입력 10회와 빠른 입력 20회를 별도 실행한다. 이 모드에서는 실제 입력 승인과 진동 서비스 기록을 비교하며 햅틱 콜백 처리 시간은 계측하지 않는다.
- 별도 실행에서 시스템 햅틱 OFF 상태로 구현별 10회 입력한다.
- 자동 입력은 창 포커스와 화면 전환 안정화를 기다린 후 보낸다. 일반 입력은 UIAutomator 클릭 후 120ms, 빠른 입력은 DOWN/UP 간 16ms와 입력 뒤 25ms 대기를 사용한다. 앱이 수신한 입력 콜백 간격은 원본의 단조 시각으로 계산한다. 물리적 터치 시각이나 주입기의 DOWN 간격을 측정한 것은 아니며, 설정값을 실제 콜백 간격으로 표시하지 않는다.

## 측정값의 의미

- `callbackNanos`: 단조 시각으로 잰 실제 햅틱 콜백의 동기 실행 시간. 물리적인 진동 시작 지연이 아니다.
- `startElapsedNanos`: UI 입력 콜백 시작의 단조 시각. 입력 간격 계산에 쓴다.
- `wallTimeMillis`: 콜백 실행 후 행을 기록한 기기 벽시계 시각. OS 기록을 연결하는 보조 기준이다.
- 진동 서비스 기록은 `dumpsys vibrator_manager` 원본에서 비교 패키지별로 추출한다. 상태가 `finished`여도 실제 모터 동작·강도·촉감을 계측한 것은 아니다.
- 서비스 시작/종료 차이는 OS가 기록한 수명이며 실제 모터 지속 시간과 다를 수 있다. 물리적 지연과 촉감은 별도 센서·사용자 평가가 없으면 미측정으로 남긴다.
- 결과는 해당 기기 한 대와 해당 Debug 빌드에 한정한다. 다른 Android 기기나 iOS로 일반화하지 않는다.

[실행 결과와 원본 근거](../measurements/haptics/jindong/device-report.md)
