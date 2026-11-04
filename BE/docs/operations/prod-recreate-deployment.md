# 운영 단일 앱 교체 배포 (임시)

2026-11-04 사용자 결정, [#1088](https://github.com/woowacourse-teams/2026-pheeeew/issues/1088). WebSocket 기능을 유지하면서 운영 CD 전용 잠금·bootstrap·blue/green 전환 코드를 제거한다. 사용자가 적은 시간에 수동 배포하고 서비스 중단을 허용한다.

## Git 이력과 제거 범위

`de84154e`는 운영 프레스 최초 이행·영속 잠금·blue/green 배포 도구를 추가했고 `f8e41bbb`는 준비 실패 복구를 수정했다. 이 변경의 운영 전용 도구 6개(`press-prod-bootstrap.py`, `press-prod-deploy.sh`, `press-prod-deploy-guard.sh`, `press-prod-lock.py`, `press-prod-manifest.py`, `press-prod-preflight.py`)와 직접 의존 테스트 7개를 제거한다. 새 배포 모듈·Compose override 파일은 추가하지 않는다.

기존 `.github/workflows/be-deploy.yml` 안에 단일 stop/start 흐름만 둔다. 개발 CD가 사용하는 `press-generation.py`와 `press-operations.py`는 유지하고, 운영 CD는 전자의 실행 신원·DB 조회·읽기 전용 WS 검증만 재사용한다. Java WebSocket/HTTP 기능, 요청 ID 중복 방지·정산, DB fencing, 기존 프레스 데이터·경쟁 공개 설정을 유지한다.

## 실행 흐름

main push는 `prod-<SHA>` 이미지만 빌드한다. Actions → BE CD → Run workflow에서 **main**, **build_only=false**를 명시한 경우에만 운영 배포한다. 기본값은 true다. 배포 시각을 자동 예약하지 않는다. be/dev 대상 PR 병합은 운영 배포가 아니며 develop/main 승격과 실제 실행은 별도 작업이다.

1. 현재 nginx active 슬롯과 기존 앱의 고정 UUID·DB 소유권·공개 상태·실제 HTTPS WS 응답을 확인한다. 새 이미지 pull과 Compose 검증을 기존 앱 종료 전에 끝낸다.
2. 중단 기록 `.press-deploy.json` 하나를 남긴다. 기존 앱의 정확한 container ID를 종료·제거하고 부재를 확인한다. 다른 앱이 실행 중이거나 확인에 실패하면 새 앱을 시작하지 않는다.
3. 제어 행의 `FOR UPDATE` 잠금으로 남은 프레스 공유 잠금 트랜잭션을 기다리고 소유권·공개 상태·날짜가 그대로인지 확인한다.
4. 같은 슬롯·UUID로 새 앱을 한 번 시작한다. health, 실제 HTTPS WS의 UUID·입력 허용, DB·실행 신원 확인이 모두 성공하면 환경·Compose 입력을 반영하고 중단 기록을 지운다.

JVM 기동과 nginx DNS 재조회까지 서비스 중단 및 WS 단절이 생긴다. 새 앱 health 대기는 최대 180초이며 Docker 명령·검증 시간은 별도다. 몇 초 중단이나 앱의 재연결 제한 안에 완료됨을 보장하지 않는다.

## 유지하는 운영 구성

현재 nginx는 app-blue/app-green 이름과 연결돼 있고 Alloy는 해당 슬롯의 로그 볼륨을 읽는다. 그래서 `compose.prod-bluegreen.yml`의 기존 슬롯 이름·프로필·네트워크·로그 볼륨 정의를 유지하되, 배포 때 active 슬롯 하나만 종료 후 재생성한다. nginx의 Upgrade/Connection 전달 설정과 Alloy를 교체·reload하지 않는다. 과거 `compose.prod.yml`로 통째로 되돌리면 현재 upstream·로그·서버 세대와 달라지고, 남아 있는 legacy app 서비스와 Compose 소유권이 충돌할 수 있다.

[ADR-0042](../adr/0042-fence-press-admission-during-deployment.md)의 운영 입력 차단·세대 양도 방식에는 임시 예외를 둔다. 기존 운영 Compose 환경에 `PHEEEEW_PRESS_RACE_ADMISSION_FILE=""`, `PHEEEEW_PRESS_RACE_ADMISSION_REQUIRED=false`를 명시한다. 기존 Java 코드가 지원하는 설정이다. 파일 입력 차단을 사용하지 않으므로 **같은 UUID로 두 앱을 동시에 실행해서는 안 된다.** 개발 CD/전환 도구와 이 운영 경로를 섞어 실행하지 않는다.

GitHub `deploy-prod` concurrency와 cancel-in-progress=false를 유지한다. 기존 수동 프레스 명령과 공유하는 `.press-operations.lock`의 OS flock만 사용하며 프로세스 종료 시 자동 해제된다. 잠금 파일은 삭제하지 않는다. 과거의 잠금 디렉터리를 획득·해제하는 코드는 제거한다. `.press-deploy.json`은 잠금이 아니라 중단된 교체를 다음 실행이 덮어쓰지 못하게 하는 단일 기록이다.

## 실패와 수동 복구

자동 rollback과 별도 복구 상태 머신은 제거한다. pull·배포 전 검증 실패는 기존 앱을 유지하지만, 기존 앱을 종료한 이후 실패하면 서비스 중단이 길어질 수 있다. Docker up timeout·SSH 단절·새 앱 검증 실패 모두 기록과 임시 입력을 남기고 종료한다. 결과가 불확실한 명령을 자동 재실행하지 않는다.

실패 시 운영자는 실제 컨테이너·DB·라우팅 및 기록을 확인하고 복구할 이미지의 현재 스키마 호환성을 검토한 뒤 별도로 승인된 수동 복구를 수행한다. 기록에는 이전 실제 이미지 ID, 슬롯, UUID와 임시 경로가 있다. `/opt/pheeeew/.recreate.*`의 `previous.env`에는 이전 실제 환경을 0600으로 보관한다. Docker 변경 명령도 OS 잠금 FD를 상속한다. 환경 파일·명령 예외 출력에는 비밀값이 있을 수 있으므로 Actions 로그에 출력하지 않는다.

DB 마이그레이션은 자동으로 되돌리지 않는다. 확인되지 않은 기록을 삭제하고 곧바로 재실행하지 않는다. 정상 성공한 임시 디렉터리만 workflow가 삭제하며 실패한 디렉터리는 복구 후 별도로 정리한다.

## 최초 운영 전환과 인계

이번 저장소 변경은 운영 서버의 기존 파일·잠금을 삭제하지 않는다. 2026-11-04 14:34–14:35 KST 확인 시점에는 과거 실행의 `.press-operations.lockdir`가 남아 있었다. 새 workflow는 과거 실행과 겹치지 않도록 이 잠금·미완료 기록을 발견하면 거절한다. 최초 전환 때 이전 Actions/수동 작업이 끝났는지, 실제 프로세스·DB·라우팅·기록이 안전한지 다시 확인하고 별도로 승인된 정리 작업으로 해소해야 한다. 과거 Actions 재실행과 새 경로를 혼용하지 않는다.

후속 무중단 배포 재설계·실패 복구·CI/CD 검증은 #1088에 인계한다. 자동 잠금 해제만 추가하는 PR #1086은 다른 대안이므로 함께 병합하지 않는다.

## 검증 범위

기존 `test-prod-workflow.py`가 workflow 안의 실제 Python 흐름을 꺼내 제어된 Docker/DB 응답으로 순서·실패 중단·중단 기록·환경 우선순위를 검사한다. 실제 Docker Compose 렌더링으로 슬롯·UUID·admission 환경도 검사한다. 실제 Spring·nginx·PostgreSQL 17/PostGIS 3.5의 `PressOperationsProxyIntegrationTest.단일_앱_교체는*`는 컨테이너 완전 제거/재생성 뒤 같은 UUID의 request-id 정산·중복 방지와 HTTP/WS 저장을 검증한다. 운영 교체·실기기·부하 검증은 별도다.
