This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across the Android and iOS apps.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - [androidMain](./shared/src/androidMain/kotlin) and [iosMain](./shared/src/iosMain/kotlin) are for code
    that's compiled only for the platform indicated in the folder name.

### 패키지 구조 (`shared/src/commonMain/kotlin/com/pheeeew`)

- `core/database` — 로컬 데이터베이스 관련 코드 (DB 스키마, DAO 등 로컬 영속성 계층)
- `core/designsystem` — 앱 전반에서 사용하는 디자인 시스템
  - `component` — 재사용 가능한 공통 UI 컴포넌트
  - `theme` — 컬러, 타이포그래피, 셰이프 등 디자인 토큰 및 테마 정의
- `core/network` — 네트워크 통신 관련 공통 설정 (HTTP 클라이언트, 인터셉터 등)
- `core/navigation` — 앱 내 화면 전환(네비게이션) 관련 공통 로직
- `core/utils` — 여러 모듈에서 공통으로 사용하는 유틸리티/확장 함수
- `core/permission` — 권한 요청 및 처리 관련 공통 로직
- `data/repository` — `domain`의 Repository 인터페이스에 대한 구현체
- `data/local` — 로컬 데이터소스(DB, DataStore 등) 구현
- `data/remote` — 원격 데이터소스(API 통신, DTO 등) 구현
- `domain/repository` — 비즈니스 로직에서 사용하는 Repository 인터페이스 정의
- `domain/model` — 앱의 핵심 비즈니스 모델(도메인 모델) 정의
- `feature` — 기능(화면) 단위 모듈 (현재 비어 있으며, 추후 기능별 하위 패키지 추가 예정)

### 앱 폰트

현재 앱의 `App` 진입점은 `core/designsystem/theme/AppTheme`으로 감싸져 있으며,
Android와 iOS 공통 UI에 Noto Sans KR을 적용합니다. `notoSansKrFontFamily()`에
Thin(100)부터 Black(900)까지 9개 정적 TTF를 등록해 굵기에 맞는 파일을 선택합니다.
폰트 원본과 SIL Open Font License는 `shared/src/commonMain/composeResources/font`에 있습니다.

Material 3 `Text`에서는 `fontFamily` 없이 굵기만 지정하면 됩니다.

```kotlin
Text("기본 본문")
Text("중간 굵기", fontWeight = FontWeight.Medium)
Text("굵은 제목", fontWeight = FontWeight.Bold)
```

`BasicText`, `BasicTextField`, Canvas의 `TextMeasurer`는 테마의 텍스트 스타일을
자동으로 상속하지 않으므로 `LocalTextStyle.current.copy(...)` 또는
`MaterialTheme.typography.bodyMedium.copy(...)`를 전달합니다. Material `Text`도
새 `TextStyle(...)` 대신 테마 스타일을 복사하면 폰트 설정을 유지할 수 있습니다.
단독 Preview에서 전역 폰트를 보려면 `AppTheme { ... }`으로 감쌉니다.

앱 UI는 `AppFontScale`에서 시스템 화면 밀도를 유지하고 글꼴 배율을 `1f`로 고정합니다.
별도 Compose 루트를 생성하는 창은 `AppDialog`, `AppAlertDialog`, `AppPopup`,
`AppModalBottomSheet`를 사용해 창 내부에서도 같은 배율을 적용합니다.
Android 약관 WebView는 `textZoom = 100`을 사용합니다.
운영체제가 표시하는 권한 요청 창은 이 설정의 적용 대상이 아닙니다.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
