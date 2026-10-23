# 서버 배포 안내

`develop` 에 푸시하면 `BE DEV CD`, `main` 에 푸시하면 `BE CD` 가 돌면서 `app-blue` 와 `app-green` 을 번갈아 띄워요. 한 번에 한 색만 떠 있고, 교체 구간에만 둘이 함께 떠요. **두 환경의 배포 흐름은 같아요.**

| 역할 | 개발 | 운영 |
| --- | --- | --- |
| 앱 두 색 | `compose.dev.yml` | `compose.prod.yml` |
| nginx (앱과 분리) | `compose.nginx.dev.yml` | `compose.nginx.yml` |
| nginx 설정 | `nginx/api-dev.conf` | `nginx/api.conf` |
| 공유 네트워크 | `pheeeew-dev` | `pheeeew-prod` |

`nginx/upstream/active.conf` 가 지금 어느 색이 트래픽을 받는지 들고 있어요. **서버에만 있고 배포가 덮지 않아요.**

## 전환 흐름

배포마다 아래를 **매번** 수행해요. 검사(CI)에서 통과했다는 이유로 생략하지 않아요.

```
1. 새 이미지 pull
2. 새 색 기동 → 컨테이너 healthcheck 로 readiness 확인 (--wait)
3. 새 색에 최소 API 직접 확인          nginx 에서 http://<색>:8080 으로 호출, available 이 boolean 인지
4. 전환 파일 교체 + nginx -t + reload
5. 전환 검증                          HTTP 200 + JSON 본문 + X-Upstream 이 새 색
6. 이전 요청 drain                    종료 중인 nginx 워커가 0이 될 때까지
7. 이전 색 graceful 종료 후 제거
```

3번은 컨테이너가 healthy 해도 API 가 아직 못 받는 경우를 전환 **전**에 거르기 위한 거예요. 실패하면 전환하지 않고 새 색만 정리해요.
최소 API 호출은 건당 5초로 제한하고 30초 동안 재시도해요. 마지막 호출과 대기 때문에 재시도 종료는 약 35초까지 걸릴 수 있어요. 응답이 멈춰도 트래픽을 바꾸지 않고 실패로 끝나요.

전환 **전**에 실패하면 전환하지 않고 끝나요. 전환 **후** 검증이 실패하면 이전 색으로 되돌리고 새 색을 정리해요. 어느 쪽이든 트래픽은 검증된 색에 남아요.

정상 응답 검증은 상태 코드 200, 본문의 `available` 이 boolean, `X-Upstream` 이 새 색인지를 함께 봐요. 503 에도 `X-Upstream` 은 붙으므로 헤더만으로 판단하지 않아요.

복구까지 실패하면 **두 색을 모두 남기고** 전환 파일과 실제 응답을 함께 보고해요. 그때는 사람이 확인해야 해요.

## 운영의 첫 배포

운영은 `app` 한 색에서 출발해요. 첫 배포만 **이관 경로**를 타요.

```
전환 파일 없음 → 준비 단계가 pheeeew-prod-app 존재를 보고 app 으로 만든다
ACTIVE=app → TARGET=app-blue → 전환 → 검증 → 드레인 → pheeeew-prod-app 정리
```

준비 단계가 **이전 프로젝트의 nginx 도 정리**해요. 분리 전 `compose.prod.yml` 이 nginx 와 app 을 한 프로젝트에 두고 `:80`, `:443` 을 잡고 있어서, 새 프로젝트의 nginx 가 뜨려면 먼저 비워야 해요. 같은 단계 안에서 바로 다시 띄우므로 사람이 손댈 일은 없어요.

이관이 끝나면 `app` 허용과 정리 분기는 지워요. 개발은 #897 에서 지웠어요.

## 되돌리기

배포가 끝난 뒤에 문제를 발견했으면 Actions 에서 **`BE DEV CD` 를 수동 실행**하고 `image_tag` 에 이전 태그를 넣어요. 빌드를 건너뛰고 그 이미지로 다시 전환해요.

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
| `test-deploy-switch.py` | 워크플로의 배포 셸을 그대로 꺼내 전환·복구·최소 API 실패/응답 지연과 Compose 구성을 검사 |
| `nginx/test-alb-health.py` | nginx 라우팅, 접근 제어, 장애 응답 |

둘 다 `BE Nginx CI` 에서 돌아요. 배포 셸을 바꾸면 테스트가 같은 셸을 읽으므로 따로 고칠 것이 없어요.

### 어떤 검사가 왜, 언제 도나

| | **BE CI** | **BE Nginx CI** |
| --- | --- | --- |
| 무엇을 검사하나 | 애플리케이션 코드, ARM64 이미지 빌드 | nginx 설정, **배포 셸** |
| 잡으려는 회귀 | 로직 오류, 스키마-엔티티 불일치, 마이그레이션 실패, 이미지 빌드 깨짐 | 잘못된 대상 라우팅, 접근 경계 붕괴, 전환 실패 시 잘못된 정리, 복구 누락, 진행 중 요청 유실 |
| 대상 브랜치 | `main`, `be/dev`, `develop` | `main`, `be/dev`, `develop` |
| 변경 경로 | `BE/**` | `nginx/**`, `compose.nginx.dev.yml`, `compose.dev.yml`, `test-deploy-switch.py`, `be-dev-deploy.yml` |
| 실제로 돌리는 것 | 실제 PostgreSQL+PostGIS, 실제 Spring 컨텍스트 | 실제 nginx·Docker·Compose, 워크플로에서 꺼낸 실제 배포 셸 |
| 가짜로 대체한 것 | `@WebMvcTest` 의 서비스 모킹, 이미지는 빌드만 | 앱은 파이썬 스텁, ALB 는 도커 서브넷. 그리고 네 곳 주입 — `image prune`, `reload` 실패, 워커 검사 실패, 엣지 응답 |

**일반 앱·SQL 변경만 있는 PR 에서는 Nginx CI 가 돌지 않아요.** 다만 `paths` 는 최신 커밋이 아니라 **PR 전체 diff** 로 판정하므로, 그 PR 이 한 번이라도 배포 관련 파일을 바꿨다면 이후 커밋이 SQL 뿐이어도 계속 돌아요. 배포 셸이 바뀐 PR 이면 머지될 때까지 전환·복구를 계속 봐야 하므로 정상이에요.

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

`test-deploy-switch.py` 는 `DEPLOY_TARGET` 으로 두 환경을 모두 검사해요. 기본은 `dev` 예요.

```bash
DEPLOY_TARGET=prod python3 BE/docker/test-deploy-switch.py
```

`test-alb-health.py` 는 **개발 설정만** 봐요. `api.conf` 에는 ALB 경유 경로가 없어 같은 검사를 그대로 쓸 수 없어요. 운영 nginx 의 라우팅은 전환 검사가 실제 `api.conf` 를 띄워 거치는 범위까지만 덮여요.

### 검사가 보장하지 않는 것

| 층 | 무엇을 보나 |
| --- | --- |
| **PR 회귀 검사 (CI)** | 전환·복구 **분기가 맞게 짜였는지**. 가짜 앱, 가짜 ALB |
| **실제 배포 (CD)** | 매 배포마다 실제 이미지로 위 7단계를 수행. 테스트가 아니라 **운영 장치** |
| **외부 연속 요청 측정** | 사용자가 **몇 건 실패했는지**. CI 도 CD 도 이 값을 모른다 |

CI 는 **실제 앱 이미지의 기동 시간·메모리·DB 커넥션, 실제 ALB 와 공개 도메인 경로, 중단 시간(초)** 을 보장하지 않아요. 오류율 모니터링(Grafana)과 중단 시간 측정은 **배포와 별개 검증**이에요.

같은 서버에 배포가 겹치지 않도록 워크플로가 `concurrency` 로 잠가요. 개발은 `deploy-dev`, 운영은 `deploy-prod` 이고 둘 다 `cancel-in-progress: false` 라 **진행 중인 배포를 중간에 끊지 않아요.** 수동 롤백도 같은 워크플로라 같은 잠금을 써요.

### 진행 중 요청을 어떻게 재는가

가짜 앱이 `/slow?seconds=N` 을 받으면 `/appstate/inflight-<색>` 파일을 만들고 N 초 뒤 응답한 다음 지워요. 검사는 그 파일로 **요청이 실제로 그 색에 닿은 시점과 끝난 시점**을 알아요. 고정 `sleep` 에 기대지 않아요.

`ThreadingHTTPServer` 를 쓰는 이유는 느린 요청이 도는 동안에도 **헬스체크가 따로 처리돼야** 하기 때문이에요. 단일 스레드면 느린 요청이 헬스체크를 막아 컨테이너가 unhealthy 가 돼요.

느린 요청 길이는 `proxy_read_timeout 60s` 보다 **충분히 짧게** 둬요. 60초로 두면 응답과 타임아웃이 경합해 504 가 섞여요. 대신 "200 이 왔다" 로 끝내지 않고 **요청이 도는 동안 정리 대상 컨테이너가 살아 있는지, 요청이 끝난 뒤에야 지워지는지**를 확인해요.
