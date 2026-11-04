# iOS 테스트의 Sentry 링크 오류 해결

작성일: 2026-10-29 KST
브랜치: `an/feat/1031-remote-config`
상태: 코드 변경 및 실제 iOS Simulator 테스트 실행 완료. 최종 변경 커밋과 리뷰 범위는 이 브랜치의 PR을 기준으로 확인한다.

## 문제와 원인

`:shared:iosSimulatorArm64Test`가 `ld: framework 'Sentry' not found`로 링크 단계에서 실패했다. [당시 실제 오류 발췌](ios-link-error.txt).

앱은 Xcode 프로젝트가 정확한 버전으로 지정한 sentry-cocoa 8.58.2를 Swift Package로 연결한다. Kotlin/Native 테스트는 앱과 별도의 실행 파일이므로 Xcode 앱의 프레임워크 검색 경로를 상속하지 않는다. Kotlin 라이브러리 컴파일 성공만으로 native 테스트 실행 파일의 링크까지 완료되는 것은 아니다.

## 변경

- 루트 `build.gradle.kts`에 `prepareIosTestFrameworks` 작업을 추가했다.
- `scripts/prepare-ios-test-frameworks.py`가 앱의 Sentry exactVersion 핀과 일치하는지 확인한다.
- 공식 8.58.2 `Sentry.xcframework.zip`을 받으며 공식 Package.swift의 SHA-256과 일치할 때만 사용한다.
- iOS arm64/x86_64 simulator slice만 `build/ios-test-frameworks/`에 준비한다. 다운로드나 변조 검사 실패는 빌드를 실패시킨다.
- shared와 developerTools의 **iosSimulatorArm64 TestExecutable**에만 framework 검색 경로를 연결하고 링크 전 준비 작업에 의존하도록 했다.
- 앱 프레임워크·Release 링크, SDK 버전, Sentry 런타임 의존성은 유지했다. CocoaPods나 추가 Gradle 플러그인을 도입하지 않았다.
- 준비 작업은 입력/출력을 선언한다. 재실행에서 UP-TO-DATE로 SDK 재다운로드를 생략하는 것도 확인했다.

공식 체크섬: `08b0fa226894b146d8bb76d057df245807cd4113a189d0fec8bc7cd69678c9c6`.

## 실행과 결과

이 컴퓨터의 기본 xcode-select는 Command Line Tools를 가리킨다. 시스템 선택을 바꾸지 않고 실행 명령에만 설치된 Xcode 경로를 지정했다.

```sh
cd AN/Pheeeew
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:iosSimulatorArm64Test :developerTools:iosSimulatorArm64Test
```

결과: **BUILD SUCCESSFUL**, 실제 테스트 실행 완료. Xcode 26.6, iOS Simulator 26.5 런타임이 있는 macOS arm64 환경에서 검증했다.

| 대상 | 테스트 | 실패 | 오류 | 건너뜀 |
| --- | ---: | ---: | ---: | ---: |
| shared iOS Simulator | 800 | 0 | 0 | 0 |
| developerTools iOS Simulator | 31 | 0 | 0 | 0 |

[실제 테스트 실행 로그 발췌](ios-tests-run-excerpt.txt), [JUnit XML에서 추출한 테스트별 결과](ios-test-summary.txt).

후속 회귀 확인에서는 iOS 테스트, 개발자 도구 분리, Android Debug 빌드 및 세 모듈 ktlintCheck를 함께 실행해 성공했다. 이전 성공 입력의 작업은 UP-TO-DATE로 재사용했다. [회귀 확인 원문 로그](ios-regression-check.txt).

준비 스크립트의 자동 검사 5개도 통과했다. 현재 앱 버전 일치, 앱 핀 변경 시 갱신 요구, 변조 체크섬 거부, simulator slice만 추출, 경로 이탈 거부를 확인했다.

```sh
python3 scripts/test-prepare-ios-test-frameworks.py
```

## 유지보수와 한계

- 앱의 Sentry 버전을 올릴 때 준비 스크립트의 VERSION/SHA256을 해당 공식 Package.swift와 함께 갱신한다. 앱과 다른 버전은 조용히 사용하지 않고 실패한다.
- 최초 테스트 링크에는 SDK 다운로드를 위한 네트워크와 Python 3.9 이상·curl이 필요하다. 빌드 출력은 Git에 추가하지 않는다.
- 환경별 Xcode 설치 경로가 다르면 DEVELOPER_DIR를 맞춘다. 정상적인 Xcode 선택이 되어 있으면 생략할 수 있다.
- 빌드 중 Swift/Clang 모듈 캐시 경고가 있었으나 링크와 테스트는 완료됐다.
- 이 결과는 Kotlin/Native 자동 테스트 결과다. 실제 서버 설정 변경, 앱 UI 캡처·영상, 반영 시간 실측 완료를 뜻하지 않는다. 해당 제출 검증은 계속 후속 작업이다.

## 공식 근거

- [Sentry Cocoa 8.58.2 Package.swift](https://github.com/getsentry/sentry-cocoa/blob/8.58.2/Package.swift): 앱과 일치하는 binaryTarget URL과 SHA-256.
- [Kotlin/Native 최종 바이너리](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html): framework와 test 실행 파일을 구분하는 바이너리 모델.
