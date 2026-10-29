# #1032 최소 지원 버전 및 Android 업데이트 구현 가이드

작성일: 2026-10-29 KST. 기준: `origin/an/dev`의 `f6c929412e25616e081609ff3769fee26519bb0c`.
작업 브랜치: `feat/1032-in-app-updates`. 이 문서는 구현 계획이며 테스트 성공·실기기 검증 결과가 아니다.

## 1. 작업 범위와 정책 원본

[이슈 #1032](https://github.com/woowacourse-teams/2026-pheeeew/issues/1032)의 클라이언트 구현을 진행한다. 서버 계약과 운영 절차는 [기존 BE 문서](../../../BE/docs/client/app-version.md)를 따른다. 새 버전 관리 API나 원격 설정으로의 이관은 추가하지 않는다.

| 항목 | 적용 기준 |
| --- | --- |
| 정책 조회 | 인증 없이 `GET /api/v2/app/version?platform=android` 호출 |
| 설치 버전 < 최소 지원 버전 | 필수 업데이트, 주요 기능 진입 차단 |
| 최소 지원 버전 ≤ 설치 버전 < 최신 버전 | 권장 업데이트, 나중에 선택 가능 |
| 설치 버전 ≥ 최신 버전 | 정상 진입 |
| 정책 관리 | `app_version` 단일 경로, 기존 SQL 등록·변경·복구 절차 사용 |
| 변경 반영 | 앱 시작·전경 복귀·명시적 재시도 때 재조회 |
| 최소 버전 상향 | 대상 사용자가 실제 새 버전을 설치할 수 있는지 먼저 확인 |

문서의 `1.0.0`, `1.1.0`, `1.2.0`과 example.com은 예시다. 운영 DB의 확정값으로 취급하거나 앱에 하드코딩하지 않는다. 저장소의 Android 빌드는 현재 `versionName=2.5.0`, `versionCode=13`이지만 이것만으로 서버 최소 지원 버전을 추정하지 않는다. 현재 운영 정책과 변경 근거는 기존 서버 운영 기록을 따른다. 이번 작업에서 별도 지원 종료 정책을 새로 만들지 않는다.

조회 실패 처리와 영속 캐시 만료 기준은 BE 문서가 확정하지 않은 클라이언트 사항이다. 아래 4절은 이 작업의 구현 권고안으로 구분한다.

## 2. 기존 코드에서 출발하기

경로는 `AN/Pheeeew/` 기준이다.

| 파일 | 현재 역할 | 변경 방향 |
| --- | --- | --- |
| `shared/src/commonMain/kotlin/com/pheeeew/domain/model/version/AppVersionPolicy.kt` | 숫자 버전 비교, 필수/권장 판단 | 서버 정책 판단을 유지. Play 설치 상태와 분리 |
| `shared/src/commonMain/kotlin/com/pheeeew/data/remote/api/AppVersionApi.kt` | 공개 정책 조회 | 기존 인증 없음 계약 유지 |
| `shared/src/commonMain/kotlin/com/pheeeew/data/repository/AppVersionRepositoryImpl.kt` | API 결과를 정책으로 변환 | 캐시 도입 시 읽기·검증·저장 책임 연결 |
| `shared/src/commonMain/kotlin/com/pheeeew/feature/screens/settings/AppStartupViewModel.kt` | 10초 조회 제한, 세션 내 판단 유지 | 정책 출처·실패·재시도 상태 연결 |
| `shared/src/commonMain/kotlin/com/pheeeew/App.kt` | 시작/전경 조회, 차단 화면, 스토어 이동, 권장 안내 | Android 업데이트 액션을 주입하고 기존 진입 조건 유지 |
| `androidApp/src/main/kotlin/com/pheeeew/MainActivity.kt` | Android Activity 진입·복귀 | Activity Result와 Play 업데이트 어댑터 연결 |
| `androidApp/build.gradle.kts`, `gradle/libs.versions.toml` | Android 의존성과 앱 버전 | Play 라이브러리를 Android 모듈에만 추가 |
| `shared/src/commonMain/kotlin/com/pheeeew/core/di/AppGraph.kt` 및 플랫폼 Graph | 의존성 구성 | 필요 시 정책 저장소와 플랫폼 액션 주입 |

현재 첫 조회 실패는 진입 허용, 같은 ViewModel에 이전 판단이 있으면 실패 때 유지한다. `App.kt`의 스플래시 최대 4초와 정책 조회 최대 10초는 다른 제한이다. 스플래시 종료가 정책 확인 완료나 필수 차단 해제를 뜻하지 않도록 유지한다. 인증 복원은 정책 조회와 병렬로 진행되고 있으므로, 차단 요구를 모든 인증 요청의 금지로 확대하지 않는다.

## 3. 구현 순서와 책임

1. 기존 정책/시작 화면 테스트를 실행해 기준을 남긴다. 운영 정책 변경 없이 Mock/Fake로 경계를 먼저 재현한다.
2. 공통 코드에 플랫폼 업데이트 액션과 관찰 가능한 상태 계약을 정의한다. 예시 이름은 `AppUpdateController`, `AppUpdateState`이며 실제 명명은 프로젝트 관례에 맞춘다.
3. Android 어댑터에서 Play 조회·시작·결과·설치·복귀를 처리한다. Activity와 launcher는 Activity 수명에 묶고 장수명 Graph/ViewModel에 Activity를 보관하지 않는다.
4. `App.kt`의 필수/권장 버튼을 컨트롤러에 연결한다. iOS는 기존 스토어 이동을 제공하는 기본 구현을 사용한다.
5. 실패·캐시 권고안을 구현하고 상태 전이 테스트를 보강한다.
6. Debug 재현 도구와 실제 Play 검증을 나누어 완료 증거를 남긴다.

공통 판단은 `Current / UpdateSuggested / UpdateRequired`, 실행 상태는 `Idle / Checking / Launching / Downloading / Downloaded / Installing / Canceled / Failed / Unavailable` 등으로 분리한다. 정책 변경은 판단을 갱신하고, Play 콜백은 실행 상태만 갱신한다. `RESULT_OK`만으로 필수 차단을 해제하지 않는다. 설치 후 실제 버전과 다시 받은 서버 정책으로 판단한다.

새 Play 흐름이 진행 중이거나 사용자에게 재시도 선택을 기다리는 동안 중복 실행을 막는다. 재구성·회전·Play 화면에서 복귀할 때 같은 안내가 무한 재실행되지 않도록 한다. 권장 안내의 나중에 선택은 정책의 최신 버전과 연결해 새 권장 버전이 도착했을 때 이전 선택이 영구 적용되지 않게 한다.

## 4. 조회 실패와 캐시: 구현 권고안

BE 문서의 합의 미정 영역에 대한 제안이다. 기존 서버 정책을 수정하는 내용은 아니다.

| 상황 | 제안 동작 |
| --- | --- |
| 첫 실행, 유효한 저장 정책 없음, 조회 실패/오프라인/타임아웃 | 현재처럼 진입 허용, 다음 전경 복귀에 재시도 |
| 현재 세션에 정상 판단 있음, 재조회 실패 | 기존 판단 유지. 필수 차단을 오류로 해제하지 않음 |
| 프로세스 재시작, 유효한 마지막 정상 정책 있음 | 현재 설치 버전으로 다시 계산한 뒤 네트워크 재조회 |
| 응답 형식/정책 순서/URL 오류 | 정상 정책·캐시를 덮어쓰지 않고 조회 실패 처리 |
| 정상 재조회 성공 | 최신 정책으로 교체. 최소 버전 하향 복구도 즉시 반영 |
| 캐시 만료/손상/플랫폼·환경 불일치 | 해당 캐시는 사용하지 않고 네트워크 결과 또는 무캐시 실패 규칙 적용 |

권장 초깃값은 마지막 정상 조회 후 24시간이다. 서버가 정한 수치가 아니며, 구현 시 상수·문서·테스트에 함께 고정한다. 무기한 필수 차단을 막기 위한 제한으로, 프로세스 재시작 후 만료된 캐시만으로는 차단하지 않는다. 같은 세션에서 이미 확인한 필수 판단은 재조회 실패로 해제하지 않는다. 만료 후 오프라인 진입 허용이라는 절충을 테스트 결과에 명시한다.

저장 항목은 전체 정책, 조회 시각, 스키마 버전, 플랫폼, 서버 환경이다. 판단 결과만 저장하지 않는다. 앱 업데이트 뒤에는 새 설치 버전으로 재계산한다. 검증이 끝난 정상 정책만 즉시 저장하고, 실패 응답과 Fake 정책은 운영 캐시에 쓰지 않는다. 시각이 미래이거나 만료 계산이 불가능하면 사용하지 않는다. 저장 실패는 정상 네트워크 판단을 실패로 되돌리지 않는다.

`#1031`이 저장/캐시 기반을 제공한다면 인터페이스를 맞춰 재사용한다. 해당 브랜치를 선행 의존성으로 만들거나 버전 정책 원본을 원격 설정 API로 바꾸지는 않는다. 영속 캐시 도입은 iOS에도 영향을 주므로 플랫폼 구현과 회귀 검증을 함께 제공하거나, Android에만 주입되는 범위를 명시한다.

## 5. Play In-App Updates 연결

다음은 [Android 공식 구현 문서](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java)를 확인한 적용 항목이다. 공식 안내 의존성은 `app-update:2.1.0`, Kotlin 확장은 `app-update-ktx:2.1.0`이다. 프로젝트 카탈로그로 관리한다.

- 사용자 업데이트 선택 시 가용 여부와 해당 방식 허용 여부를 확인한다. 권장은 Flexible, 필수는 Immediate를 선택한다. 재시도는 새 `AppUpdateInfo`로 수행한다.
- Flexible은 다운로드 상태를 관찰하고 완료 시 설치/재시작 버튼을 제공한다. 사용자 선택으로 `completeUpdate()`를 호출한다.
- Immediate 취소·실패 시 공통 필수 차단을 유지하고 설명·재시도를 제공한다.
- 복귀/재실행 때 `DOWNLOADED`는 설치 안내를 복원하고, `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`는 Immediate를 재개한다.
- Activity Result launcher를 생명주기에 맞게 등록한다. 설치 listener는 필요 시 등록하고 해제한다.

| 실패 상황 | 사용자 복구 경로 |
| --- | --- |
| Play 미지원/가용 업데이트 없음/해당 방식 불허/조회·시작 실패 | 서버 `storeUrl`로 이동하는 대체 버튼 제공 |
| 스토어 이동 실패 | 실패 안내와 스토어 재시도·정책 재조회 제공 |
| 필수 업데이트 취소·실패 | 주요 기능 차단 유지, 명시적 업데이트 재시도 |
| 권장 업데이트 취소·실패 | 나중에 선택으로 앱 사용 가능 |

Play의 업데이트 없음은 서버의 지원 가능 판단이 아니다. 서버가 필수라고 판단하면 스토어 대체 경로에서도 차단을 유지한다. 사용자에게 복구 액션을 제공하되 자동으로 Play UI를 반복 실행하지 않는다. Play의 versionCode와 서버 비교용 versionName을 혼동하지 않는다.

## 6. 검증 계획

### 자동 검증

기존 `AppVersionPolicyTest`, `AppVersionApiTest`, `AppStartupViewModelTest`, `AppStartupPresentationTest`를 기준으로 확장한다.

- 설치 버전=최소, =최신, 최신 초과, `1.9.0/1.10.0`, 잘못된 형식·순서·URL.
- 첫 조회 실패, 10초 타임아웃, 취소 전파, 필수 판단 후 재조회 실패, 정상 정책 하향 복구.
- 캐시 재시작 복구, 24시간 경계, 손상·미래 시각·환경 분리, 업데이트된 설치 버전으로 재평가.
- FakeAppUpdateManager 또는 주입 가능한 Fake로 Flexible 진행·취소·완료, Immediate 취소·실패·재개, 중복 시작 방지.
- 설치 상태가 필수 판단을 해제하지 않는지, 권장 나중에 선택 후 주요 기능 사용 가능한지.
- iOS의 기존 스토어 이동·차단·재시도 회귀, 회전과 프로세스 재시작을 각각 검증.

`AN/Pheeeew`에서 현재 CI와 같은 검증을 실행한다. 새 Android 어댑터 테스트를 추가하면 해당 테스트 태스크도 실행한다.

```sh
./gradlew ktlintCheck
./gradlew :shared:testAndroidHostTest :developerTools:testAndroidHostTest :androidApp:lintDebug :androidApp:assembleDebug
```

SDK/JDK와 로컬 설정은 해당 워크트리에서 준비한다. 비밀 설정을 가이드나 커밋에 넣지 않는다. iOS 검증은 Xcode 환경에서 별도로 수행하고 실행한 테스트/빌드 명령과 결과를 기록한다.

### 서버 정책 반영 확인

개발/테스트 환경에서 기존 BE 문서의 조회·변경·복구 절차를 사용한다. 변경 전 값과 변경자·사유를 기록한 뒤 같은 설치 앱에서 최신 버전만 올려 권장 판단, 최소 버전까지 올려 필수 판단, 원복 후 정상 판단을 확인한다. 시작·전경 복귀·명시적 재시도를 구분해 증거를 남긴다. 운영 DB 값은 이 구현 준비 과정에서 변경하지 않는다.

### 실제 Play 검증

[공식 테스트 절차](https://developer.android.com/guide/playcore/in-app-updates/test)에 따라 내부 앱 공유로 검증한다. 업데이트 기능이 포함된 빌드 A를 공유 링크로 설치하고 더 높은 versionCode의 B를 업로드한다. B 링크를 열되 그 화면에서 설치하지 않은 상태로 A를 실행한다. 패키지·서명 일치와 테스트 계정의 Play 앱 보유 조건을 확인한다. 내부 앱 공유는 `inAppUpdatePriority`를 지원하지 않는다.

1. A에서 권장 업데이트를 미뤄 주요 기능 사용을 확인한다.
2. Flexible 다운로드·완료 안내·사용자 설치 선택·B 재시작을 확인한다.
3. A를 다시 준비해 필수 Immediate의 취소·실패 후 차단과 재시도를 확인한다.
4. 홈 이동·앱 복귀·회전·프로세스 재시작 중 진행 상태 복구를 확인한다.
5. B 설치 후 정상 진입과 실제 설치 버전·서버 정책 재평가를 확인한다.

A/B의 versionName·versionCode, 커밋, applicationId, 서명 식별자, 배포 경로, 기기/OS, 테스트 계정 조건, 서버 정책 전후, 결과와 화면 캡처/영상을 기록한다. 계정 비밀이나 서명 키는 기록하지 않는다. Fake 통과와 실제 Play 통과를 별도 표기하며, #1019 배포 준비가 미완료라면 실제 검증은 미완료로 남긴다.

## 7. 다른 세션과의 통합

`#1031`과 `App.kt`, `AppStartupViewModel.kt`, Graph, 개발자 도구, 저장소 구성이 겹칠 수 있다. 새 워크트리에서만 작업하고 해당 세션의 미커밋 파일을 복사하지 않는다. 통합 전에 원격 `an/dev`와 #1031 머지 상태를 확인해 중복 캐시·중복 전경 조회를 정리한다. Debug 시나리오 주입은 Release에 포함되지 않도록 기존 developerTools 격리 규칙을 따른다.

## 8. 근거와 완료 기록

- 서버 정책 원본: [BE 버전 운영 문서](../../../BE/docs/client/app-version.md).
- 요구사항: [#1032](https://github.com/woowacourse-teams/2026-pheeeew/issues/1032), 연계: #1031, #1019.
- Play API와 실제 테스트: 위 Android 공식 문서. 확인일 2026-10-29.
- 지식베이스: [Android Activity 생명주기와 상태 보존](https://github.com/katie0109/knowledge-base/blob/main/technologies/platforms/android/android-lifecycle-and-state.md). 회전·프로세스 종료 구분과 상태 수명에 따른 저장 위치 선정에 적용했다. Play API 사용법은 공식 문서를 기준으로 했다.

이 절은 작성 당시 준비 상태를 기록한 것이다. 이후 구현 결과는 아래와 `measurements/app-update-1032/README.md`를 따른다.

## 9. 구현 반영

- 공통 `DefaultAppUpdateController`와 Activity 수명의 Android Play 어댑터를 추가했다. 정책 판단과 설치 상태는 분리한다.
- Android/iOS 마지막 정상 정책 캐시를 추가했다. 24시간 만료, 환경·플랫폼·스키마 검증, 실패 시 세션 판단 유지, 정상 하향 복구를 적용했다.
- 권장 안내의 나중에 선택은 최신 버전 정책에 연결한다. 필수 업데이트의 기존 흰색 차단 화면을 유지하며 취소·실패 뒤 재시도와 스토어 경로를 제공한다.
- `-PappUpdateTestBuild=true`로만 `com.pheeeew.updatetest`와 테스트 앱 이름을 사용한다. 일반 Debug/Release 식별자·버전은 유지한다. 테스트 패키지는 Firebase 프로젝트에 등록되지 않았으므로 Google Services 처리를 생략하고 토큰 요청을 실행하지 않는다.
- 테스트 앱 A는 기본 `2.5.0 / 103201`이다. B는 `-PappUpdateTestVersionName=2.6.0 -PappUpdateTestVersionCode=103202`로 빌드한다.
- 테스트 패키지의 Debug 전용 `AppUpdateTestActivity`에서 현재/권장/필수/조회 실패와 Play 실제/Fake 취소/실패/다운로드/불가를 선택한다. Fake 정책은 실제 정책 캐시에 저장되지 않는다.
- Google Play 의존성의 구형 Fragment와 Activity Result의 충돌은 Fragment 1.8.9 명시로 해결했다.
- 실제 Play 검증에는 업데이트 기능이 포함된 A/B의 Play 설치 조건이 필요하다. USB 설치 및 Fake 결과로 Play 검증을 완료 처리하지 않는다.
