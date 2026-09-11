# 운영 Alloy 연결

운영 EC2 한 대에서 앱과 별도 Alloy 컨테이너를 실행해요. 앱 지표는 60초마다 읽고, 검토된 HTTP 오류·느린 요청 로그만 Grafana Cloud로 전송해요. 별도 Prometheus 서버나 EC2는 만들지 않아요.

## 파일과 연결

```text
/opt/pheeeew/
├── compose.yml                  # 기존 앱·nginx
├── compose.monitoring.yml       # Alloy만 실행
├── .env                         # CD가 앱 이미지·DB Secrets로 갱신
├── .env.alloy                   # CD가 Grafana Secrets로 갱신
└── alloy/
    └── config.alloy
```

로컬 프로젝트에 `.env.alloy`를 만들 필요는 없어요. 이 파일은 운영 배포 시 생성하며, IntelliJ에서 앱을 실행할 때는 사용하지 않아요. 필요한 변수 목록은 아래 표에서 관리해요.

두 Compose 프로젝트는 기존 `pheeeew-prod_default` 네트워크와 `pheeeew-prod_app-logs` 볼륨을 공유해요. 따라서 운영 앱 Compose를 먼저 실행해야 해요. 로그 볼륨은 Alloy에 읽기 전용으로 연결하고 Docker 소켓은 연결하지 않아요. 개발 환경에는 적용하지 않아요.

## 최초 연결

1. 기존 프로젝트 Grafana Cloud 스택의 연결 안내에서 아래 값을 확인해요. 지표와 로그의 사용자 ID는 서로 다를 수 있어요. 대시보드 주소(`*.grafana.net`)를 전송 URL로 사용하지 않아요.

   | 변수 | 값 |
   | --- | --- |
   | `GRAFANA_METRICS_URL` | Prometheus remote write URL, `/api/prom/push` 포함 |
   | `GRAFANA_METRICS_USER` | 지표 연결의 사용자 ID |
   | `GRAFANA_LOGS_URL` | Loki push URL, `/loki/api/v1/push` 포함 |
   | `GRAFANA_LOGS_USER` | 로그 연결의 사용자 ID |
   | `GRAFANA_CLOUD_TOKEN` | 해당 스택에 `metrics:write`, `logs:write` 권한이 있는 실제 토큰 문자열이에요. 정책 이름을 넣지 않아요 |

2. GitHub 저장소의 `Settings → Environments → production → Environment secrets`에 위 다섯 값을 등록해요. 기존 배포 설정과 같이 Secrets로 관리해요. 값에 따옴표를 둘러싸거나 공백·줄바꿈을 추가하지 않아요. 토큰은 Git이나 채팅에 올리지 않아요.
3. 승인된 운영 배포를 실행해요. CD가 Secrets를 확인하고 임시 파일을 만든 뒤 기존 Cloudflare SSH 경로로 `/opt/pheeeew/.env.alloy`를 전송해요. 파일 권한은 `600`으로 제한하며, 앱용 `.env`는 기존대로 별도 갱신해요. 서버에서 파일을 수동 생성할 필요가 없어요.

   필수 값이 없거나 공백·작은따옴표·역슬래시가 포함되면 서버에 접속하기 전에 배포 작업을 중단해요. 파일 값은 작은따옴표로 감싸 Compose가 `$` 등을 변수로 다시 해석하지 않게 해요. 러너 임시 파일은 작업 성공·실패에 관계없이 정리해요.

   URL은 현재 프로젝트 스택에서 확인한 아래 HTTPS 주소만 허용해요. 다른 호스트·경로, 명시적인 포트, URL 안의 인증 정보, 쿼리·fragment가 있으면 환경 파일을 만들기 전에 배포 작업을 중단해요. 주소는 연결 대상이며 비밀 값은 아니에요.

   | 변수 | 허용하는 전송 주소 |
   | --- | --- |
   | `GRAFANA_METRICS_URL` | `https://prometheus-prod-49-prod-ap-northeast-0.grafana.net/api/prom/push` |
   | `GRAFANA_LOGS_URL` | `https://logs-prod-030.grafana.net/loki/api/v1/push` |

   스택이나 리전을 옮겨 주소가 바뀌면 Grafana Cloud에서 새 전송 주소를 확인하고, 배포 워크플로우의 `approved_endpoints`와 이 표를 함께 수정해요. Secrets만 바꾸면 허용 목록 검사에서 중단돼요.

   지표와 로그의 전송 설정은 모두 `follow_redirects = false`로 지정해요. 서버가 다른 주소로 이동하라고 응답해도 자동으로 따라가지 않아요. 이 설정과 주소 검증은 잘못된 전송 대상을 방지하며, 실제 인증 성공이나 Cloud 수신까지 보장하지는 않아요. 서버에서 환경 파일을 직접 수정하고 수동 실행하면 CD의 주소 검증을 거치지 않으므로, 주소 변경은 위 배포 절차로 반영해요.

   앱 배포 성공 후 CD가 아래 명령으로 Alloy를 검증하고 실행해요. 문제 해결 시 서버에서도 같은 명령을 사용할 수 있어요.

   ```bash
   cd /opt/pheeeew
   docker compose --env-file .env.alloy -f compose.monitoring.yml config --quiet
   docker compose --env-file .env.alloy -f compose.monitoring.yml pull alloy
   docker compose --env-file .env.alloy -f compose.monitoring.yml run --rm --no-deps alloy validate /etc/alloy/config.alloy
   docker compose --env-file .env.alloy -f compose.monitoring.yml up -d --force-recreate --wait --wait-timeout 60
   ```

   문법 검사와 컨테이너 준비 상태만으로 Cloud 인증·수신이 확인되지는 않아요. `config`에서 `--quiet`를 빼면 토큰이 출력될 수 있어요. 토큰은 컨테이너 환경에 전달되므로 Docker 관리 권한이 있는 사람은 읽을 수 있어요.

4. 1~2분 뒤 Grafana Explore에서 지표와 로그를 확인해요.

   ```promql
   up{job="pheeeew-api",environment="prod"}
   ```

   값이 `1`이면 마지막 앱 지표 수집이 성공했어요. `0`이면 Alloy는 실행 중이지만 앱 지표 수집에 실패한 것이에요. 지표 자체가 없으면 Alloy 기동·전송 URL·권한·데이터 소스·조회 시간 범위를 확인해요.

   ```logql
   {job="pheeeew-api",environment="prod"} | json
   ```

   오류나 1초 이상 걸린 HTTP 요청이 없다면 로그 결과가 비어 있는 것이 정상이에요. 확인하려고 운영 서버에 의도적으로 오류·부하를 만들지는 않아요.

## 수집 범위와 자원

- 지표: HTTP 요청 시간·횟수, 지도 조회 시간·결과, JVM 메모리·GC·스레드, 앱 프로세스 CPU·가동 시간, DB 커넥션 풀, 수집 상태. `config.alloy`의 이름 허용 목록 밖 지표는 보내지 않아요.
- 로그: `RequestLogWriter`의 `http_request_failed`, `http_request_slow` 이벤트만 전송해요. 다른 logger, 다른 event, 잘못된 JSON은 제외해요. `correlationId`와 오류 위치 등은 JSON 본문에 유지하며 라벨로 승격하지 않아요.
- 앱에서 제공하는 JVM·프로세스 지표가 중심이에요. EC2 전체 메모리·디스크, CPU 크레딧, RDS 내부 상태, nginx·Cloudflare 로그, traces·profiles는 이번 수집 범위에 없어요. HTTP 지표 또한 앱에 도달한 요청만 반영해요.
- Alloy 메모리 상한은 256MiB, Go 메모리 목표는 192MiB, CPU 상한은 0.25 CPU예요. 실제 사용량이나 운영 EC2의 여유를 보장하는 값은 아니에요. 최초 배포 전 `free -h`, `df -h`, `docker stats --no-stream`으로 확인하고 배포 후에도 실제 부하에서 관찰해요.
- Alloy 버전은 `v1.19.2`로 고정했어요. ARM64 이미지를 사용한 로컬 실행을 검증해요. 호스트 포트를 추가로 공개하지 않으며 관리 HTTP는 컨테이너의 `127.0.0.1:12345`에서만 받아요.

## 재시작과 보관 한계

`alloy-data` 볼륨에 지표 WAL과 로그 파일의 읽은 위치를 보관해요. 지표 WAL은 15분마다 정리하며 최소 5분, 최대 1시간의 보관 설정을 사용해요. 시간 기반 정리이며 디스크 용량의 하드 상한은 아니에요. 장시간 전송 장애에서는 지표가 유실될 수 있어요.

로그는 활성 파일 `application.json`만 읽어요. 최초에는 해당 파일의 처음부터 읽고, 정상 재시작에서는 저장한 위치부터 이어 읽어요. 실행 중 파일이 이름을 바꾸며 회전하는 경우 새 활성 파일을 따라가요. Alloy가 멈춰 있는 동안 회전되어 보관 파일로 넘어간 로그까지 복구하지는 않아요.

로그 읽기 위치는 Cloud의 수신 확인서가 아니에요. 강제 종료 시 일부 중복·유실이 가능하고, Loki 전송 재시도 한도를 넘기거나 메모리 버퍼가 사라지면 로그가 유실될 수 있어요. 실험적 Loki WAL은 활성화하지 않았어요. 이 구성은 정확히 한 번 전송이나 감사 로그 보존을 보장하지 않아요.

## 배포·중지·문제 확인

이후 CD는 앱 배포 성공 후 Alloy를 검증하고 재생성해요. 설정 파일 내용만 바뀌어도 적용되도록 `--force-recreate`를 사용해요. 데이터 볼륨은 유지해요. Alloy 단계가 실패하면 워크플로는 실패로 표시되지만 이미 성공한 앱을 롤백하지 않아요. Alloy의 자동 롤백은 없으므로 실패 시 이전 설정을 복원하고 다시 실행해요.

```bash
cd /opt/pheeeew
docker compose --env-file .env.alloy -f compose.monitoring.yml ps
docker compose --env-file .env.alloy -f compose.monitoring.yml logs --tail=50 alloy
```

`401/403`이면 토큰·권한·사용자 ID를, DNS·timeout이면 전송 주소와 outbound 연결을 확인해요. 토큰을 바꾸면 GitHub `production`의 `GRAFANA_CLOUD_TOKEN` Secret을 수정하고 다시 배포해요. 서버의 `.env.alloy`만 수정하면 다음 배포에서 GitHub 값으로 덮어써져요.

```bash
docker compose --env-file .env.alloy -f compose.monitoring.yml stop alloy
```

중지는 앱에 영향을 주지 않아요. 다음 CD는 `.env.alloy`를 다시 만들고 Alloy를 실행하므로, 장기간 끌 때는 배포 워크플로의 Alloy 배포 단계도 함께 비활성화해야 해요. Secrets를 삭제하면 운영 배포 자체가 실패하므로 중지 수단으로 사용하지 않아요. `down -v`로 수집 위치·WAL 볼륨을 삭제하지 않아요.

## Cloudflare와 AWS

SSH를 위한 Cloudflare 터널은 그대로 사용해요. 수집은 `Alloy → Docker 내부 app:8080`으로 이루어져 nginx나 Cloudflare Access를 통과하지 않아요. 공개 nginx의 Actuator 차단을 해제하거나 8080·12345 인바운드 포트를 열 필요가 없어요.

외부 전송은 `Alloy → Grafana Cloud HTTPS(443)` 방향이에요. EC2의 DNS와 outbound HTTPS 연결은 필요하며, outbound를 제한했다면 실제 두 전송 호스트에 대한 허용을 확인해요. SSH용 Cloudflare 서비스 토큰을 Grafana 인증에 사용하지 않아요. 기존 인바운드·터널 설정은 변경하지 않아요.

## 검증 범위

2026-09-11, ARM64의 Alloy v1.19.2와 격리된 Docker 가짜 앱·수신 서버로 아래를 확인했어요. 실제 토큰과 Grafana Cloud는 사용하지 않았어요.

- Alloy 문법·Compose 설정·배포 YAML 및 shell 문법 검사 통과.
- 가짜 Secrets로 환경 파일 생성·권한 `600`·특수 문자 보존, 필수 값 누락·잘못된 형식일 때 중단, 임시 파일 정리 확인.
- 실제 60초 수집과 지표 허용 목록, 공통 라벨 중복 방지 확인.
- 허용된 HTTP 오류·느린 로그만 전송하고 나머지 logger·event·잘못된 JSON을 제외하는 것을 확인.
- 실행 중 파일 회전과 정상 재시작 후 이어 읽기, WAL·위치 파일 생성 확인.
- 전용 사용자·자원 제한·준비 상태 검사 적용 확인. 가짜 부하의 최종 메모리 사용량은 약 43.5MiB였으며 운영 사용량을 예측하는 부하 테스트는 아니에요.

운영 배포, 실제 Cloud 인증·수신, 운영 자원 여유, 장시간 장애·강제 종료에 대한 복구 검증은 아직 수행하지 않았어요.

- [Alloy prometheus.remote_write](https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.remote_write/)
- [Alloy loki.source.file](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.file/)
- [Alloy loki.process](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.process/)
- [Docker Compose 환경 파일 문법](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
