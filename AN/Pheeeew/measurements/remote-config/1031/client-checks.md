# #1031 클라이언트 구현·자동 검증 기록

작성일: 2026-10-29 KST
기준 커밋: `f6c929412e25616e081609ff3769fee26519bb0c`
브랜치: `an/feat/1031-remote-config`
검증은 기준 커밋 위 작업 트리에서 실행했다. 최종 변경 커밋과 리뷰 범위는 이 브랜치의 PR을 기준으로 확인한다.

## 구현한 범위

- 팀 서버 공개 설정 API와 필수 필드·JSON 원본 타입·revision·UTC 시각·공지 길이 검증.
- 기본값, Android Preferences/iOS NSUserDefaults 캐시, 환경·플랫폼별 저장 공간, 정상값 유지, 저장 실패 진단.
- 최초 캐시 로드 이전 일일 순위 진입 보류, 전경 60초 갱신, 총 3초 제한, 중복 조회 공유와 백그라운드 취소.
- 일일 순위 route gate, 직접 알림 진입 차단, 진행 중 요청 취소와 결과 수락 검사, 복구 후 재조회.
- 지도 기존 안내 영역의 공지. 오류 안내를 우선하며 작성/녹음 중 원격 공지는 숨김.
- Debug 개발자 도구의 실제/임시값·출처·시각·오류·수동 갱신·임시 덮어쓰기/해제·설정 캐시만 초기화.

공지 길이는 우선 Unicode 코드 포인트 200개로 구현했다. 백엔드와 실제 길이 계산 계약은 연동 전에 대조한다. 캐시 환경은 기존 API 설정의 dev/prod 분류를 사용하며 Repository 인스턴스 안에서는 환경이 고정된다. 기존 그래프 종료 시 소유 scope가 취소된다.

## 실행한 검증

`AN/Pheeeew`에서 실행했다. 실제 서버 요청이 없는 fixture·가상 시계·호스트 테스트이며 실제 앱 화면 검증과 다르다.

```sh
./gradlew :shared:testAndroidHostTest :developerTools:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64 :developerTools:compileKotlinIosSimulatorArm64 :shared:verifyDeveloperToolsIsolation :shared:ktlintCheck :developerTools:ktlintCheck :androidApp:ktlintCheck
```

결과: **BUILD SUCCESSFUL**. 최종 명령에서 일부 작업은 현재 입력에 대한 이전 성공 결과를 재사용했다(UP-TO-DATE).

| 검증 | 결과 |
| --- | --- |
| shared Android 호스트 테스트 | 794개, 실패/오류/건너뜀 0 |
| developerTools Android 호스트 테스트 | 31개, 실패/오류/건너뜀 0 |
| 새 원격 설정 테스트 | 12개, 모두 통과. 위 shared 집계에 포함 |
| Android Debug APK | assembleDebug 성공 |
| shared·developerTools iOS Simulator 코드 | 컴파일 성공 |
| iOS Simulator 테스트 — 후속 링크 보완 후 | shared 800개·developerTools 31개 통과 |
| Kotlin 스타일 검사 | shared·developerTools·androidApp 통과 |
| 개발자 도구 분리 | iOS 제품 그래프 및 Android Release 그래프에서 developerTools=false |

원문: [최종 검증 로그](client-check.txt), [JUnit XML에서 추출한 테스트별 집계](android-test-summary.txt).

새 테스트는 기본값/손상 캐시, 정상 캐시 재시작 복원(가짜 저장소), 실패 유지, 낮은/충돌 revision 거부, 임시값 비영속성, 동시 갱신 공유, 60초 경계/백그라운드 취소, 타임아웃/저장 실패, 캐시만 초기화, 수신/적용 시각 분리, 실패 후 주기 제한, 공개 API/타입·오류 계약, 비활성화 전후 순위 네트워크 요청 경계를 확인한다. 실제 Android/iOS 저장 장치와 UI 상태 복원은 별도 기기 검증이 필요하다.

## 발견한 문제와 조치

- JSON 디코더가 문자열 `"false"`를 Boolean으로 허용했다. 설정 API에서 JsonObject 원본 필드 타입을 검사하도록 보완했고 계약 테스트가 통과했다.
- 순위 요청 경계 테스트의 인증 fixture가 누락되어 요청이 엔진에 도달하지 않았다. 테스트 토큰 provider를 추가한 후 요청 차단·진행 중 취소를 확인했다.
- 기본 Xcode 경로는 `/Library/Developer/CommandLineTools`여서 native 테스트 링크가 실패했다. 시스템 설정을 변경하지 않고 아래 환경 변수를 해당 명령에만 지정했다.

```sh
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer ./gradlew :shared:iosSimulatorArm64Test
```

설치된 Xcode 26.6은 확인했지만 테스트 링크에서 `ld: framework 'Sentry' not found`가 발생했다. [실제 실패 로그 발췌](ios-link-error.txt). 이것은 초기 실행 결과다. 이후 테스트 실행 파일에 앱과 같은 버전의 Sentry simulator 프레임워크를 연결하여 **shared 800개·developerTools 31개 iOS 테스트 실행을 완료했다**. 테스트 의존성을 제거하거나 앱 SDK 버전을 바꾸지 않았다. [원인·해결·실행 근거](ios-sentry-link-fix.md)를 참고한다.

## 이슈 코멘트

- [설계·브랜치 준비](https://github.com/woowacourse-teams/2026-pheeeew/issues/1031#issuecomment-6486378567)
- [1·2단계 코드 작성](https://github.com/woowacourse-teams/2026-pheeeew/issues/1031#issuecomment-6486447264)
- [3~5단계 코드 연결](https://github.com/woowacourse-teams/2026-pheeeew/issues/1031#issuecomment-6486533814)

[최종 자동 검증·제출 항목 현황](https://github.com/woowacourse-teams/2026-pheeeew/issues/1031#issuecomment-6486671183)을 기록했다. 가이드와 검증 파일은 이 브랜치의 PR에 포함한다.

## 제출 항목 현황과 후속 작업

| 제출 항목 | 현재 상태 |
| --- | --- |
| 구현 수단·선정 이유 | 팀 서버·KMP 공통 구조 활용으로 문서화 |
| 기본값 | 활성화 true/공지 빈 문자열, 정상 캐시 우선 구현·fixture 검증 |
| 수신·적용 시점 | 60초/3초·즉시 차단·지도 공지로 구현, 주기/취소 자동 검증 |
| 실제 변경부터 반영까지 시간 | **미측정**. 60초+요청 시간은 목표이며 실측이 아님 |
| 실제 서버 값 변경·앱 반영 | **미실행**. 백엔드 테스트 API 배포 후 진행 |
| 실제 앱 화면 캡처·영상 | **미확보**. RC-01~07과 플랫폼별 결과를 다음 단계에서 확보 |
| iOS native 테스트 실행 | 링크 보완 후 shared 800개·developerTools 31개 통과 |

다음 작업은 실제 배포 API/오류/문자 수 계약 대조, Android/iOS 실제 UI의 차단·복구·공지 배치·로컬 캐시 유지 확인, 알림 직접 진입, 서버 ON→OFF→ON/공지 변경, T0~T3 측정과 실제 영상·캡처다. 가상 시계 테스트 결과나 개발자 도구 임시값을 실제 서버 반영 증거로 제출하지 않는다. 이슈는 열린 상태로 유지한다.
