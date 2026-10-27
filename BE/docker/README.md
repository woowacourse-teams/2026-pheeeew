# 개발 서버 배포 안내

`develop` 에 푸시하면 `BE DEV CD` 가 돌면서 `app-blue` 와 `app-green` 을 번갈아 띄워요. 한 번에 한 색만 떠 있고, 교체 구간에만 둘이 함께 떠요.

| 파일 | 역할 |
| --- | --- |
| `compose.dev.yml` | `app-blue`, `app-green`. 색마다 로그 볼륨이 따로예요 |
| `compose.nginx.dev.yml` | nginx. 앱 배포와 분리돼 있어 앱만 교체돼요 |
| `nginx/upstream/active.conf` | 지금 어느 색이 트래픽을 받는지. **서버에만 있고 배포가 덮지 않아요** |

## 전환 흐름

배포마다 아래를 **매번** 수행해요. 검사(CI)에서 통과했다는 이유로 생략하지 않아요.

```
1. 기존 서버/DB/실제 라우팅 확인, 새 컨테이너 UUID 준비 → 새 이미지 pull·세대 계약 확인
2. 새 색 기동 → 컨테이너 healthcheck 로 readiness 확인 (--wait)
3. 새 색에 최소 API 직접 확인          nginx 에서 http://<색>:8080 으로 호출, available 이 boolean 인지
4. 계약 2의 신규 입력 차단 → DB 조건부 세대 교체·이전 연결 자격 무효화 → 구 WS 종료
5. 전환 파일 교체 + nginx -t + reload → HTTP 200 + JSON 본문 + X-Upstream·DB·실행 세대 확인 → 후보 입력 개방
6. 이전 요청 drain                    reload 직전에 기록한 nginx 워커 PID가 모두 사라질 때까지
7. 이전 색 graceful 종료 후 제거
```

3번은 컨테이너가 healthy 해도 API 가 아직 못 받는 경우를 전환 **전**에 거르기 위한 거예요. 실패하면 전환하지 않고 새 색만 정리해요.
최소 API 호출은 건당 5초로 제한하고 30초 동안 재시도해요. 마지막 호출과 대기 때문에 재시도 종료는 약 35초까지 걸릴 수 있어요. 응답이 멈춰도 트래픽을 바꾸지 않고 실패로 끝나요.

DB 전환 전 기동·최소 API 검증이 실패하면 기존 소유권을 유지해요. 후보 입력 개방 시도 전의 실패만 DB·실행 인스턴스·입력 차단·프록시를 검증한 자동 복귀를 허용해요. 개방 시도 이후에는 ON 자동 복귀를 하지 않고 별도 중단·복구 경로로 넘어가요. 불확실하면 두 서버와 기록을 보존해요.

정상 응답 검증은 상태 코드 200, 본문의 `available` 이 boolean, `X-Upstream` 이 새 색인지를 함께 봐요. 503 에도 `X-Upstream` 은 붙으므로 헤더만으로 판단하지 않아요.

복구까지 실패하면 **두 색을 모두 남기고** 전환 파일과 실제 응답을 함께 보고해요. 그때는 사람이 확인해야 해요.

### 경쟁 ON 배포·활성화·중단·복구

현재 도구는 기존 OFF 경로와 이미지 계약 2의 입력 차단·ON 전환을 구분해요. ON에서는 원본과 후보가 모두 계약 2여야 하며, 정상 배포가 `enabled`·`activation_date`를 바꾸지 않아요. 최초 계약 2 도입은 OFF에서 수행해요.

이미지의 입력 차단은 같은 컨테이너 재시작에 유지돼요. 새 후보는 CLOSED로 시작하고 실제 라우팅·DB 자격을 확인한 뒤 도구가 개방해요. 구형 도구는 계약 2 이미지를 거절해요. 앱 부팅·health 200으로 저장 준비를 대신하지 않아요.

명령·실행 전 확인·실패 경계는 [운영 절차](../docs/operations/realtime-press.md), 선택 이유는 [ADR-0042](../docs/adr/0042-fence-press-admission-during-deployment.md)를 따라요. 실제 배포·활성화·중단·복구는 대상 SHA·환경·시점·중단 기준을 고정해 별도로 승인받아요. 운영의 기존 단일 앱 워크플로는 계약 2를 앱 교체 전에 거절하며 운영 이행 완료로 취급하지 않아요.

### 프레스 서버 세대: 경쟁 OFF 전용

이 절은 **기존 계약 1의 OFF 전용·최초 이행 동작**을 설명해요. 이 경로는 `press_race_control`의 `active_server_generation`만 바꾸고 공개 설정은 유지해요. 계약 2의 ON 배포·활성화·중단 명령은 위 운영 절차와 `press-operations.py`를 함께 사용해요. 실제 개발 배포 서버에는 Python 3.10 이상, `psql`, OpenSSL이 필요하며 새 앱 이미지에는 curl·flock이 있어야 해요.

- `prepare`: 현재 컨테이너와 DB를 읽고 OFF 상태를 확인해요. 새 대상만 새 UUID를 받아 `press-generations.env`에 저장하며 기존 활성 컨테이너 UUID는 유지해요. 앱은 환경변수로 UUID를 받고 부팅으로 DB 소유권을 가져가지 않아요.
- `record-target`: 새 컨테이너 기동 뒤 UUID 설정·이미지 계약·DB 목적지를 확인하고 실제 컨테이너 ID·이미지를 전환 기록에 고정해요. 기동 실패로 종료된 후보도 같은 방식으로 식별하며, 이후 같은 이름의 다른 컨테이너로 기록을 덮어쓰지 않아요.
- `handoff`: 새 컨테이너의 실제 UUID·이미지 계약 버전·DB 목적지를 확인한 뒤, 예상 구 세대·OFF·활성화 날짜가 모두 같을 때만 DB 행을 갱신해요. 공통 행의 기존 `FOR SHARE` 저장이 끝나기를 기다리며 잠금 5초·문장 8초 제한을 넘으면 실패해요. 공정성이나 최대 전환 성공 시간을 보장하지 않아요.
- `restore` / `verify`: 커밋 응답 유실도 실제 DB 재조회로 판단해요. 다른 작업이 세대·경쟁 상태를 바꿨거나 결과를 확인할 수 없으면 추정해서 덮어쓰지 않고 두 서버를 보존해요. `.press-deploy.json`이나 전환 백업이 남으면 다음 배포도 중단해요. 파일을 지우기 전에 DB·실제 nginx 대상·컨테이너 세대를 함께 확인해야 해요.
- `cleanup-target` / `cleanup-active`: 남길 서버의 실행 세대와 DB 소유권을 다시 확인하고 정리할 컨테이너의 기록된 ID·이미지가 같을 때만 **고정 ID**로 종료·제거해요. 실패하거나 같은 이름의 다른 컨테이너로 바뀌었으면 전환 기록·백업을 남겨요.

환경변수·라벨만으로 실제 실행 세대를 판단하지 않아요. 기존 인증 WS(`/api/v1/press-race`)에 DB에 없는 합성 기기의 15초 토큰을 보내고 `protocol.error / DEVICE_NOT_FOUND` 봉투의 UUID를 확인해요. 계약 2의 차단 중에는 DB 쓰기 없는 `SERVER_DRAINING` 봉투를 확인해요. 토큰과 키는 메모리·stdin·파일 디스크립터로만 전달하며 파일·인자·로그에 쓰지 않아요. curl 설정 파일·프록시·리디렉션을 사용하지 않고 컨테이너 안의 고정 localhost 주소로만 요청해요. 이 검사는 실제 HTTP 입력 저장 검증을 대신하지 않아요.

전환 파일과 실제 nginx 응답의 대상이 일치하는지 준비 전과 DB 전환 직전에 확인해요. 새 컨테이너는 환경변수에 지정한 UUID를 재시작 때도 유지하며, 다음 새 컨테이너에는 다른 UUID를 발급해요. 활성 컨테이너의 ID·이미지를 고정하고 대상 컨테이너도 승격 때의 ID·이미지·실행 세대를 다시 확인해요.

DB 교체부터 nginx 전환까지 구 서버 HTTP 입력이 잠시 `503 / SERVER_REPLACED`로 거절될 수 있어요. 요청 식별자가 없는 기존 HTTP를 무조건 자동 재시도하면 안 돼요. 다른 조회 API와 기존 점수는 그대로 유지하며 WS 경쟁은 OFF예요.

첫 지정에서 기존 DB 세대가 NULL이면, 환경변수가 없던 기존 앱을 그대로 둔 채 새 컨테이너부터 명시적 UUID를 적용해요. 이때 실패해 NULL로 복구하는 것은 **배포 전 상태 복원**이며 기존 HTTP 정상화가 아니에요. 기존 활성 컨테이너가 없거나 DB 세대가 non-NULL인데 컨테이너 UUID와 일치하지 않으면 자동으로 진행하지 않아요.

#### 임시 복구된 기존 앱의 최초 이행

DB에는 실행 중인 임의 UUID가 지정돼 있지만 앱 환경변수와 이미지 라벨이 없는 경우, 기본 배포는 계속 거절해요. 별도 개발 배포 승인을 받은 뒤 **BE DEV CD 수동 실행의 `migrate_legacy_generation`**을 켜야 최초 이행을 시도해요. push 배포는 이 옵션을 켜지 않아요. 보조 도구를 직접 쓰는 경우 같은 의미의 옵션은 `prepare <현재 컨테이너> <새 슬롯> --allow-legacy`예요.

1. 경쟁 OFF·활성화 날짜·실제 트래픽 대상을 확인하고, 기존 프로세스의 실행 UUID가 DB 소유권과 같은지 읽기 전용으로 검증해요.
2. 원본 컨테이너의 ID·이미지·시작 시각·재시작 횟수를 고정해요. 기존 컨테이너를 재생성하거나 과거 임의 UUID를 새 컨테이너에 복제하지 않아요.
3. 새 UUID를 명시한 계약 지원 이미지를 예열하고 실제 실행 UUID를 확인한 뒤, 예상 세대·OFF·날짜 조건부 UPDATE와 nginx 전환을 수행해요.
4. 실패하면 원본 프로세스가 그대로이며 실제 UUID도 같을 때만 DB와 nginx를 복구해요. 원본의 재시작·교체·조회 실패가 발견되면 과거 UUID를 덮어쓰거나 후보를 삭제하지 않고 두 서버와 전환 기록을 보존해요.

확인과 DB 변경은 하나의 원자적 작업이 아니므로 각 경계에서 원본을 재확인해요. 운영자·다른 프로세스가 동시에 컨테이너를 변경하는 상황의 무중단·자동 복구를 보장하지 않아요. 기존 임의 세대 프로세스로 복귀한 경우 **그 실행 인스턴스의 HTTP 자격 복원**이며 이후 재시작 문제는 남아요. 영구 복구는 명시적 세대 배포의 성공과 실제 HTTP 저장 검증 후에 판단해요. 일반 health 200이나 닉네임 API 응답만으로 저장 정상이라고 판단하지 않아요.

새 도구의 OFF 배포는 `com.pheeeew.press-generation-contract=1` 또는 `2`가 필요해요. ON은 양쪽 모두 `2`여야 해요. 표시가 없는 과거 이미지로의 일반 되돌리기는 거절하며, 입력 중단·정리·재집계가 필요한 별도 구버전 복귀 절차를 생략하면 안 돼요.

기존 OFF 완료 증거는 ON 배포·초기 활성화·실제 운영 검증 증거가 아니에요. 새 구현의 로컬 결과와 실제 개발·운영 실행 기록을 구분하고 #924의 나머지 검증을 완료했다고 표시하지 않아요.

## 되돌리기

배포가 끝난 뒤 문제를 발견한 경우에는 [운영 절차](../docs/operations/realtime-press.md)에 따라 먼저 경쟁을 OFF로 중단하고 호환 이미지를 확인해요. 별도 배포 승인 후 Actions의 **`BE DEV CD` 수동 실행**에서 `image_tag`에 그 태그를 넣으면 빌드를 건너뛰고 새 컨테이너·새 UUID로 전환해요. 이것은 개방 시도 전 자동 복귀와 다른 작업이에요.

이전 태그는 서버에서 확인해요.

```bash
docker image ls --format '{{.Repository}}:{{.Tag}}  {{.CreatedSince}}' | grep dev-
```

`docker image prune -f` 는 태그 없는 이미지만 지우므로 이전 배포 이미지는 남아 있어요.

주의할 것이 둘이에요.

- 수동 실행은 **선택한 ref 의 compose 와 nginx 설정을 그대로 전송해요.** 앱 코드만 되돌릴 때는 맞지만, 설정까지 되돌리려면 그 시점의 커밋을 가리키는 ref 로 실행해야 해요.
- 구버전 앱이 **새 스키마에서 떠야 해요.** `ddl-auto: validate` 라서 엔티티가 참조하는 컬럼이 사라졌으면 부팅이 실패해요. 마이그레이션을 expand-contract 로 나누는 규칙이 [코드 컨벤션 9.3](../docs/decisions/backend-code-convention.md)에 있어요.

## 상태 확인

```bash
docker ps --format '{{.Names}}\t{{.Status}}'
docker exec pheeeew-dev-nginx cat /etc/nginx/upstream/active.conf
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'
docker logs pheeeew-dev-alloy 2>&1 | grep -i skipping
```

마지막 줄이 **비어 있어야** 해요. Alloy 가 두 색 로그를 다 수집한다는 뜻이에요.

## 검사

| 파일 | 검증하는 것 |
| --- | --- |
| `test-press-generation.py` | 실제 subprocess 입출력으로 세대 조회의 분할 수신·응답 검증·크기 및 시간 상한·EOF 전 완료를 검사해요. 전송 상대와 DB 응답은 격리된 시험용 프로그램이에요 |
| `test-deploy-switch.py` | 실제 nginx·Docker·PostgreSQL 17/PostGIS 3.5에서 워크플로의 셸·세대 보조 도구로 전환·복구·커밋 응답 유실과 Compose 구성을 검사. 앱은 Python 스텁이며 DB 연결 전송과 일부 장애를 주입해요 |
| `PressHttpDeploymentLifecycleIntegrationTest` | 실제 Spring HTTP API·PostGIS에서 임의 세대 최초 지정→새 명시적 세대 이행→HTTP 서버 종료·재부팅→정상 교체→조건부 복귀 후 입력·원본·투영 저장을 확인해요. Python의 실제 토큰 서명·WS 세대 조회도 실행해요 |
| `PressOperationsProxyIntegrationTest` | 실제 Spring 컨테이너 두 개·인증 WS·nginx·PostGIS에서 ON 전환·개방 전 복귀·미확인 정산·OFF 복구·재시작을 검증해요. `PHEEEEW_PRESS_OPERATIONS_IMAGE`로 검증용 이미지를 지정한 경우에 실행해요 |
| `nginx/test-alb-health.py` | nginx 라우팅, 접근 제어, 장애 응답 |

`test-press-generation.py`, `test-deploy-switch.py`, `nginx/test-alb-health.py`는 `BE Nginx CI`, `PressHttpDeploymentLifecycleIntegrationTest`는 `BE CI`의 Gradle 테스트에서 돌아요. 실제 Spring 프록시 검사는 `BE Nginx CI`의 별도 이미지 빌드·선택 테스트 잡에서 실행해요. 배포 셸 검사는 워크플로의 같은 셸을 읽어요.

WS 세대 조회는 Upgrade와 첫 완전한 text 응답의 오류 코드·UUID를 검증하면 연결 종료를 기다리지 않고 완료해요. 수집하는 검증 데이터의 64KiB 상한과 기존 curl 3초·외부 수신 5초 제한, 조회 전후 컨테이너 신원 검사는 유지해요. 조기 완료 이후의 추가 데이터까지 검사한 것은 아니에요. 로컬 `docker exec` 프로세스 정리는 컨테이너 내부 curl 종료의 증거가 아니며, 남은 읽기 전용 조회는 기존 curl 제한으로 끝나요.

nginx의 reload 명령은 신호 접수 뒤 먼저 반환할 수 있어요. 종료 중이라는 프로세스 표시가 아직 없다고 요청이 끝난 것은 아니므로, 정상 전환과 복귀 각각 reload 직전에 모든 worker PID를 기록하고 그 PID가 프로세스 목록에서 사라질 때까지 기다려요. PID 재사용은 보수적으로 추가 대기를 일으키며 조기 정리로 판단하지 않아요. 조회 실패·빈 기록은 0개가 아니라 확인 불가로 취급해요. 60초 대기 뒤에도 확인할 수 없으면 기존처럼 앱의 종료 유예 안에서 남은 요청을 마무리하므로 유예를 넘긴 요청 보존까지 보장하지 않아요.

이미지 pull·inspect 실패는 후보 기동과 DB·nginx 전환 전이므로 전환 기록을 지우고 종료해요. 이미지 준비가 회복되면 수동으로 전환 기록을 지우지 않고 다시 배포할 수 있어요.

### 어떤 검사가 왜, 언제 도나

| | **BE CI** | **BE Nginx CI** |
| --- | --- | --- |
| 무엇을 검사하나 | 애플리케이션 코드, ARM64 이미지 빌드 | nginx 설정, **배포 셸**, 실제 Spring 이미지의 프레스 운영 전환 |
| 잡으려는 회귀 | 로직 오류, 스키마-엔티티 불일치, 마이그레이션 실패, 이미지 빌드 깨짐 | 잘못된 대상 라우팅, 접근 경계 붕괴, 전환 실패 시 잘못된 정리, 복구 누락, 진행 중 요청 유실 |
| 대상 브랜치 | `main`, `be/dev`, `develop` | `main`, `be/dev`, `develop` |
| 변경 경로 | `BE/**` | nginx·배포 도구·워크플로·프레스 애플리케이션/테스트·인증 경로 |
| 실제로 돌리는 것 | 실제 PostgreSQL+PostGIS, 실제 Spring 컨텍스트 | 실제 nginx·Docker·Compose, 워크플로에서 꺼낸 실제 배포 셸 |
| 가짜로 대체한 것 | `@WebMvcTest` 의 서비스 모킹, 이미지는 빌드만 | 앱은 파이썬 스텁, ALB 는 도커 서브넷. 그리고 네 곳 주입 — `image prune`, `reload` 실패, 워커 검사 실패, 엣지 응답 |

일반 앱·SQL 변경만 있는 PR에서는 Nginx CI가 실행되지 않을 수 있어요. 프레스·인증 경로 변경은 실제 전환 검사 대상으로 포함해요. `paths`는 최신 커밋이 아니라 **PR 전체 diff**로 판정하므로 해당 PR이 검사 경로를 바꾸면 후속 커밋에서도 계속 실행돼요.

### 머지 전에 직접 돌리기

```bash
python3 BE/docker/test-deploy-switch.py
python3 BE/docker/nginx/test-alb-health.py
```

시나리오 이름을 주면 그것만 돌아요.

```bash
python3 BE/docker/test-deploy-switch.py '롤백이 진행 중 요청을 기다린다'
```

리눅스 러너와 같은 환경에서 확인하려면 컨테이너 안에서 돌려요. **`진행 중 요청` 두 시나리오는 호스트에 공개된 nginx 포트로 요청해야 해서 이 방식으로는 못 돌려요.** 그래서 이름을 지정해 나머지를 돌려요.

```bash
mkdir -p /tmp/dsw-linux
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp/dsw-linux:/tmp/dsw-linux -v "$PWD:/repo:ro" \
  -e TMPDIR=/tmp/dsw-linux -w /repo docker:28-cli \
  sh -c 'apk add -q python3 py3-yaml openssl bash curl
         python3 BE/docker/test-deploy-switch.py \
           "정상 전환" "새 버전 기동 실패" "새 버전이 최소 API 를 응답하지 않음" \
           "새 버전의 최소 API 응답이 멈춤" \
           "nginx -t 실패" "reload 명령 실패" "전환 미반영" "전환 후 503" \
           "전환 후 잘못된 본문" "복구 실패" "워커 검사 실패" "전환 후 깨진 JSON" \
           "이관 전 전환 파일 거부" "전환 파일 이상값" "전환 파일 빈 값" \
           "compose 의 색 이름이 배포 스크립트와 일치"'
```

### 검사가 보장하지 않는 것

| 층 | 무엇을 보나 |
| --- | --- |
| **PR 회귀 검사 (CI)** | 전환·복구 **분기가 맞게 짜였는지**. 가짜 앱, 가짜 ALB |
| **실제 배포 (CD)** | 매 배포마다 실제 이미지로 위 7단계를 수행. 테스트가 아니라 **운영 장치** |
| **외부 연속 요청 측정** | 사용자가 **몇 건 실패했는지**. CI 도 CD 도 이 값을 모른다 |

배포 셸 검사는 세대 조회의 Docker/curl 전송을 shim으로 대체해요. 실제 Spring 테스트는 토큰 서명·curl·WS 봉투를 사용하지만 HTTP 서버 컨텍스트 종료·재부팅이며 JVM·실제 앱 컨테이너 재시작은 아니에요. CI 는 **실제 앱 이미지의 기동 시간·메모리·DB 커넥션, 실제 ALB 와 공개 도메인 경로, 중단 시간(초)** 을 보장하지 않아요. 오류율 모니터링(Grafana)과 중단 시간 측정은 **배포와 별개 검증**이에요.

같은 서버에 배포가 겹치지 않도록 워크플로가 `concurrency` 로 잠가요. 개발은 `deploy-dev`, 운영은 `deploy-prod` 이고 둘 다 `cancel-in-progress: false` 라 **진행 중인 배포를 중간에 끊지 않아요.** 수동 롤백도 같은 워크플로라 같은 잠금을 써요.

### 진행 중 요청을 어떻게 재는가

가짜 앱이 `/slow?seconds=N` 을 받으면 `/appstate/inflight-<색>` 파일을 만들고 N 초 뒤 응답한 다음 지워요. 검사는 그 파일로 **요청이 실제로 그 색에 닿은 시점과 끝난 시점**을 알아요. 고정 `sleep` 에 기대지 않아요.

`ThreadingHTTPServer` 를 쓰는 이유는 느린 요청이 도는 동안에도 **헬스체크가 따로 처리돼야** 하기 때문이에요. 단일 스레드면 느린 요청이 헬스체크를 막아 컨테이너가 unhealthy 가 돼요.

느린 요청 길이는 `proxy_read_timeout 60s` 보다 **충분히 짧게** 둬요. 60초로 두면 응답과 타임아웃이 경합해 504 가 섞여요. 대신 "200 이 왔다" 로 끝내지 않고 **요청이 도는 동안 정리 대상 컨테이너가 살아 있는지, 요청이 끝난 뒤에야 지워지는지**를 확인해요.
