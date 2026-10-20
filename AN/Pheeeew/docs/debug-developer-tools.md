# Debug 개발자 도구 (#784)

## 빌드 구조

- `developerTools/commonMain`에 정보 모델과 Compose 화면을 둔다. 화면에 사용하는 문자열도 이 모듈의 Kotlin 코드에만 있으며 별도 리소스나 외부 SDK는 추가하지 않는다.
- Android 앱은 `debugImplementation(project(":developerTools"))`과 `src/debug` 진입점을 사용한다. `MainActivity`는 `src/main`에서 공유한다. 빌드별 `AppEntry`가 같은 `AndroidAppContent`를 조립하고 Debug에만 개발자 도구를 붙인다. Release `AppEntry`는 일반 앱을 직접 구성하며 개발자 도구 대체 구현을 포함하지 않는다.
- iOS Debug는 `developerTools`의 **Debug 전용 정적 `Shared.framework`**를 링크한다. 이 프레임워크가 `api/export(project(":shared"))`로 앱 코드를 포함한다. 기존 `shared` 프레임워크를 함께 링크하지 않아 Kotlin 런타임을 중복 링크하지 않는다.
- iOS Release는 기존 `shared`의 Release 정적 `Shared.framework`만 링크한다. `shared`에는 `developerTools` 의존성이 없고, `developerTools` 모듈에는 Release 프레임워크가 없다. Xcode 빌드 스크립트가 `CONFIGURATION`으로 프레임워크 생성 모듈을 선택한다.
- `ContentView.swift`의 `#if DEBUG`가 Debug 진입점을 선택한다. Swift의 `import Shared`와 기존 지도/모니터링 API는 양쪽 프레임워크에서 동일하다.
- Kotlin의 일반 소스셋을 다른 컴파일의 기본 소스셋에 연결하거나 런타임 조건으로 구현을 숨기지 않는다.
- `App → AppContent → SettingsScreen`에는 일반 `settingsContent` 슬롯만 전달한다. 개발자 도구 항목과 문구는 Debug 모듈의 `DeveloperToolsSettingsEntry`에만 있다. 버전 7회 탭의 분석 식별자 기능은 그대로 유지한다.
- `ApiDependencies.buildConfig`는 실제 HTTP 클라이언트 생성에 전달한 불변 설정 객체다. 두 플랫폼 개발자 도구는 이 객체의 서버 환경과 URL을 표시한다. 버전/빌드 번호는 Android `BuildConfig`, iOS `NSBundle`에서 읽는다.
- 에러 재현 화면의 입력은 초안이다. `재현 조건 초기화`도 초안만 기본값으로 돌리고, `적용` 확인 다이얼로그에서 `완료`를 눌러야 설정이 반영된다. 완료 후 기존 UI composition을 제거하고 UI 소유 ViewModel·위치 자원을 정리한 뒤 조건을 적용한다. Android는 Activity 전체 store 대신 retained owner 안의 `AppUiSession`과 UI 그래프만 새로 만들며, iOS는 MainApp의 `AppUiSession`과 controller 그래프를 새로 만든다. 앱 화면 초기화이며 OS 프로세스를 재시작하지 않는다. 인증·영구 저장소·process 작업은 유지한다. 설정은 프로세스 메모리에만 보관한다.

참고: [Kotlin/Native 프레임워크 export](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html), [Kotlin 컴파일 구성](https://kotlinlang.org/docs/multiplatform/multiplatform-configure-compilations.html).


## 현재 화면을 유지하는 네트워크 복구 (2026-10-21)

- 기존 `적용 → 완료`는 화면·UI 그래프를 초기화한다. `재현 조건 초기화` 버튼의 위치와 문구는 유지하며, 초기화도 완료 적용 시 반영한다.
- 정상 연결로 지도에 진입한 뒤 설정 → 개발자 도구 → 에러 상황 재현 → 네트워크 복구 시나리오에서 `5초 후 / 10초 후 / 30초 후 / 수동 복구`를 선택한다.
- `현재 화면에서 시나리오 실행 → 실행`은 도구를 닫고 네트워크만 강제 오프라인으로 바꾼다. 설정에서 뒤로 가기로 지도로 돌아가 지도 이동·상세 조회 등 실제 조작으로 실패를 확인한다. 화면·입력·ViewModel·UI 캐시·진행 중 요청은 초기화하지 않는다.
- 화면 위 Debug 카드의 `지금 복구` 또는 설정한 시간이 지나면 추가 지연·응답 없음을 해제하고 실제 연결 사용으로 복귀한다. 실제 기기가 오프라인이면 연결 성공을 강제하지 않는다. 위치 조건은 유지한다.
- 복구 시나리오는 편집 중인 변경과 적용 중인 개별 에러가 없어야 실행할 수 있다. 개별 ‘계속 실패’ 조건 때문에 복구 후에도 실패하는 상황과 공통 네트워크 원인의 복구를 혼동하지 않게 한다.
- Debug process 메모리의 `NetworkRecoveryController`가 대기 작업을 소유해 Activity/화면 재생성 중에도 유지한다. 중복 실행을 막고, 네트워크 조건이 바뀌면 예약을 종료한다. 일반 완료 적용은 예약을 취소한 후 새 조건을 적용한다. 프로세스 종료 시 설정과 예약이 함께 사라진다.
- 컨트롤러는 제품 ViewModel의 재조회나 오류 해제를 호출하지 않는다. 기존 연결 관찰 → 제품 `onConnectivityChanged` → 재조회 경로가 복구를 담당한다. 이미 실제 엔진에 전달한 요청은 중단하거나 성공/실패로 바꾸지 않는다. 이 기능은 전송 중 실제 연결 단절이나 MapLibre 타일 장애를 재현하지 않는다.
- 편집하지 않은 설정은 적용 중 상태를 따른다. 페이지가 열린 동안 소비된 NEXT나 완료된 네트워크 복구가 다른 설정을 적용할 때 다시 활성화되지 않는다.
- 지도 재시도는 기존 오류를 복구 성공까지 유지하며 중복 재시도를 막는다. 이전 map revision의 늦은 콜백은 최신 오류를 해제하거나 덮어쓰지 않는다. ‘지도 스타일 로드 실패’는 실패 스타일 URL을 실제 지도에 전달하는 입력 재현이며 렌더러 생성 실패·타일 장애와 구분한다.
- 제품 등록 동작 테스트는 `shared`의 `RegistrationTestGraph : EmotionBindings`에서 수행하고 Debug 테스트는 HTTP·파일 입력 어댑터를 검증한다. Android UI 통합 테스트는 실제 앱 그래프에서 초기화 적용과 초기화 없는 수동 복구를 함께 확인한다.

## 실제 앱 경로 공유 (2026-10-20)

- 목적은 반복하기 어려운 운영 상황을 재현해 **실제 앱의 대응**을 확인하는 것이다. Debug 전용 앱 업무 로직을 별도로 유지하지 않는다. LMS+의 다른 요구사항을 일괄 구현하는 작업은 아니다.
- 위치는 양쪽 빌드 모두 `RefreshLocationUseCase → LocationRepositoryImpl`을 사용한다. Debug는 권한 컨트롤러와 `PlatformLocationProvider`만 장식한다. Mutex·상태·좌표/시각 검증·30초 제한 시간은 실제 저장소에 하나만 있다.
- 위치 획득 시작 시 결과·지연·응답 없음 조건을 고정한다. 이전의 ‘대기 중 변경 즉시 반영’에서 **다음 획득 요청부터 적용**으로 바꿨다. 완료 적용은 기존 UI 요청을 취소하고 새 UI를 시작하므로 중간에 권한 결과와 획득 결과가 섞이지 않는다. 같은 요청에서 제한 시간을 다시 시작하지 않는다.
- 권한 거부·서비스 꺼짐은 획득 전에 처리하여 획득 지연을 적용하지 않는다. 지정 좌표의 가상 권한은 OS 권한 변경이 아니다. 실제 위치 모드는 실제 권한과 제공자를 따른다.
- ‘위치 확인 실패’는 실제 Android·iOS 위치 제공자의 일반 실패와 동일하게 `null`을 반환한다. 오류 분류는 제품 저장소에 맡기며, 현재 제품은 이 응답도 `LocationTimeout`으로 매핑한다. 지연이 없다면 즉시 반환하므로 실제 30초 제한 시간을 기다리는 ‘응답 없음’과 구분한다. 이는 네이티브 GPS 고장 자체를 만드는 기능이 아니다.
- 등록은 실제 `EmotionRegistrationRepositoryImpl`의 파일 확인 → URL 요청 → signed PUT → 등록 순서를 따른다. 업로드 단계별 관측 callback과 업로드 캐시·requestId 재사용 규칙도 동일하다. 건너뛴 단계의 NEXT는 소비하지 않는다.
- 연결 실패는 일반 `IOException`, 응답 없음은 기존 `HttpTimeout`의 `HttpRequestTimeoutException`, 합성 서버 오류는 기존 HTTP 응답 처리 경로로 전달한다. 제품 API 실행기에 재현 전용 예외나 처리 분기를 두지 않는다. 쓰기 연결 실패는 기존 정책대로 `UNKNOWN → Unavailable`로 처리하며 실제 전송 전 차단 여부를 별도로 알려주지 않는다. 합성 503은 앱의 응답 처리 검증이며 서버 실제 반영 여부를 검증한 것이 아니다. 실패 재현을 위해 실제 쓰기를 보내지 않는다.
- 개별 ‘녹음 업로드 실패’는 URL_REQUEST 단계만 조작한다. signed PUT의 UPLOAD 단계는 공통 네트워크 프로필과 MockEngine 통합 테스트로 확인한다. 실제 PUT 단계만 고르는 수동 조작은 제공하지 않는다.
- 강제 오프라인은 공통 API·서명 업로드·별도 녹음 다운로드의 실제 엔진 위임 전에 차단한다. MapLibre 배경·타일에는 적용되지 않는다. 추가 지연은 KB/s 속도 제한이 아니다. 지도 스타일 오류는 Debug가 스타일 주소만 바꾸고 MapLibre가 실제 오류를 보고한다. ViewModel 오류·재시도·복구 처리는 제품 경로를 따른다.
- 연결 복구는 아래의 별도 실행 기능으로 같은 관찰자·ViewModel을 유지해 확인한다. 실제 기기 연결이 끊겨 있으면 강제 조건 해제로 온라인을 만들 수 없다.
- 정상 Activity 구성 변경에서는 retained owner와 UI 모델을 유지한다. 완료 적용 때만 이전 composition 제거 → UI store 취소·권한 정리 → 조건 적용 → 새 UI 그래프 순서로 처리한다. process API client·monitoring·Press 작업은 교체하지 않는다. iOS는 controller composition 제거에서 자신의 store와 위치 delegate를 정리한다.

수동 확인이 필요한 경계:
- Android 권한 다이얼로그 중 구성 변경, 결과 복귀와 반복 초기화 후 권한 요청
- Android/iOS에서 적용 취소·완료 후 실제 화면/입력 초기화, 지도·녹음 자원 해제
- 실제 네트워크 단절·복구, MapLibre 타일 장애, OS 권한·위치 서비스 변경
- UI 초기화는 시스템 백그라운드 프로세스 종료와 다른 기능이다.

## 최신 검증: 공통 처리 경로 공유

위 구조 개선 후 새로 실행한 결과다. 아래 과거 분리 검증의 해시·로그를 이번 변경의 근거로 재사용하지 않았다.

- Android 제품 host 테스트 **579개**, Debug 도구 **28개** 통과. iOS 시뮬레이터 제품 **581개**, Debug 도구 **28개** 통과. 위치 실제 30초 timeout, 취소·다음 요청 정책, 실제 지도 ViewModel의 획득 예외/지연 재시도/중복 방지, 파일/URL_REQUEST/UPLOAD callback, 등록 실패 후 업로드 캐시 유지와 NEXT 미소비를 검증했다.
- shared·developerTools·androidApp ktlintCheck, Android lintDebug, Debug APK·Release APK/AAB 빌드와 compile/runtime 의존성 격리 검사를 통과했다. Sentry 매핑 업로드는 제외했다. 로그: `/tmp/pheeeew-shared-path-android.log`, 추가 지도 테스트: `/tmp/pheeeew-map-location-regression.log`.
- Android Debug/Release 모두 공통 MainActivity를 컴파일했다. 제품 R8 이전 JAR **2,823개 항목**에서 재현 식별자가 없고 Debug JAR에 이전 DebugLocationRepository 클래스가 남아 있지 않음을 확인했다.
- 최신 APK/AAB의 Manifest·DEX·리소스와 R8 매핑에서 개발자 도구·재현 코드가 검출되지 않았다. Debug APK는 도구 포함을 확인하는 양성 대조군이다. APK SHA-256: `4c0b2d8a1fc29e8fd11e609da0893b7aa162e196c6bbb927d3f3ac925af5a06e`, AAB: `8ebd4b4ba798d58e558a16a877fbc2ab65681cac48354881816016b1fdbe76de`.
- 산출물 검사기 **12개** 회귀 테스트를 일반/최적화 Python에서 통과했다. 새 위치 어댑터와 Debug 조립·지도 타입도 검사한다.
- iOS arm64 시뮬레이터 **Debug/Release 호스트 앱**을 Xcode로 빌드했다. Debug 양성 대조군에 DebugLocationProvider·ErrorSimulation이 있고 Release 앱 **132개 파일**·제품 framework 바이너리·헤더에는 재현 식별자가 없었다. Release 로그는 shared의 실제 Release framework link와 developerTools task 부재를 보이며 링크 맵도 shared static framework만 참조한다.
- iOS Release 시뮬레이터 앱 SHA-256: `2a6e25b47e587d1229e083f2b19eedca2372f24db2275681c476287081d11b64`, 제품 framework: `9105bd403eb0cafb2abf7e8befb6ff5d012962c12b912a48806ec0206b0879f5`. 로그: `/tmp/pheeeew-shared-path-ios-debug.log`, `/tmp/pheeeew-shared-path-ios-release.log`.
- Native 테스트에는 Xcode가 생성한 Sentry framework 경로를 `/tmp/pheeeew-shared-path-native.init.gradle`로 **테스트 binary에만** 제공했다. 로그: `/tmp/pheeeew-shared-path-native.log`, `/tmp/pheeeew-map-location-regression.log`. 프로젝트의 Release 링크 설정을 변경하지 않았다.
- 실제 기기 UI 조작, 권한 다이얼로그 중 회전, 반복 초기화 후 네이티브 지도·오디오 자원, 실제 네트워크·타일 장애는 미검증이다. 이번 iOS 검증은 시뮬레이터 호스트 빌드/테스트이며 실기기·Archive·배포 서명 검증이 아니다. 커밋·스테이징·배포·실제 서버 쓰기는 수행하지 않았다.

### 재현 전용 통신 예외 제거 (2026-10-20)

- 재현을 위해 추가했던 전송 전 미반영 예외와 제품 API 실행기의 전용 catch 분기를 제거했다. API 실행기는 기존 제품 코드로 돌아갔다.
- Debug 연결 실패는 일반 IOException으로 전달하고 응답 없음·제한 시간을 넘는 추가 지연은 기존 HttpTimeout의 제한 시간·취소를 따른다. 쓰기는 실제 전송 전에 차단하지만 앱에는 기존 연결 실패와 동일하게 UNKNOWN을 전달한다. 등록은 Unavailable, 업로드 URL 요청 실패의 관측 결과는 UNKNOWN이 된다.
- Android 제품 579개·개발자 도구 28개 테스트, Debug APK 빌드, 관련 Kotlin 스타일, 양쪽 모듈의 의존성 분리 검사가 통과했다. POST·signed PUT 차단, 응답 없음·과도한 지연의 HttpRequestTimeoutException, 지연 중 취소와 실제 등록 저장소 매핑을 검증했다. 공유 Android 컴파일 JAR에 제거한 예외의 클래스·참조가 없음을 확인했다.
- iOS arm64 시뮬레이터의 제품 공통 코드 581개·개발자 도구 28개 테스트도 통과했다. 기존 로컬 Sentry 링크 보조 설정을 테스트 binary에만 사용했다. 로그: `/tmp/pheeeew-standard-network-failures-android.log`, `/tmp/pheeeew-standard-network-failures-ios.log`. 이 변경 후 기기 UI와 Release APK/AAB·iOS 호스트 앱을 다시 검증하지 않았다.

### Android 에뮬레이터 추가 확인 (2026-10-20)

- Pixel 8 / Android 14(API 34)에서 `DeveloperToolsLifecycleTest` instrumentation 테스트 1개를 실행해 통과했다. 실제 MainActivity·Compose 화면·Android 권한 컨트롤러·MapLibre MapView를 사용하고, 업무 API는 MockEngine과 별도 fixture 저장소로 격리했다. 실제 서버 쓰기는 보내지 않았다.
- 세로 → 가로 → 세로 Activity 재생성에서 동일 UI 세션이 유지됐다. 개발자 도구의 지역 조회 오류 선택/해제를 번갈아 수행하며 취소·완료를 3회 반복했다. 취소 시 UI 세션 유지, 완료 시 UI 세션 교체, process AppSession·AppGraph 유지와 새 네이티브 지도 연결을 확인했다.
- `:androidApp:assembleDebugAndroidTest :androidApp:ktlintCheck`가 통과했다. 로그: `/tmp/pheeeew-emulator-test-build.log`, `/tmp/pheeeew-emulator-lifecycle-result.log` (`OK (1 test)`, 68.933초). 마지막 화면: `/tmp/pheeeew-emulator-success.png`.
- 이 테스트는 실제 권한 다이얼로그 중 회전, GPS 수신, 마이크 녹음·재생, 네이티브 자원 누수, 실제 서버·타일 장애를 검증하지 않는다. 지도 View 연결 확인을 타일 통신 성공으로 간주하지 않는다. 추가 의존성과 테스트는 androidTest에만 있으며 제품 코드는 변경하지 않았다.

실기기 확인 순서:
1. 설정 → 개발자 도구 → 에러 상황 재현에서 조건을 고르고 적용한다. 취소 시 현재 화면과 적용값이 유지되는지, 완료 시 입력·화면만 초기화되는지 확인한다.
2. 위치 2초 지연과 지정 좌표를 적용하고 위치 버튼을 반복 눌러 중복 요청 없이 늦게 성공하는지 확인한다. 응답 없음은 30초를 기다려 확인한다.
3. 네트워크 오류와 위치 오류를 함께 설정하고 재시도·탭 이동·설정 화면 진입 시 오류 우선순위와 표시 범위를 확인한다.
4. 기본 조건으로 초기화한 뒤 실제 기기 네트워크를 끊고 복구한다. 동일 화면에서 데이터가 복구되는지 확인한다. 도구 적용의 UI 초기화를 연결 복구 검증으로 대신하지 않는다.
5. 권한 다이얼로그 중 회전·복귀와 반복 초기화 후 위치 요청·지도·녹음 동작을 확인한다. 실제 타일 장애는 별도 시스템 네트워크 조건으로 확인한다.

## 재검증

`AN/Pheeeew`에서 실행한다. Release 모니터링 설정과 Sentry 인증 토큰 검증은 기존 정책을 따른다.

```sh
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:bundleRelease \
  :shared:verifyDeveloperToolsIsolation :androidApp:verifyDeveloperToolsIsolation \
  -x :androidApp:uploadSentryProguardMappingsRelease \
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

UI 확인: Debug 앱의 설정 → 개발자 도구에서 버전 `2.3.0`, 빌드 `11`, Debug 구성, `dev`, `https://api-dev.pheeeew.com`을 확인하고 뒤로 가기로 설정으로 복귀한다. 조건을 변경한 뒤 취소하면 적용값이 유지되는지, `완료` 후에는 앱이 시작 화면으로 돌아오는지도 확인한다. Release 설정에는 진입 항목이 없어야 한다.

### 오류 재현 조립과 제외 검사

- 제품 `AppServiceFactory`는 실제 HTTP 엔진·연결 관찰자·파일 읽기·위치 의존성을 제공한다. 등록/오디오 저장소는 제품 Metro binding과 생성자 주입으로 조립하며, 팩토리에 별도 등록 저장소 생성 helper를 두지 않는다. API client 생성은 같은 팩토리의 일반 조립 함수를 사용한다. Android Release는 `AndroidAppServiceFactory`, iOS Release는 `IosAppServiceFactory`를 사용한다.
- Debug 플랫폼 조립에서만 `DebugAppServiceFactory`를 선택한다. Android는 `src/debug/ApplicationGraph.kt`, iOS는 `#if DEBUG`의 초기화 호출에서 모니터링의 최초 접근보다 먼저 같은 process graph를 구성한다.
- `developerTools/.../simulation`에 재현 상태·모델·HTTP 엔진·위치 제공자·가짜 권한·파일 읽기 어댑터를 둔다. 제품 App·ViewModel·API 실행기·위치 저장소에는 재현 상태 참조와 강제 실패 분기를 두지 않는다.
- 공통 API와 서명 업로드, 녹음 다운로드는 Debug HTTP 엔진을 사용한다. 네트워크 대기 후 HTTP method·API origin·path가 일치하는 개별 실패 조건을 검사하고 실제 엔진에 위임한다. 녹음 파일 확인 실패는 실제 Repository의 파일 읽기 단계에서 null을 반환한다. 녹음 업로드 실패는 업로드 URL 요청(POST /api/v1/audio-uploads)에서 차단하고, 감정 등록 실패는 POST /api/v3/emotions에 합성 503을 반환한다. 최종 도메인 실패를 직접 반환하는 등록 데코레이터는 없다. 강제 오프라인은 실제 엔진을 호출하지 않는다. 이미 실제 엔진으로 전달한 요청은 기존 취소·제한 시간·결과 불확실성 처리를 유지한다.
- `MapRenderer`와 `NativeMapRenderer`는 공통 계약·기본 구현으로 유지한다. 별도 `DebugMapRenderer`는 제거했다. Debug host가 `LocalMapStyleOverrideProvider`를 설정하고 `NativeMapRenderer`가 지도 mount·map revision당 한 번만 override를 요청한다. ‘다음 1회’는 실제 스타일 로드 입력 한 번에 실패 주소를 주며, ‘계속 실패’는 재시도 때마다 같은 실패 주소를 준다. 지도 오류 callback과 성공 복구 callback은 MapLibre가 발생시키며 실제 MapView 재생성 때 새 스타일 주소를 사용한다.
- Android·iOS 개발자 도구 overlay, 페이지·뒤로가기 상태와 적용 순서는 `DeveloperToolsHost`에 모았다. 플랫폼 진입점은 build info, 실제 앱 content, Android의 retained UI session 정리 callback만 전달한다.
- `shared:verifyDeveloperToolsIsolation`은 실제 main compilation의 source set 입력에서 재현 타입과 개발자 도구 참조를 검사한다. Android는 Debug/Release compile/runtime 의존성 그래프도 검사하고 Release의 Compose 실행 tooling을 거부한다. 산출물 검사기는 이전 이름의 재현 타입, Debug 어댑터, PreviewActivity까지 검사한다.
- 네트워크 화면은 연결 상태와 추가 지연·응답 없음만 제공한다. KB/s 처리량 제한과 MapLibre 타일 트래픽은 제어하지 않는다.
- 아래 2026-10-15 해시는 당시 기준 커밋의 기록이다. 현재 변경의 증거로 재사용하지 않는다.

## 2026-10-20 재현 코드 분리 검증

- 제품 main compilation 소스 입력과 R8 이전 `shared` 컴파일 JAR 2,818개 항목에서 재현 타입·개발자 도구 참조가 검출되지 않았다.
- Debug/Release compile/runtime 의존성 격리 검사, 세 변경 모듈 ktlint, Android lintDebug와 Debug APK·Release APK/AAB 빌드를 통과했다. Release 검증은 `-x :androidApp:uploadSentryProguardMappingsRelease`로 매핑 업로드를 제외했다.
- Android host: 제품 테스트 578개, Debug 어댑터 테스트 9개 통과. Debug 테스트는 오프라인 읽기·쓰기 전송 차단/복귀, 다음 1회와 다중 조건, 상세 404, 지연 성공·응답 없음·취소, 위치 지연·제한 시간·조건 변경을 검증한다.
- 산출물 검사기 회귀 테스트 11개가 일반/최적화 Python 실행에서 통과했다. 별도 임시 소스셋의 `ErrorSimulationLeak`을 main compilation에 주입했을 때 소스 격리 task가 해당 파일을 지목하며 실패하는 것도 확인했다. 저장소 소스는 이 누출 검사에 변경하지 않았다.
- 현재 Release APK SHA-256: `e51c99b1c8bf24873b480d73845c884fc07db124faa77d9b0879deb083c64249`; AAB: `18dde6f2ea6fe3e9a04cf6d8e4a16567f1949f045c702eaedb6fa26989eb0ede`. 두 결과물의 Manifest·DEX·리소스와 R8 매핑에서 재현·개발자 도구·PreviewActivity가 검출되지 않았다. Debug APK는 양성 대조군으로 포함을 확인했다.
- iOS arm64 시뮬레이터 Debug 호스트 앱을 Xcode로 빌드했다. 앱 바이너리에 재현 상태와 Debug 초기화가 포함되는 양성 대조군을 확인했다. 로그: `/tmp/pheeeew-isolation-ios-debug-arm64.log`.
- iOS arm64 시뮬레이터 Release 호스트 앱도 Xcode 빌드에 통과했다. 로그에서 `:shared:linkReleaseFrameworkIosSimulatorArm64` 실행과 `developerTools` task 부재를 확인했다. Release 프레임워크 바이너리·헤더와 호스트 앱의 실행 파일·프레임워크·리소스 132개 파일에서 앱의 개발자 도구·재현 식별자가 검출되지 않았다. Apple 표준 `DeveloperToolsSupport.swiftmodule` 경로는 앱 전용 도구와 구분하고 회귀 테스트로 보호한다.
- iOS Release 시뮬레이터 호스트 실행 파일 SHA-256: `29266ef1c8eba48d8b4c068bcee2e7fa97864b60a0b430d3310ed5c305fe1830`; 공유 정적 프레임워크: `e75b9fd2f620046dfec32984fa1dba9a0e5ad3c9ff9001bf6c70996963b2a3ed`. 로그: `/tmp/pheeeew-isolation-ios-release-arm64.log`.
- 실기기에서 메뉴·다이얼로그·재시작·재현 오류를 직접 조작한 검증은 수행하지 않았다.

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

## 2026-10-21 복구 시나리오 검증

- Android host: 제품 588개, Debug 도구 31개 통과. 등록 테스트는 실제 `EmotionBindings` 조립을 사용하며, 예약/수동 복구·조건 변경·초기화·전송 중 요청 보존과 NEXT 편집 병합을 검증했다.
- Pixel 8 Android 14 에뮬레이터에서 `DeveloperToolsLifecycleTest`의 2개 테스트가 통과했다. 회전 시 UI session 유지, 완료 적용 3회, 실제 개발자 도구를 통한 수동 오프라인/복구, 제품 스낵바 표시와 기존 연결 관찰에 의한 지도 재조회, UI/process session identity 유지를 확인했다. API는 MockEngine과 격리된 테스트 저장소를 사용했다.
- UI 테스트의 단방향 스크롤과 활성 창만 검색하던 탐색을 보완했다. 실제 제품 스낵바는 별도 Popup 창에서 표시되므로 다중 창 접근성 탐색으로 확인한다. Debug 복구 카드는 제품 스낵바와 겹치지 않도록 화면 오른쪽 중앙에 배치했다.
- Android Debug 앱/UI 테스트 APK 빌드, 세 모듈 ktlint, Release Kotlin 컴파일과 개발 도구 의존성 격리 검사가 통과했다. 이번 검증에서는 Release 패키징·Sentry 매핑 업로드를 실행하지 않았다. 산출물 검사기 회귀 테스트 12개도 통과했다.
- iOS arm64 기기용 공통 코드 및 arm64 시뮬레이터 공통 코드·테스트 소스를 컴파일했다. 최종 iOS arm64 시뮬레이터 Debug 호스트 앱 Xcode 빌드가 통과했다. 테스트 소스 컴파일은 native 테스트 실행을 의미하지 않는다.
- 실제 Android/iOS 기기, iOS 앱 실행/UI 조작, 최신 Release 패키지 산출물 검사는 수행하지 않았다. MapLibre 타일 장애와 실제 대역폭 제한은 기능 적용 범위에 포함되지 않는다.
- 로그: `/tmp/pheeeew-recovery-checks.log`, `/tmp/pheeeew-recovery-ui-final.log`, `/tmp/pheeeew-recovery-ios-final.log`.
