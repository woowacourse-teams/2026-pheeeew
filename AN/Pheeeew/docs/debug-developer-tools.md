# Debug 개발자 도구 (#784)

## 빌드 구조

- `developerTools/commonMain`에 정보 모델과 Compose 화면을 둔다. 화면에 사용하는 문자열도 이 모듈의 Kotlin 코드에만 있으며 별도 리소스나 외부 SDK는 추가하지 않는다.
- Android 앱은 `debugImplementation(project(":developerTools"))`과 `src/debug` 진입점을 사용한다. `src/release` 진입점은 콜백에 `null`을 전달하며 구현 모듈을 참조하지 않는다.
- iOS Debug는 `developerTools`의 **Debug 전용 정적 `Shared.framework`**를 링크한다. 이 프레임워크가 `api/export(project(":shared"))`로 앱 코드를 포함한다. 기존 `shared` 프레임워크를 함께 링크하지 않아 Kotlin 런타임을 중복 링크하지 않는다.
- iOS Release는 기존 `shared`의 Release 정적 `Shared.framework`만 링크한다. `shared`에는 `developerTools` 의존성이 없고, `developerTools` 모듈에는 Release 프레임워크가 없다. Xcode 빌드 스크립트가 `CONFIGURATION`으로 프레임워크 생성 모듈을 선택한다.
- `ContentView.swift`의 `#if DEBUG`가 Debug 진입점을 선택한다. Swift의 `import Shared`와 기존 지도/모니터링 API는 양쪽 프레임워크에서 동일하다.
- Kotlin의 일반 소스셋을 다른 컴파일의 기본 소스셋에 연결하거나 런타임 조건으로 구현을 숨기지 않는다.
- `App → AppContent → SettingsScreen`에는 선택적 진입 콜백만 전달한다. 버전 7회 탭의 분석 식별자 기능은 그대로 유지한다.
- `ApiDependencies.buildConfig`는 실제 HTTP 클라이언트 생성에 전달한 불변 설정 객체다. 두 플랫폼 개발자 도구는 이 객체의 서버 환경과 URL을 표시한다. 버전/빌드 번호는 Android `BuildConfig`, iOS `NSBundle`에서 읽는다.

참고: [Kotlin/Native 프레임워크 export](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html), [Kotlin 컴파일 구성](https://kotlinlang.org/docs/multiplatform/multiplatform-configure-compilations.html).

## 재검증

`AN/Pheeeew`에서 실행한다. Release 모니터링 설정과 Sentry 인증 토큰 검증은 기존 정책을 따른다.

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:bundleRelease \
  :shared:verifyDeveloperToolsIsolation :androidApp:verifyDeveloperToolsIsolation \
  :shared:ktlintCheck :androidApp:ktlintCheck :developerTools:ktlintCheck

python3 scripts/inspect-developer-tools-android.py \
  --debug-apk androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --release-apk androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk \
  --release-aab androidApp/build/outputs/bundle/release/androidApp-release.aab \
  --mapping androidApp/build/outputs/mapping/release/mapping.txt

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/pheeeew-784-derived CODE_SIGNING_ALLOWED=NO build

xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath /tmp/pheeeew-784-release-derived \
  -archivePath /tmp/pheeeew-784-release.xcarchive CODE_SIGNING_ALLOWED=NO archive \
  > /tmp/pheeeew-ios-release.log 2>&1

./gradlew :shared:verifyDeveloperToolsIsolation --console=plain \
  > /tmp/pheeeew-native-graphs.log 2>&1

python3 scripts/inspect-developer-tools-ios.py \
  --archive /tmp/pheeeew-784-release.xcarchive \
  --build-log /tmp/pheeeew-ios-release.log \
  --graph-log /tmp/pheeeew-native-graphs.log \
  --link-map '<Release Pheeeew-LinkMap-normal-arm64.txt 경로>'
```

Android 검사 스크립트는 Debug APK를 양성 대조군으로 사용하고 Release APK/AAB의 Manifest·DEX·리소스 전체와 R8 클래스 매핑을 검사한다. 난독화로 클래스 이름이 없어질 수 있으므로 빌드의 resolved dependency graph에서 구현 모듈이 제외된 결과와 함께 판단한다.

iOS 검사는 Archive 성공 여부, 실제 Release 프레임워크 생성 작업, resolved dependency graph, 정적 프레임워크의 링크 맵 입력, 앱 실행 파일·포함 프레임워크·리소스를 함께 검사한다. 빌드 로그의 Archive 경로와 Release 구성, 링커의 `-map` 및 `-o` 경로를 전달한 Archive·링크 맵 헤더와 대조한다. Archive 실행 파일의 Mach-O UUID를 같은 링크 출력 또는 그 출력에서 생성된 dSYM의 UUID와 비교한다. 정적 프레임워크 입력은 `shared`의 Release 경로에 있어야 하며 실제 Release 프레임워크와 SHA-256이 같아야 한다. 해당 Archive를 생성한 DerivedData(링크 맵·dSYM·프레임워크)와 `shared/build` 결과물을 함께 보존해야 한다. 심벌 검색만으로 제외를 판정하지 않는다. `LD_GENERATE_MAP_FILE=YES`를 Release xcconfig에 설정했다. 서명을 생략한 로컬 Archive는 배포용 서명 검증을 대신하지 않는다.

UI 확인: Debug 앱의 설정 → 개발자 도구에서 버전 `2.3.0`, 빌드 `11`, Debug 구성, `dev`, `https://api-dev.pheeeew.com`을 확인하고 뒤로 가기로 설정으로 복귀한다. Release 설정에는 진입 항목이 없어야 한다.

## 검증 기록

- 기준 커밋: `28cdc83b3a23823b621391dc6a926dc66bb4f1df` (버전 2.3.0 / 빌드 11).
- 기능 구현 커밋: `7fb8d3aaa34ca3747b37af30ee18456c7f2ba4a6` (`feat: Debug 전용 개발자 도구 추가`).
- 제외 검사 커밋: `5000e263adad3c544f460d73cacc7415f409f87f` (`test: 개발자 도구 Release 제외 검사 추가`).
- 검증 대상: 위 두 커밋의 구현 및 검사 코드. 기존 검증 작업 트리와 커밋한 구현 파일의 내용 지문이 일치함을 확인했다.
- 검증 날짜: 2026-10-15 (Asia/Seoul).
- JDK 21.0.11, Gradle 9.1.0, Kotlin 2.4.10, AGP 9.0.1, Xcode iOS SDK 26.5.

### 완료한 검사

- Android Debug APK, Release APK/AAB 빌드 및 세 변경 모듈의 `ktlintCheck` 통과.
- Android resolved graph: `debug developerTools=true`, `release developerTools=false`.
- iOS `shared` resolved graph: `iosArm64.main developerTools=false`, `iosSimulatorArm64.main developerTools=false`. 양쪽 `shared` 프레임워크는 일반 `main` 컴파일을 사용한다.
- iOS Debug 앱 전체 빌드 통과. Debug 앱의 `Pheeeew.debug.dylib`에 `DeveloperToolsInfo`, `DeveloperToolsScreen`, `DebugMainViewController`가 포함돼 양성 대조군을 확인했다.
- Android Debug APK 설치·앱 실행 및 iOS Debug 시뮬레이터 설치·앱 시작 확인.
- iOS Debug Info.plist: `2.3.0`, `11`, `Debug`, `dev`, `https://api-dev.pheeeew.com`. 화면의 서버 값은 이 설정으로 만들어진 `ApiDependencies.buildConfig`를 사용한다.
- UI 조작 도구가 `NSCocoaErrorDomain 3840: Unterminated string` 오류를 반환해 **설정 메뉴 클릭·화면 표시·뒤로 가기 동작은 미검증**이다. 위 UI 확인 절차를 수동으로 진행해야 한다.

### Android 결과물 SHA-256

| 결과물 | SHA-256 |
| --- | --- |
| `androidApp/build/outputs/apk/debug/androidApp-debug.apk` | `c0a136ea9641cf50843e976727cf34c84381bfffe3bc966a6230f43c4dea5bdd` |
| `androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk` | `44ef13c0a01fdc26d6a393423427d091393eb02a026235e2a98c0f9931e63251` |
| `androidApp/build/outputs/bundle/release/androidApp-release.aab` | `3b0f3dfae11da8daa6ae6c8209640e2ccf433a1ae870f13a2cfced46b91ff9c6` |

Debug는 구현이 있는 DEX 20개, Release APK/AAB는 구현이 없는 DEX 1개다. ZIP의 모든 항목(Manifest·리소스 포함)과 Release R8 매핑에서 구현 식별자가 제외돼 검사에 통과했다.

### iOS Debug 결과물 SHA-256

- `/tmp/pheeeew-784-derived/Build/Products/Debug-iphonesimulator/Pheeeew.app/Pheeeew.debug.dylib`: `596fa3c115c40285712e52c49b00f06b10eb9d89e984d4c9b2d0d5b702530f43`.
- 최종 Xcode Debug 빌드 로그: `/tmp/pheeeew-ios-debug-final.log`.
- Release 의존성 유출 회귀 검사: 임시 Gradle init script로 `releaseImplementation(project(":developerTools"))`를 주입했을 때 `:androidApp:verifyDeveloperToolsIsolation`이 `Developer tools isolation failed: release`로 실패하는 것을 확인했다. 저장소 소스와 실제 Release 설정은 변경하지 않았다. 로그: `/tmp/pheeeew-784-leak-check.log`.
- 최종 Android 빌드·스타일·의존성 검사 로그: `/tmp/pheeeew-final-build-checks.log`.
- Android 결과물 검사 로그: `/tmp/pheeeew-android-isolation-final.txt`.

### iOS Release Archive 검사

- `/tmp/pheeeew-784-release.xcarchive` 생성 성공 (`CODE_SIGNING_ALLOWED=NO`, Release, iphoneos arm64).
- 최종 Xcode Archive 로그: `/tmp/pheeeew-ios-release-final.log`. `:shared:linkReleaseFrameworkIosArm64`가 실행됐고 `:developerTools:` 작업은 실행되지 않았다.
- 링크 맵: `/tmp/pheeeew-784-release-derived/Build/Intermediates.noindex/ArchiveIntermediates/iosApp/IntermediateBuildFilesPath/iosApp.build/Release-iphoneos/iosApp.build/Pheeeew-LinkMap-normal-arm64.txt`. 정적 `Shared.framework/Shared` 입력이 있고 `developerTools` 입력은 없다.
- 앱 내 실행 파일·포함 프레임워크·리소스 **127개 파일**에서 구현 식별자가 제외됐다. resolved graph와 실제 Xcode 링크 입력을 함께 확인했으므로 심벌 제거에만 의존한 판정이 아니다.
- Archive Info.plist: 버전 `2.3.0`, 빌드 `11`, 구성 `Release`, 환경 `prod`, URL `https://api.pheeeew.com`.
- 앱 실행 파일 `/tmp/pheeeew-784-release.xcarchive/Products/Applications/Pheeeew.app/Pheeeew` SHA-256: `b8192f0502cc2f92950ea3cf5f2c1ebdd6b269496d533ca0e0e127e04e0f9894`.
- 검사 로그: `/tmp/pheeeew-ios-isolation-final.txt`.
- 기준 커밋 위 변경 구현 파일의 내용 지문 SHA-256: `fc8cbd46a201f3094783be42444739f2c052a89e326086f4bc0ce2d938e5c575`. 변경 경로를 정렬해 `경로 + NUL + 파일 내용 + NUL`로 연결한 값이며 이 문서는 제외한다.

남은 확인은 두 플랫폼의 실제 설정 메뉴 진입·표시·뒤로 가기와 Release 메뉴 부재 확인이다. 네이티브 UI 조작 도구 오류 때문에 이 항목을 완료 처리하지 않았다.

## 리뷰 반영: 검사 실패 처리 및 빌드 근거 연결

- 두 검사 스크립트의 `assert`를 명시적인 조건 검사와 `ValueError`로 바꿔 `python3 -O` 및 `PYTHONOPTIMIZE=1`에서도 실패 검사를 수행한다.
- iOS는 다른 Archive의 로그, 다른 링크 맵, Debug 프레임워크 경로, Release 프레임워크 내용 불일치, 실행 파일·dSYM UUID 불일치를 거부한다.
- 다음 회귀 테스트 9개를 일반 실행과 두 최적화 실행에서 모두 통과했다.

```sh
PYTHONDONTWRITEBYTECODE=1 python3 scripts/test-inspect-developer-tools.py
PYTHONDONTWRITEBYTECODE=1 python3 -O scripts/test-inspect-developer-tools.py
PYTHONDONTWRITEBYTECODE=1 PYTHONOPTIMIZE=1 python3 scripts/test-inspect-developer-tools.py
```

기존 Android Debug/Release APK·Release AAB와 iOS Release Archive에도 수정한 스크립트를 `python3 -O`로 실행해 통과했다. 앱 코드는 변경하지 않았으며 위 결과물 해시는 그대로다. 앞의 구현 파일 내용 지문은 최초 기능·검사 커밋의 기록이다.
