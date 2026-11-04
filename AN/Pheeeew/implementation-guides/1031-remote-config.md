# #1031 원격 설정 클라이언트 구현 가이드

작성일: 2026-10-29 KST
기준: 최신 `an/dev`, `f6c929412e25616e081609ff3769fee26519bb0c`
브랜치: `an/feat/1031-remote-config`
상태: 클라이언트 코드와 Android 호스트 자동 검증 완료. Android Debug 빌드·iOS 코드 컴파일·스타일·개발자 도구 분리 검사 통과. Sentry 테스트 링크를 보완하여 iOS shared 800개·developerTools 31개 테스트도 통과. 실제 서버 변경 실험·앱 캡처·영상은 후속 작업이다.

구현 및 검증 이력: [클라이언트 검증 기록](../measurements/remote-config/1031/client-checks.md). 아래 단계별 항목은 구현/검증 절차이며 개별 실행 범위와 남은 항목은 검증 기록을 기준으로 확인한다.

관련 이슈: [#1031](https://github.com/woowacourse-teams/2026-pheeeew/issues/1031), 버전 업데이트는 [#1032](https://github.com/woowacourse-teams/2026-pheeeew/issues/1032).

## 목표와 역할

앱 재배포 없이 일일 프레스 순위를 중단·복구하고 지도 공지를 변경한다. 설정을 못 받아도 앱이 동작해야 한다. 구현 완료는 코드와 테스트만으로 판단하지 않는다. 실제 서버 값을 바꾼 동일 설치 앱의 화면 캡처 또는 영상을 확보하고 이슈 코멘트에 연결한다.

백엔드는 공개 조회 API, 플랫폼별 초기 데이터, SQL 변경·복구와 revision 갱신을 담당한다. 클라이언트는 기본값·영속 캐시·갱신·차단 UI·공지·개발자 도구와 Android/iOS 검증을 담당한다. 서버 API 배포 전에는 fixture로 개발할 수 있지만 실제 반영 검증 완료로 기록하지 않는다.

기존 버전 API는 재사용 선례이며 새 원격 설정에 버전 값을 중복하지 않는다. 일반 순위·감정 기록·프레스 입력·사용자의 알림 설정은 이번 플래그와 독립적이다. 최신 an/dev에서는 사용 가이드 화면이 제거되어 있다. 예전 가이드 초기화 동작을 전제로 구현하지 않는다.

## 기존 코드와 변경 지점

아래 경로는 `AN/Pheeeew` 기준이다. 새 파일 이름은 제안이며 구현하면서 기존 규칙에 맞춘다.

| 위치 | 현재 구조와 구현 방향 |
| --- | --- |
| `shared/src/commonMain/kotlin/com/pheeeew/data/remote/api/AppVersionApi.kt` | ApiRequestExecutor 공개 조회 선례. AppConfigApi 추가 |
| `shared/src/commonMain/kotlin/com/pheeeew/core/network/ApiRequestExecutor.kt` | 공통 요청·오류 경계 사용. 설정용 별도 HTTP 클라이언트를 만들지 않음 |
| `shared/src/commonMain/kotlin/com/pheeeew/core/di/ApiBindings.kt` 및 `ApiDependencies.kt` | 앱 서비스 수명의 Repository·설정 상태 제공 |
| `shared/src/commonMain/kotlin/com/pheeeew/core/di/AppUiSession.kt` | UI 세션 재생성과 설정 저장/조회 소유권 구분 |
| `shared/src/commonMain/kotlin/com/pheeeew/App.kt` | 전경 수명주기·알림 진입·DailyPressRankingRootDestination·지도 표시 연결 |
| `shared/src/commonMain/kotlin/com/pheeeew/feature/screens/ranking/press/DailyPressRankingViewModel.kt` | init 조회·복귀·알림·재시도 요청을 모두 차단 상태와 연결 |
| `shared/src/commonMain/kotlin/com/pheeeew/feature/screens/ranking/press/PressRankingScreen.kt` | DailyPressRankingRoute 생성 전 점검 화면 분기 |
| `shared/src/androidMain/kotlin/com/pheeeew/core/di/AndroidAppGraph.kt` | 플랫폼 저장 어댑터 주입 |
| `shared/src/iosMain/kotlin/com/pheeeew/core/di/IosAppGraph.kt` | 플랫폼 저장 어댑터 주입 |
| `developerTools/src/commonMain/kotlin/com/pheeeew/developertools/DeveloperToolsHost.kt` | 기존 Debug 호스트 확장. Release가 developerTools에 의존하지 않게 유지 |

## API 계약과 구현 전 확인

```http
GET /api/v2/app/config?platform=android
```

```json
{
  "revision": 3,
  "dailyPressRankingEnabled": true,
  "noticeMessage": "",
  "updatedAt": "2026-10-29T03:00:00Z"
}
```

- android/ios 플랫폼, 인증·기기 등록 전 공개 조회, 최상위 JSON.
- 필수 필드: 양의 정수 revision, Boolean dailyPressRankingEnabled, 최대 200자 noticeMessage, UTC ISO 8601 updatedAt.
- 공지 200자의 계산 기준은 서버와 확정한다. Kotlin String.length와 코드 포인트 수를 혼용하지 않는다.
- 잘못된 플랫폼 400, 설정 미등록 404, 나머지는 기존 오류 계약 사용. 운영 전 두 플랫폼 초기 데이터 등록 필요.
- Cache-Control: no-store로 HTTP 캐시 재사용을 막고 앱은 명시적으로 검증한 설정 스냅샷을 보존한다.
- 변경·복구 때 revision 증가 및 updatedAt 갱신. 초기 구현은 서버 DB 최신 값을 조회한다.
- 필수 필드가 없거나 타입/값이 잘못되면 응답 전체를 거부한다. DTO 기본 인자로 서버 누락을 정상 응답처럼 바꾸지 않는다. 알 수 없는 추가 필드는 무시한다.
- 동일 revision의 다른 내용은 계약 위반으로 거부한다. 같은 환경에서 더 낮은 revision도 거부한다. 복구는 더 높은 revision으로 수행한다.
- 공지는 일반 텍스트로 렌더링한다. 비밀 키·토큰·권한 정책·HTML·실행 코드를 담지 않는다.

위 계약은 백엔드에 전달한 설계다. 실제 배포 응답·공지 길이 기준·테스트 환경과 일치하는지 확인 후 구현한다. 차이는 이슈 코멘트에 남긴다.

## 1단계 — 상태와 기본값

- [ ] `domain/model/config/AppConfig.kt`: 활성화 Boolean과 공지 문자열 정의.
- [ ] 서버 스냅샷에 revision·updatedAt, 앱 상태에 출처·lastFetchSucceededAt·lastAppliedAt·최근 오류·조회 중 여부를 별도로 둔다.
- [ ] 기본값은 활성화 true, 공지 빈 문자열. revision/서버 변경 시각은 없으며 수신한 척 생성하지 않는다.
- [ ] 기본값 true는 현재 제공 기능을 유지하기 위한 결정이다. 위험한 신규 기능은 false로 시작하는 별도 정책을 검토한다.
- [ ] 정상 캐시 false를 받았으면 오프라인·재시작에서도 false 유지. 오래됐다는 이유로 true로 복구하지 않는다.

원격값은 일일 순위 활성화·공지·서버 메타데이터다. 60초 주기·3초 제한·기본값·점검 화면 문구·API 주소와 인증 정책은 앱/빌드 설정에 둔다. 운영 설정으로 요청 주기를 무제한 낮추는 구조는 이번에 만들지 않는다.

## 2단계 — 공개 조회와 영속 캐시

- [ ] `data/remote/dto/AppConfigResponseDto.kt`, `data/remote/api/AppConfigApi.kt`와 검증·매핑 추가.
- [ ] ApiRequest의 `AuthenticationRequirement.NONE`, `RequestKind.READ`, platform 쿼리 사용.
- [ ] AppConfigRepository와 AppConfigStore 경계를 추가하고 앱 서비스 수명으로 제공.
- [ ] Android는 기존 Preferences 계열, iOS는 NSUserDefaults 선례에 맞춘 저장 어댑터 구현. 개별 필드 대신 버전이 있는 스냅샷을 한 번에 교체한다.
- [ ] 캐시 키는 환경(API origin 등 비밀정보 없는 식별자)·플랫폼·캐시 스키마 버전을 포함한다. 토큰·기기 식별자는 키에 넣지 않는다.
- [ ] 초기 로드: 정상 캐시 → 없거나 손상됐으면 기본값. 캐시 로드와 네트워크 완료의 순서를 보장해 늦은 캐시가 새 서버값을 덮지 않게 한다.
- [ ] 실패는 현재 설정을 유지하고 오류 상태만 갱신한다. CancellationException은 전파하며 앱 종료를 통신 실패로 기록하지 않는다.
- [ ] 저장 실패 정책을 명시한다. 제안: 검증된 값은 메모리에 적용하되 영속화 실패를 별도 표시하고, 재시작 유지 검증은 성공으로 기록하지 않는다.
- [ ] 환경 전환·폐기된 요청의 응답은 세대/키 검사로 적용과 저장을 모두 거부한다.

정상 스냅샷과 임시 덮어쓰기는 분리한다. 유효 우선순위는 덮어쓰기 > 서버 정상값/정상 캐시 > 기본값이다.

## 3단계 — 갱신과 적용

| 상황 | 구현 정책 |
| --- | --- |
| 프로세스 시작 | 캐시/기본값으로 UI 시작, 비동기 조회. 기존 스플래시 대기 증가 없음 |
| 전경 복귀 | 마지막 요청 시도 시작 후 60초가 지났으면 조회 |
| 지속 전경 | 마지막 시도 기준 60초 간격으로 조회 |
| 백그라운드 | 주기 작업 중단, 조회 중 작업 취소 및 취소 결과 미적용 |
| 요청 예산 | 요청 전체 3초. 공통 실행기의 내부 재시도도 예산 안에 포함 |
| 요청 실패 | 다음 주기에 재시도. 즉시 반복 요청 없음 |
| 수동 조회 | 주기 제한 우회, 이미 조회 중이면 기존 요청 공유 |

- [ ] Mutex/공유 Job 등으로 단일 조회만 실행하고 완료·실패·취소 시 정리한다.
- [ ] 주기 판단은 단조 시계, 제출용 시각은 UTC/KST 절대 시계로 구분한다.
- [ ] Repository는 캐시/상태, 하나의 전경 소유자는 타이머를 담당한다. Compose 재구성·화면 이동·UI 세션 초기화로 타이머가 중복되지 않게 한다.
- [ ] 수신 성공과 적용을 구분한다. 같은 revision 재수신은 수신 시각만 갱신하며 기능 변화·적용 시각을 가짜로 생성하지 않는다.
- [ ] 기능 플래그는 검증 후 즉시 적용. 장애 화면 중단이라는 이유를 문서에 남긴다.
- [ ] 공지는 메인 지도 배너에서 갱신한다. 작성/녹음 중에는 팝업을 띄우지 않고 지도 재진입 때 최신값을 표시한다. 데이터 적용 시각과 화면에 보인 시각은 다를 수 있다.

온라인·전경·요청 성공 시 목표는 변경 후 대략 60초 + 요청 시간이다. 수동 조회 결과와 자동 반영 결과를 구분한다. 오프라인·백그라운드·서버 장애에는 보장 시간이 없다. 지속 전경 사용자당 대략 분당 1회 요청이며 실제 주기와 부하는 검증한다.

## 4단계 — 일일 순위 차단과 공지

- [ ] route를 감싸는 gate에서 false면 DailyPressRankingRoute/ViewModel을 생성하지 않는다. 알림 직접 진입·복구된 navigation 경로도 동일 gate를 통과시킨다.
- [ ] 점검 화면: “일일 순위를 잠시 점검하고 있어요.”, 뒤로 가기, 설정 다시 확인. 뒤로 갈 대상이 없으면 안전한 메인 지도로 이동한다.
- [ ] 이미 표시 중 false 적용: 진행 중 조회 취소, 기존 화면 점검 화면으로 교체.
- [ ] ViewModel의 init·복귀·알림·재시도 공통 요청 경계도 검사한다. 취소만 믿지 말고 요청 세대·설정 활성화 상태를 응답 수락 직전에 검사한다.
- [ ] false → true 복구 후 현재 화면에서 다시 정상 조회 가능하게 한다.
- [ ] 공지 배너를 지도 상단의 기존 오버레이·시스템 인셋과 겹치지 않게 배치한다. 빈 문자열이면 숨기고 긴 공지·큰 글꼴·좁은 화면 확인.
- [ ] 일반 순위·프레스 입력·감정 작성·알림 사용자 설정의 회귀 확인.

## 5단계 — 개발자 도구와 증거 수집 지원

- [ ] 실제 API 환경·플랫폼·앱 버전·revision·서버값·적용값·출처 표시.
- [ ] 서버 변경 시각, 마지막 수신 성공 시각, 데이터 적용 시각, 최근 오류/저장 오류 표시.
- [ ] 다시 받기, 플래그/공지 임시 덮어쓰기, 해제 추가. 메모리 한정으로 프로세스 재시작 시 제거.
- [ ] 덮어쓰기 중에도 서버 수신·정상 캐시 저장 유지. 해제하면 최신 정상값으로 복구.
- [ ] 증거 수집 전 덮어쓰기 없음 확인. 초기화 버튼은 설정 캐시만 지우며 다른 데이터 초기화와 구분.
- [ ] 필요하면 진단 이벤트에 revision·조회 사유·결과·소요 시간만 남기고 토큰·개인정보·공지 원문은 기록하지 않음.

## 6단계 — 자동 검증

가상 시계·fake 저장소·MockEngine으로 의미 있는 경계를 확인한다. 실제 API 없이도 진행할 수 있다.

- 기본값/캐시 복원·손상·환경 분리·저장 실패.
- 400/404/5xx·타임아웃·누락/잘못된 타입·공지 제한·추가 필드.
- 낮은 revision·동일 revision 상이 내용 거부, 동일값 재수신 시 시각 의미 유지.
- 60초 전후·전경 재진입·백그라운드 취소·동시 수동/자동 조회의 중복 방지.
- 지연된 캐시·이전 환경 응답·차단 이전 순위 응답이 현재 상태를 덮지 않음.
- 덮어쓰기 중 정상값 저장·해제·프로세스 재시작 시 복구.
- UI gate의 알림 진입·표시 중 차단·복구, 작성 데이터 유지.

기존 Gradle 작업명을 확인하고 변경 범위에 맞게 실행한다. 아래는 실행할 명령이며 실행 결과가 아니다.

```sh
cd AN/Pheeeew
./gradlew :shared:testAndroidHostTest
./gradlew :developerTools:allTests
./gradlew :androidApp:assembleDebug
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:iosSimulatorArm64Test :developerTools:iosSimulatorArm64Test
```

iOS 테스트는 루트 prepareIosTestFrameworks 작업이 앱과 같은 Sentry simulator SDK를 체크섬 검증 후 준비한다. 앱 Sentry 버전을 올리면 준비 스크립트의 버전/체크섬도 공식 Package.swift와 맞춘다. 자세한 내용은 [iOS 링크 해결 기록](../measurements/remote-config/1031/ios-sentry-link-fix.md)을 참고한다. developerTools allTests는 여러 플랫폼을 실행할 수 있다. 실제 구성에 맞는 대상 작업을 먼저 확인한다. iOS 호스트/SDK 제약으로 실행할 수 없다면 이유와 남은 검증을 기록한다. UI는 Android 기기/에뮬레이터와 iOS 시뮬레이터 또는 실기기에서 별도 확인한다.

## 7단계 — 실제 앱 캡처·영상과 서버 실험

실제 서버 API 준비 후 백엔드와 테스트 환경에서 수행한다. 앱을 다시 빌드하거나 설치하지 않은 채 서버 값만 변경하는 과정을 남긴다. 운영 설정은 실험용으로 임의 변경하지 않는다.

| ID | 시나리오 | 필요한 실제 화면 증거 |
| --- | --- | --- |
| RC-01 | 캐시 없는 첫 실행 + 오프라인 | 기본값 출처/값, 앱 정상 진입. 캐시만 초기화했는지 기록 |
| RC-02 | 서버 true → false, 자동 갱신 대기 | 변경 전 일일 순위 → 서버 변경 시각/revision → 자동 수신 → 점검 화면 |
| RC-03 | false에서 순위 알림 클릭 | 실제 테스트 알림 클릭과 점검 화면, 순위 조회 미발생 로그 보조 |
| RC-04 | false 수신 후 오프라인 재시작 | 네트워크 OFF, 프로세스 재시작, 캐시 false, 차단 유지 |
| RC-05 | 서버 false → true | 새 revision 수신과 정상 순위 재진입 |
| RC-06 | 공지 A → B → 빈 문자열 | 같은 설치 앱의 지도 배너 변경·숨김. 작성 화면 간섭 없음 |
| RC-07 | 서버 오류·타임아웃·형식 오류 | 마지막 정상값 유지와 오류 표시. fixture 주입이면 fixture라고 표시 |

- [ ] Android·iOS에서 결과를 각각 남긴다. 한 플랫폼 검증을 양쪽 완료로 쓰지 않는다.
- [ ] 영상에는 변경 전 상태·앱 버전·환경·덮어쓰기 없음·변경 후 상태가 식별되어야 한다.
- [ ] 자동 지연 실험 중 다시 받기를 누르지 않는다. 수동 갱신 영상은 별도 이름으로 보관한다.
- [ ] 변경 시각(T0), 수신 성공(T1), 데이터 적용(T2), 실제 UI 표시(T3)를 기록한다. T1-T0와 T3-T0를 따로 계산한다.
- [ ] 서버와 기기의 시계 오차를 확인한다. 동기화 확인이 안 되면 숫자는 참고 측정이라고 밝히고 같은 녹화의 경과 시간을 보조 근거로 쓴다.
- [ ] 실제 앱 화면 캡처/원본 영상을 확보한다. 로그를 이미지로 만든 것을 실제 앱 캡처로 대체하지 않는다.
- [ ] 가급적 ON→OFF→ON은 연속 영상, 오프라인 재시작은 별도 영상, 공지 변경은 전후 캡처로 남긴다.
- [ ] 민감정보 없는 화면·정제한 응답/로그만 게시한다. 원본 경로와 이슈 첨부 링크를 함께 기록한다.

증거 파일명 예: `RC-02-android-live-auto-off.mp4`, `RC-04-ios-live-offline-cache.mp4`, `RC-06-android-notice-after.png`. 저장 위치는 `measurements/remote-config/1031/`로 제안하되 큰 영상은 이슈 첨부를 사용하고 Git에 강제 추가하지 않는다.

## 이슈 코멘트 기록 운영

각 단계가 끝났을 때 #1031에 새 코멘트를 남긴다. 설계·fixture 테스트·실제 서버 검증을 구분하고 결과가 없는 칸은 미실행으로 남긴다. 설계/브랜치 준비, 1·2단계, 3~5단계 진행 코멘트를 게시했으며 최종 자동 검증과 제출용 미완료 항목도 별도로 기록한다. 아래 템플릿을 사용하면 제출 항목을 작업 중 축적할 수 있다.

### 코멘트 A — 구현 선택·기본값·적용 정책

```markdown
### 원격 설정 기록 A — 설계 및 구현 정책
- 상태: 설계 / 구현 중 / 코드 반영 완료 중 선택
- 기준 커밋 / 브랜치 / PR:
- 수단: 팀 서버 GET /api/v2/app/config + KMP 공통 Repository
- 선정 이유: 기존 서버·공개 조회·플랫폼 저장 구조 활용, Android/iOS 정책 공유
- 원격값: dailyPressRankingEnabled, noticeMessage
- 기본값: true / 빈 문자열. 기존 기능 유지 및 공지 없음
- 고정값: 60초 주기, 총 3초 요청 제한, 점검 문구, 인증/환경 설정
- 수신: 시작·전경 복귀·전경 60초 주기. 백그라운드 중단
- 적용: 기능 플래그 즉시, 공지는 지도 배너. 작성 화면 팝업 없음
- 실패: 정상 캐시 유지, 캐시 없으면 기본값. 오래된 false 자동 해제 없음
- 목표 지연: 온라인/전경/성공 조건 약 60초 + 요청 시간. 실측 아님
- 실제 구현과 설계 차이 / 이유:
- 근거 코드 / 테스트 / 남은 작업:
```

### 코멘트 B — 구현·자동 검증 진행

```markdown
### 원격 설정 기록 B — [완료한 단계명]
- 실행일(KST) / 커밋 / 환경(fixture 또는 live):
- 변경한 동작과 근거 파일:
- 명령 / 결과 / 검증한 경계:
- 기본값·캐시·실패·주기·취소 검증 결과:
- 화면 캡처/영상 링크(있으면):
- 한계 / 미실행 / 다음 작업:
※ fixture 응답이나 임시 덮어쓰기는 실제 서버 변경 증거가 아님
```

### 코멘트 C — 실제 변경·반영과 화면 증거

```markdown
### 원격 설정 기록 C — RC-__ 실제 반영 실험
- 실행일 / 플랫폼 / OS / 기기:
- 앱 버전·빌드 / 커밋 / API 환경:
- 같은 설치 앱 유지: 예/아니오, 임시 덮어쓰기: 없음/있음
- 갱신 방식: 자동/수동
- 변경 전 revision·값 → 변경 후 revision·값:
- T0 서버 변경 시각(UTC/KST):
- T1 수신 성공 시각 / T2 데이터 적용 시각 / T3 화면 표시 시각:
- 변경→수신 / 변경→화면 반영 시간:
- 시계 동기화 확인 방법 / 오차·측정 한계:
- 실제 화면 결과:
- 원본 영상·캡처 경로 / 이슈 첨부 링크 / 영상 타임스탬프:
- 정제한 응답·로그 링크:
- 결과: 통과/실패/미실행
- 실패 원인·재실험 / 남은 한계:
```

### 코멘트 D — 제출용 증거 색인

```markdown
### 원격 설정 제출 기록
| 제출 항목 | 확정 내용 | 근거 코멘트/캡처/영상 |
| --- | --- | --- |
| 구현 수단과 고른 이유 | | |
| 기본값 | | |
| 설정 수신·적용 시점 | | |
| 변경부터 반영까지 시간과 측정 조건 | | |
| 실제 서버 값 변경·앱 반영 기록 | | |
| 기본값·오프라인 캐시·서버 실패 동작 | | |
| 실제 앱 화면 캡처 또는 영상 | | |
- Android 결과 / iOS 결과:
- 미완료와 한계:
```

## 완료 기준

백엔드 계약 대조, 클라이언트 구현, 필요한 자동 검증, 실제 서버 변경 실험, 기본값/캐시 실패 실험, **실제 앱 화면 캡처 또는 영상**, 이슈의 제출용 근거 링크가 모두 있어야 완료한다. 가이드 작성이나 빌드 성공만으로 이슈를 종료하지 않는다.

## 참고와 적용 근거

- [Firebase 로딩 전략](https://firebase.google.com/docs/remote-config/loading): 기본값과 UI 변화에 따른 수신/적용 분리. 팀 서버에도 원리를 적용하며 Firebase SDK는 추가하지 않는다.
- [지식베이스: HTTP 캐시와 앱 캐시](https://github.com/katie0109/knowledge-base/blob/main/foundations/networking/cache-keys-and-invalidation.md): 환경/플랫폼별 캐시 키와 갱신·보존 분리. 문서 초안의 원리를 적용하며 수치는 이 이슈 결정이다.
- [지식베이스: 비동기 상태와 오래된 응답](https://github.com/katie0109/knowledge-base/blob/main/foundations/state-and-concurrency/async-state-and-ownership.md): 취소와 별개로 세대/환경 검사, 수신값과 현재 UI 상태의 소유권 구분.


## 리뷰 후 저장·표시 보완

- 응답 저장 직전에 취소를 확인한다. 저장 도중 취소되면 취소 불가능한 정리 구간에서 이전 캐시를 복원한다. 복원 실패 시 캐시 삭제를 시도하고 저장 오류를 기록한다. 삭제까지 실패한 저장 장치 오류에서는 디스크 상태를 보장하지 못하며 진단 오류를 남긴다. 초기화는 진행 중 요청의 취소·복원 완료를 기다린다.
- iOS NSUserDefaults의 쓰기 반환은 디스크 기록 완료 확인이 아니다. 일반적인 저장 후 재실행과 달리, 기록 직후 강제 종료·전원 손실 구간의 최신 스냅샷 내구성은 보장하지 않는다. RC-04는 기록이 보존된 정상 재실행과 이 강제 종료 구간을 구분해 검증해야 하며 후자는 아직 미검증이다. 정상 캐시의 오프라인 사용 정책은 유지한다. synchronize()는 내구성 보장 수단으로 추가하지 않는다.
- 원격 공지는 최대 높이 240dp 영역에서 스크롤하여 전체 내용을 읽을 수 있다. 좁은 화면·큰 글꼴의 실제 UI 검증은 별도로 수행한다.
- 공지 길이 검사는 유효한 UTF-16만 허용하며 surrogate pair를 한 코드 포인트로 센다. 잘못된 surrogate는 응답과 임시 설정 모두에서 거부한다.
