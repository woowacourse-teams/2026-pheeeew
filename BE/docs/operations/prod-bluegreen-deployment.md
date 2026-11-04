# 운영 블루그린 배포

2026-11-04, [#911](https://github.com/woowacourse-teams/2026-pheeeew/issues/911). 운영 CD는 개발 CD(`be-dev-deploy.yml`)와 같은 blue/green 전환을 사용한다. 같은 날 [#1088](https://github.com/woowacourse-teams/2026-pheeeew/issues/1088)에서 둔 임시 단일 앱 교체를 대체한다.

## 실행 조건

main push는 `prod-<SHA>` 이미지만 빌드한다. Actions → BE CD → Run workflow에서 **main**, **build_only=false**를 명시한 경우에만 운영에 배포한다. 기본값은 true다.

## 흐름

개발 CD와 단계가 같다.

1. compose, nginx, Alloy 설정과 `press-generation.py`, `press-operations.py`를 서버에 올리고 `.env`, `.env.alloy`를 갱신한다.
2. 공유 네트워크(`pheeeew-prod_default`)와 슬롯별 로그 볼륨을 준비하고 nginx 설정을 검사한 뒤 reload한다.
3. 현재 슬롯이 실제로 응답하는지 확인하고, `prepare`로 현재 앱의 세대와 DB 활성 세대가 같은지 확인한 뒤 반대 슬롯에 새 세대를 준다.
4. 반대 슬롯을 띄우고 최소 API 응답을 확인한 뒤 `handoff`, `close-active`, nginx 전환, `verify`, `open-target` 순서로 넘긴다.
5. 이전 nginx 워커가 끝나기를 기다린 뒤 이전 슬롯을 정리하고, Alloy를 배포한 뒤 `finalize`로 전환 기록을 지운다.

전환 중 실패하면 이전 슬롯으로 되돌린다. 되돌리기도 확인하지 못하면 두 슬롯과 전환 기록을 남기고 멈춘다. 이 경우 기록을 확인하기 전에는 다시 배포하지 않는다.

## 개발 CD와 다른 점

- 올리는 파일: `compose.prod.yml`, `compose.nginx.prod.yml`, `nginx/api-prod-bluegreen.conf`(서버에서는 `api-bluegreen.conf`), `compose.monitoring.prod.yml`, `alloy/config.prod-bluegreen.alloy`
- 응답 확인 주소: `api.pheeeew.com`
- 기존 랜덤 세대의 최초 이행 옵션(`migrate_legacy_generation`)이 없다
- 접속 사용자: `ubuntu`

## 첫 배포 전 확인

2026-11-04에 서버에서 읽은 상태는 다음과 같다. 활성 슬롯은 `app-blue`, 이미지 세대 계약은 2, 파일 입력 차단은 켜져 있고 `OPEN`이며 `press-generations.env`의 blue 세대와 같다. 그래서 별도 이행 없이 첫 배포가 `app-green`으로 넘어간다. DB 활성 세대와 같은지는 `prepare`가 확인한다.

과거 운영 도구가 남긴 `.press-operations.lockdir`와 `.press-prod-bootstrap.json` 등이 서버에 남아 있다. 새 흐름은 이 파일들을 쓰지 않지만, 첫 배포 전에 이전 작업이 끝났는지 확인하고 정리한다.

## 검증 범위

운영 배포 셸은 개발 배포 셸과 올리는 파일, 이름, 최초 이행 옵션만 다르다. CI 전환 검사(`test-deploy-switch.py`)는 개발 배포 셸을 검사하므로, 운영 셸이 실제 서버에서 끝까지 도는지는 첫 운영 배포에서 확인한다. 두 앱 동시 기동의 메모리 여유는 2026-11-04 기준 약 2.7GiB이고 앱 하나의 제한은 640MiB다.
