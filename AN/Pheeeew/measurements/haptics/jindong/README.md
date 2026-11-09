# Jindong 감정 버튼 햅틱 적용과 비교

현재 기준: `an/feat/1145-an-jindong-haptics`에서 Jindong core 1.1.0의 주 진동을 **20ms·강도 0.6**으로 적용했다. [20ms 실기기 결과](device-report.md)는 자동 테스트 26회·입력 384회 기준이다. 실제 모터 시작 지연과 iOS 실기기 동작은 미측정이다.

| 확인 항목 | 기존 구현 (`native`) | Jindong (`jindong`, 기본값) |
| --- | --- | --- |
| Android 실행 방식 | 시스템 `CONFIRM` / `VIRTUAL_KEY` 효과 | 공통 DSL → 진동 파형 |
| iOS 실행 방식 | 직접 관리하는 Core Haptics 순간 타격 | 라이브러리의 Core Haptics 지속 진동 |
| 패턴 정의 | 플랫폼별 구현 | 공통 코드에서 20ms, 강도 0.6, iOS 날카로움 0.5 |
| Android Debug APK 생성 | 통과 ([로그](logs/04-native-comparison-build.log)) | 통과 ([로그](logs/03-jindong-validation.log)) |
| iOS 시뮬레이터 대상 컴파일 | 통과 ([로그](logs/04-native-comparison-build.log)) | 통과 ([로그](logs/03-jindong-validation.log)) |
| iOS 실기기 대상 컴파일 | 이 옵션으로 별도 미실행 | 통과 ([로그](logs/03-jindong-validation.log)); 기기 설치·실행은 미실시 |
| 공통 햅틱 단위 테스트 | 기존 세션·요청 관리 9개 통과 | 신규 제어기 5개 통과; 실제 기기 대신 가짜 실행기 사용 |
| 코드 스타일 | 공유 모듈 전체 검사 통과 | 공유 모듈 전체 검사 통과 |
| Android 첫 입력·연속 입력 OS 기록 | 첫 입력 10건·빠른 입력 60건 확인 | 첫 입력 10건·빠른 입력 60건 확인 |
| Android 백그라운드·화면 복귀 OS 기록 | 각 10/10 확인 | 각 10/10 확인 |
| 실제 모터 시작 지연 | 미측정 (n=0) | 미측정 (n=0) |
| 현재 20ms 촉감 정성 평가 | 비교 기준 | 미측정 |

현재 코드와 이 브랜치에 포함하는 측정 기준은 모두 **20ms**다. 10ms 실험 자료는 로컬에 별도 보존하며 이번 커밋·PR에는 포함하지 않는다.

## 세션 내용을 어떻게 적용했는가

DroidKnights 2026의 「선언형 햅틱 라이브러리 진동을 소개합니다」에서 소개한 Kotlin DSL과 플랫폼 실행 분리를 감정 버튼에 적용했다. Compose 상태 변화로 재생시키지 않고, 기존에 승인된 버튼 입력에서 공통 패턴을 직접 실행한다. 따라서 화면 진입이나 재구성 자체는 재생 조건이 아니다.

- [발표 자료](https://speakerdeck.com/l2hyunwoo/seoneonhyeong-haebtig-raibeureori-jindongeul-sogaehabnida), 24~31쪽: core / Compose 모듈과 Kotlin 패턴 정의.
- [공식 프로젝트](https://github.com/compose-jindong/jindong): main의 개발 API와 배포 API는 구분한다.
- [Maven Central 배포 메타데이터](https://repo.maven.apache.org/maven2/io/github/compose-jindong/jindong-core/maven-metadata.xml): 이번 작업에서 확인한 core 배포 버전은 1.1.0이다.
- [검토한 배포 소스](https://repo.maven.apache.org/maven2/io/github/compose-jindong/jindong-core/1.1.0/jindong-core-1.1.0-sources.jar): DSL·실행기·iOS 구현을 확인했다.
- [검토한 Android 배포 소스](https://repo.maven.apache.org/maven2/io/github/compose-jindong/jindong-core-android/1.1.0/jindong-core-android-1.1.0-sources.jar): 권한과 진동 파형 변환을 확인했다.

## 구현과 보호 동작

기준 커밋은 최신 `an/dev`의 `f7d1569022b4068531cf07d116e205b078e19453`이다. 감정 버튼의 입력 승인·시각 효과 조건은 바꾸지 않았다.

- [UI 연결](../../../shared/src/commonMain/kotlin/com/pheeeew/feature/emotion/component/EmotionHapticFeedback.kt): 화면이 RESUMED 상태일 때 소유자를 등록하고, 일시정지·종료·구성 제거 시 해제한다.
- [공통 재생 담당과 패턴](../../../shared/src/commonMain/kotlin/com/pheeeew/core/haptics/JindongEmotionHapticController.kt): 활성 화면들이 실행기 하나를 공유한다. 새 입력은 기존 재생을 취소하고 즉시 교체하며, 마지막 화면이 나가면 재생 취소와 실행기 해제를 각각 시도한다. 재생 예외가 입력 처리나 시각 효과를 막지 않게 한다.
- [Android 연결](../../../shared/src/androidMain/kotlin/com/pheeeew/feature/emotion/component/EmotionHapticFeedback.android.kt): 애플리케이션 컨텍스트를 전달하며 View의 햅틱 허용 여부와 시스템 햅틱 설정을 재생 전에 확인한다. `VIBRATE` 권한을 선언했다.
- [iOS 연결](../../../shared/src/iosMain/kotlin/com/pheeeew/feature/emotion/component/EmotionHapticFeedback.ios.kt): 앱이 활성 상태인지 추가로 확인한다. 기존 제어기와 Jindong을 동시에 활성화하지 않는다.
- [단위 테스트](../../../shared/src/commonTest/kotlin/com/pheeeew/core/haptics/JindongEmotionHapticControllerTest.kt): 연속 입력 교체, 소유자 공유, 복귀 시 새 실행기 생성, 미지원 기기, 취소 예외가 있어도 자원 해제를 시도하는 동작을 가짜 실행기로 확인한다.

## 비교 빌드 재현

실기기 비교는 기존 설치 앱과 다른 `com.pheeeew.hapticcomparison.native`, `com.pheeeew.hapticcomparison.jindong` 두 패키지를 사용한다. [별도 비교 앱의 빌드·실행 방법](../../../hapticComparison/README.md)과 [현재 기준의 실기기 결과 문서](device-report.md)를 참고한다.

`AN/Pheeeew`에서 다음 스크립트를 실행하면 두 별도 앱과 계측 APK, 소스·APK 해시 manifest를 순서대로 생성한다.

```sh
python3 hapticComparison/scripts/build-comparison-apks.py
```

일반 제품 소스의 기본값은 Jindong이며 `-PemotionHapticBackend=native`로 기존 구현을 선택할 수 있다. 초기 자동 검증 로그의 `:androidApp:assembleDebug` 결과는 일반 제품 패키지 빌드 증거이며 이번 실기기용 설치 파일과 다르다. 해당 초기 APK는 실기기 비교에 설치하지 않았다.

```sh
./gradlew :shared:testAndroidHostTest --tests '*Haptic*' \
  :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 \
  :shared:ktlintCheck
```

iOS Xcode 빌드에서 같은 옵션을 사용하려면 로컬 `gradle.properties`에 `emotionHapticBackend=native` 또는 `emotionHapticBackend=jindong`을 일시 지정하고 Shared 프레임워크를 다시 빌드한다. 이번 실기기 비교는 사용자의 선택에 따라 Android부터 진행한다.

## 확인된 차이와 해석의 한계

- iOS의 기존 순간 타격과 Jindong 1.1.0의 지속 진동은 다른 이벤트다. 20ms는 초기 후보이며 촉감이 같거나 좋아졌다는 뜻이 아니다.
- Android 1.1.0은 단일 이벤트 뒤에 1ms 무진동·1ms 보조 진동·1ms 무진동을 추가한다. 따라서 DSL의 20ms를 실제 파형 전체 길이나 실제 모터 동작 시간과 동일하게 해석하지 않는다.
- 기존 iOS 구현의 전용 직렬 큐, 오래된 요청 폐기, 오디오 중단 제어가 그대로 이전된 것은 아니다. Jindong 경로는 UI 스레드에서 실행기를 직접 호출하며 앱 내부 재생 대기열을 만들지 않는다. 첫 입력의 엔진 생성 비용과 오디오 중단·복구는 실기기 확인이 필요하다.
- 라이브러리가 내부 오류를 재생 핸들에 상세히 드러내지 않는 경우가 있다. 예외가 없거나 API 호출이 끝났다는 사실만으로 실제 진동 성공을 셀 수 없다.
- 기존 코드도 비교용으로 남아 있으므로 이번 단계에서 플랫폼 코드가 제거되었거나 유지보수 비용이 줄었다고 단정하지 않는다.
- [기존 #1060 조사](../1060/README.txt)는 과거 조건의 자료다. 이번 빌드 결과와 합쳐 실패율·지연을 계산하지 않는다.

## 실기기 비교와 공유 완료 조건

Android와 iOS 각각 같은 기기·OS·빌드 종류에서 A/B를 비교한다. 권장 표본은 첫 입력 10회, 일정 간격 입력 30회, 20회 연속 입력 3세트, 화면 복귀 10회, 백그라운드 복귀 10회다. 이 표본은 기능 확인용이며 일반적인 성능 우위를 입증하지 않는다.

기기·OS·빌드 옵션·입력 수·관찰 결과를 기록하고, Android 햅틱 설정 OFF와 iOS 오디오 중단·복구도 별도 확인한다. 로그의 재생 요청 수와 사용자가 실제로 느낀 진동 수를 구분한다. 실제 모터 시작을 측정할 장비가 없으면 물리적 지연은 계속 `미측정`으로 둔다.

결과 공유는 **세션에서 배운 점 → 기존 구현 → 적용한 코드 → 같은 조건의 결과표 → 도입/보류 판단** 순서로 작성한다. Android 별도 패키지 실기기 비교는 완료했다. [현재 기준의 20ms 실기기 결과](device-report.md)를 참고한다. 물리적 시작 지연과 iOS 실기기 확인, 최종 도입 판단은 남아 있다.

## 자동 검증 근거

- [스타일 오류로 중단된 첫 보존 로그](logs/01-style-check-failed.log)
- [추가 스타일 오류로 중단된 로그](logs/02-jindong-validation.log)
- [Jindong 최종 자동 검증 로그](logs/03-jindong-validation.log): 종료 코드 0. 햅틱 테스트, 공유 모듈 스타일, Android Debug APK, iOS 두 대상 컴파일 통과.
- [기본 Jindong 빌드 복원 로그](logs/05-default-jindong-build.log): 종료 코드 0. 기본 APK와 iOS 시뮬레이터 컴파일 결과를 Jindong 경로로 복원했다.
- [기존 구현 비교 빌드 로그](logs/04-native-comparison-build.log): 종료 코드 0. `-PemotionHapticBackend=native`에서 Android Debug APK와 iOS 시뮬레이터 대상 컴파일 통과.
- [기존 햅틱 테스트 XML](test-results/TEST-com.pheeeew.core.haptics.EmotionHapticSessionTest.xml): 9개, 실패·오류·건너뜀 0개.
- [신규 제어기 테스트 XML](test-results/TEST-com.pheeeew.core.haptics.JindongEmotionHapticControllerTest.xml): 5개, 실패·오류·건너뜀 0개.
- [비교 APK의 SHA-256 및 크기](apk-manifest.json): 로컬 APK는 `AN/Pheeeew/androidApp/build/outputs/haptic-comparison/native-debug.apk`와 `jindong-debug.apk`에 보존했다. APK 바이너리는 저장소 문서에 포함하지 않는다.

테스트는 Android 호스트 JVM에서 공통 로직을 검사한 결과다. iOS 컴파일 통과는 앱 링크·서명·설치·실제 진동 검증을 의미하지 않는다. 기존 소스의 컴파일 경고는 남아 있다. 스타일 수정 전 실패 로그는 성공 표본에 포함하지 않는다.

## 20ms 롤백 상태

[롤백 기록](rollback-20ms.json): 코드와 비교 APK를 20ms로 되돌려 빌드했다. 컨트롤러 소스 해시는 기존 `device-03`의 20ms 소스와 같다. 단말이 연결되지 않아 설치된 비교 앱의 재설치는 수행하지 못했다. 기존 20ms 측정값을 보존했으며 이번 롤백 후 실기기 재측정을 수행한 것으로 표시하지 않는다.
