# Pheeeew 운영 대시보드와 알림

`pheeeew-prod.json`은 Grafana Cloud로 가져올 대시보드예요. JSON에는 토큰이나 특정 계정의 데이터 소스 ID가 없어요. 알림은 아래 기준으로 Grafana Cloud 화면에 설정했어요. 이 파일을 배포한다고 Cloud 대시보드나 알림이 자동 생성되지는 않아요.

## JSON 파일의 역할

`pheeeew-prod.json`에는 **Grafana가 어떤 데이터를 어떤 그래프로 보여줄지** 적혀 있어요. 화면에서 패널을 하나씩 만드는 대신, 이 파일을 가져와 미리 구성한 화면을 만들 수 있어요. 실제 지표·로그 데이터는 Grafana Cloud에 있고, JSON에는 데이터를 조회하는 방법과 화면 설정이 들어 있어요.

```text
pheeeew-prod.json을 Grafana에 가져와요
    ↓
지표·로그 데이터 소스를 선택해요
    ↓
Grafana가 패널별 쿼리로 Cloud에 저장된 데이터를 읽어요
    ↓
요청량·응답 시간·오류·로그를 화면에 표시해요
```

| 설정 | 역할 |
| --- | --- |
| `title`, `uid` | 대시보드 이름과 고유 식별자예요 |
| `panels` | 데이터 패널 17개와 구역 제목·안내 7개로 구성된 목록이에요 |
| `datasource`, `targets[].expr` | 어떤 저장소에서 어떤 쿼리로 데이터를 읽을지 정해요 |
| `gridPos`, `fieldConfig` | 패널의 위치·크기, 초·바이트·백분율 같은 표시 단위를 정해요 |
| `time`, `refresh` | 처음 볼 시간 범위와 화면을 새로 조회할 주기를 정해요 |

상단 `API 요청 · 선택 기간`은 선택한 시간 범위에서 Counter의 증가량을 추정해 보여줘요. `서버 오류율 · 선택 기간`과 `API p95 · 선택 기간`도 같은 범위를 사용해요. 아래 추이 그래프는 이동 구간의 증가율인 `rate`로 요청/초와 지연 변화를 보여줘요. 수집 연결·힙 사용률·DB 연결 대기는 현재 값이에요. 화면을 1분마다 새로고침해도 앱이나 Alloy의 수집 주기가 바뀌지는 않아요.

`${DS_PROMETHEUS}`와 `${DS_LOKI}`는 가져올 때 선택한 데이터 소스로 바뀌는 자리예요. 이 파일에 Grafana 토큰을 입력할 필요는 없어요. 알림 규칙과 알림 수신자는 아래 절차로 따로 설정해요.

로컬 IntelliJ의 앱 실행에는 이 파일이 필요하지 않아요. 운영 배포도 이 JSON을 자동으로 읽거나 Grafana에 등록하지 않아요. 저장소에 두는 이유는 팀이 같은 화면을 다시 만들고, 쿼리·배치 변경을 코드 리뷰로 확인할 수 있게 하기 위해서예요. Grafana 화면에서 수정한 내용이 저장소 JSON에 자동 반영되지는 않으므로, 유지할 변경은 다시 내보내 저장소에도 반영해요.

## 대시보드 연결

1. 운영 배포 후 Explore에서 `up{job="pheeeew-api",environment="prod",instance="pheeeew-prod"}`가 `1`인지 확인해요. 연결이 아직 없으면 [Alloy 연결 안내](../alloy/README.md)를 먼저 따라요.
2. Grafana의 `Dashboards → New → Import`에서 `pheeeew-prod.json`을 올려요.
3. `운영 지표 저장소`에는 기존 스택의 Prometheus 데이터 소스를, `운영 로그 저장소`에는 Loki 데이터 소스를 선택해요. Grafana Cloud가 제공하는 저장소를 선택하는 것이며 Prometheus 서버를 설치하는 단계가 아니에요.
4. [Alloy 설정](../alloy/config.alloy)의 `prometheus.scrape "app"`에서 `scrape_interval = "60s"`인지 확인해요. 이 값이 앱 지표의 실제 수집 주기예요. 가져온 화면에서는 최근 1시간을 보고 새 요청이 발생한 뒤 2~5분 기다려요.

Grafana의 Prometheus 데이터 소스에 있는 `Scrape interval`도 실제 수집 주기에 맞춰 `60s`로 설정해요. 이 값은 Grafana가 조회 간격 등을 계산할 때 참고하는 설정이며, 바꿔도 Alloy의 실제 수집 주기는 바뀌지 않아요. 대시보드의 각 지표 쿼리는 최소 간격이 이미 `1m`로 설정되어 있어요. [Grafana 데이터 소스 설정 안내](https://grafana.com/docs/grafana/latest/datasources/prometheus/configure/#interval-behavior)

대시보드 UID는 `pheeeew-prod-overview`예요. 같은 UID가 이미 있다면 기존 팀 대시보드를 덮어쓸지 검토해요. JSON 가져오기는 대시보드만 생성하며 알림·수신자는 만들지 않아요.

## 어떤 순서로 볼까요?

| 화면 | 확인할 내용 |
| --- | --- |
| 전체 상태 | 수집 성공 여부, 선택 기간의 추정 API 요청 수·5xx 비율·p95, 현재 힙 사용률·DB 연결 대기 |
| HTTP와 지도 | 경로별 요청량, API 응답 p95·p99, 지도 HTTP 응답과 Repository 조회 시간 |
| 지도 결과 | 평균 반환 개수, 500개 제한으로 잘린 완료 호출 비율 |
| 앱 자원 | JVM 힙·프로세스 CPU, HikariCP 사용 중·유휴·최대 연결과 대기 |
| HTTP 로그 | 오류·1초 이상 느린 요청의 JSON, 추적용 correlationId |

지도 조회는 현재 `GET /api/v1/sighs`예요. 전체 API 패널과 오류 알림은 `uri=~"/api/.*"`로 Actuator 수집 요청을 제외해요. 경로가 매핑되지 않아 `UNKNOWN`·`NOT_FOUND` 등으로 기록되는 요청도 제외되므로, 서버가 받은 모든 요청의 통계는 아니에요. 엔드포인트가 바뀌면 지도 쿼리를 함께 수정해요.

지도 평균 반환 개수는 `results_sum / results_count`의 증가율 비율이에요. 결과 개수를 합친 값을 조회 횟수로 나누므로, 예를 들어 두 호출이 각각 100개와 500개를 반환하면 평균은 300개예요. `truncated=true`는 조회 결과를 500개로 제한했다는 뜻이며 서버 오류가 아니에요.

API 흐름 → 지도 조회 → 앱 자원 → 문제 로그 순서로 구역을 나눴어요. 상단 상태는 민트색을 기본으로 사용하고 주의·위험 구간은 노란색·빨간색으로 표시해요. 색상 경계는 초기 참고 기준이며 확정된 SLO가 아니에요. 계산할 표본이 없는 상단 비율·지연은 회색 `표본 없음`으로 구분해요.

**빈 그래프를 0으로 간주하지 않아요.** 최초 연결 전·수집 중단은 데이터 없음이에요. 요청이 없으면 응답 시간과 오류·잘림 비율을 계산할 수 없어요. 요청이 있지만 5xx가 없으면 오류 비율은 0%예요. 로그가 비어 있는 것은 오류·느린 요청이 없기 때문일 수도 있어요.

문제 로그 패널은 `verification=true`인 합성 검증 로그를 제외해요. 검증 로그는 Explore에서 별도로 조회할 수 있으며 실제 장애로 해석하지 않아요.

p95·p99는 버킷 기반 추정값이에요. HTTP의 마지막 유한 버킷은 10초, 지도 Repository 조회는 5초이므로 이를 넘는 지연을 정확히 구분할 수 없어요. 요청이 적으면 특히 p99가 불안정해요. HTTP p95와 Repository p95는 서로 다른 분포이므로 두 값을 빼서 서비스 처리 시간을 구하지 않아요.

## 최초 알림 세 가지

아래 값은 **운영에 적용한 초기 기준이며 확정된 서비스 SLO가 아니에요.** 연결 확인 후 적용했으며 24~48시간 관찰해 요청량·평소 지연에 맞게 조정해요. 다른 환경에서 처음 설정할 때도 연결 확인을 먼저 해요.

모두 `Pheeeew` 폴더의 Grafana-managed alert예요. 지표 데이터 소스는 대시보드에서 선택한 실제 Prometheus 데이터 소스예요. 쿼리 안에 대시보드 변수는 사용하지 않아요. `pheeeew-prod` 평가 그룹에서 1분마다 평가해요. 아래 쿼리 A를 Instant로 실행하고 결과가 `0.5`를 초과하는 것을 조건으로 사용해요. 결과는 0 또는 1이에요.

| 규칙 이름 | 조건 | Pending 기간 | 라벨 |
| --- | --- | --- | --- |
| Pheeeew 수집 중단 | 마지막 up이 0이거나 시계열이 사라짐 | 2분 | service=pheeeew, environment=prod, severity=critical |
| Pheeeew API 5xx 증가 | 최근 5분 오류율 5% 초과이며 추정 5xx 횟수 5회 이상 | 5분 | service=pheeeew, environment=prod, severity=warning |
| Pheeeew 지도 응답 지연 | 최근 5분 지도 HTTP p95가 1초 초과이며 추정 요청 수 100회 이상 | 5분 | service=pheeeew, environment=prod, severity=warning |

`increase`는 수집된 누적값에서 시간 범위 경계까지 추정하므로 정확한 정수 로그 건수와 다를 수 있어요. 최소 요청·오류 횟수는 요청 한두 번으로 알림이 반복되는 것을 줄이기 위한 기준이에요. 저트래픽에서 발생한 소수 오류는 이 알림에 잡히지 않을 수 있으므로 대시보드·로그에서도 확인해요.

### 수집 중단 쿼리

```promql
1 - (max(up{job="pheeeew-api",environment="prod",instance="pheeeew-prod"}) or vector(0))
```

`up=0`은 Alloy가 앱 지표를 읽지 못한 상태예요. EC2 또는 Alloy 자체가 멈추면 새 `up`을 보내지 못하므로 시계열의 부재도 감지해요. 마지막 값이 조회에서 사라지는 데 걸리는 lookback 지연과 Pending 2분 때문에 즉시 알림은 아니에요. 이는 외부 공개 URL의 가용성 검사가 아니며, Cloudflare·nginx만 고장 났을 때는 `up=1`일 수 있어요.

### API 오류 쿼리

```promql
(
  (
    sum(increase(http_server_requests_seconds_count{job="pheeeew-api",environment="prod",instance="pheeeew-prod",uri=~"/api/.*",status=~"5.."}[5m]))
    /
    sum(increase(http_server_requests_seconds_count{job="pheeeew-api",environment="prod",instance="pheeeew-prod",uri=~"/api/.*"}[5m]))
  ) > bool 0.05
  and
  sum(increase(http_server_requests_seconds_count{job="pheeeew-api",environment="prod",instance="pheeeew-prod",uri=~"/api/.*",status=~"5.."}[5m])) >= 5
) or vector(0)
```

### 지도 지연 쿼리

```promql
(
  histogram_quantile(0.95,
    sum by (le) (rate(http_server_requests_seconds_bucket{job="pheeeew-api",environment="prod",instance="pheeeew-prod",uri="/api/v1/sighs",method="GET"}[5m]))
  ) > bool 1
  and
  sum(increase(http_server_requests_seconds_count{job="pheeeew-api",environment="prod",instance="pheeeew-prod",uri="/api/v1/sighs",method="GET"}[5m])) >= 100
) or vector(0)
```

### 데이터 없음·평가 실패와 수신자

수집 중단 규칙은 No Data를 Alerting으로, 다른 두 규칙은 No Data를 Normal로 설정해요. 요청이 없다는 이유로 오류·지연 알림을 보내지 않기 위해서예요. 세 쿼리는 보통 데이터 부재를 0/1로 처리하지만 데이터 소스가 반환하는 빈 결과에 대한 정책도 명시해요. 평가 Error는 세 규칙 모두 Alerting으로 설정해 조회 장애를 정상으로 숨기지 않아요. Error로 발생한 알림은 실제 앱 오류와 구분해 데이터 소스 연결부터 확인해요.

세 규칙에서 이메일 Contact point인 `pheeeew-prod-email`을 직접 선택했어요. 별도 Notification policy 분기 없이 같은 수신처로 보내며 `service`, `environment`, `severity` 라벨은 검색과 분류에 사용해요. 수신 이메일 주소는 Grafana Cloud에서 관리해요. 그룹 대기 30초, 그룹 간격 5분, 재알림 간격 4시간의 기본값을 사용하고, 조건이 해소된 뒤 추가로 Firing을 유지하는 시간은 0초예요. 대시보드 연결용 읽기 권한과 Alloy 전송 토큰은 역할이 다르므로 Alloy 토큰에 알림 관리 권한을 추가하지 않아요.

규칙이 Firing이어도 수신 경로가 없으면 팀이 알아채지 못할 수 있어요. Contact point를 변경할 때는 Test 기능으로 발송하고 수신자가 실제 도착을 확인해요. 운영 API에 오류를 일으켜 테스트할 필요는 없어요. Contact point 테스트는 메시지 전달 경로 검사이며 실제 지표의 조건 충족부터 Pending·Firing 전이까지 검증하는 것은 아니에요.

## 알림이 오면 확인할 순서

| 증상 | 첫 확인 | 다음 확인 |
| --- | --- | --- |
| 수집 중단 | Alloy·앱 컨테이너 상태와 마지막 up 시각 | 401/403은 Grafana 토큰·권한, timeout은 DNS·outbound·앱 응답 확인 |
| API 5xx 증가 | 최근 배포 시각과 오류가 늘어난 경로 | HTTP 오류 로그의 correlationId·예외 종류·위치 확인 |
| 지도 응답 지연 | 지도 요청량, HTTP와 Repository 지연의 동반 상승 | Hikari 대기·앱 CPU·힙 확인. DB 지연이면 쿼리·실행 계획을 별도 조사 |
| 지도 결과 잘림 증가 | 요청한 지도 범위와 평균 반환 개수 | 확대 수준·결과 한도 정책 검토. 오류로 단정하지 않음 |

서버 상태 확인은 기존 Cloudflare SSH를 사용해요.

```bash
cd /opt/pheeeew
docker compose -f compose.yml ps
docker compose --env-file .env.alloy -f compose.monitoring.yml ps
docker compose --env-file .env.alloy -f compose.monitoring.yml logs --tail=50 alloy
docker stats --no-stream
```

로그를 공유할 때 토큰·사용자 입력값이 섞이지 않았는지 확인해요. Grafana Explore에서는 아래처럼 요청 하나를 찾아요. correlationId를 Loki 라벨로 추가하지 않아요.

```logql
{job="pheeeew-api",environment="prod",instance="pheeeew-prod"} | json | correlationId="찾을-번호"
```

CPU는 같은 인스턴스의 시계열에 Cloud 부가 라벨이 추가되어도 범례가 중복되지 않도록 시점별 최댓값을 표시해요. 이는 여러 서버의 CPU를 합산하는 쿼리가 아니에요.

대시보드의 프로세스 CPU·힙·Hikari 지표만으로 EC2 전체 메모리·디스크, CPU 크레딧, RDS 내부 자원을 판단하지 않아요. 현재 수집 범위 밖의 항목은 서버 또는 AWS에서 별도로 확인해요.

## 연결 후 기준값 남기기

정상 운영 24~48시간 뒤, 한산한 시간과 요청이 많은 시간 각각의 API·지도 요청/초, 지도 HTTP·DB p95/p99, 5xx 비율, 힙·CPU·연결 대기, 평균 반환 개수·잘림 비율을 기록해요. 동시에 Grafana Cloud 사용량 화면에서 active series와 로그 수집량을 확인해요. 운영 요청량을 모르는 상태에서 월 비용이나 안전한 자원 여유를 확정하지 않아요.

Cloud 대시보드와 로그 전송, 알림 규칙 등록·정상 평가·이메일 수신처 연결·테스트 메일 도착을 확인했어요. 24~48시간 운영 기준값 측정은 남아 있어요.

## 검증 기록

### 초기 구성의 로컬 검증

2026-09-11 기준으로 다음을 확인했어요. 검증용 컨테이너와 가짜 값만 사용했으며 Cloud 설정은 변경하지 않았어요.

- Prometheus `promtool check rules`: 대시보드 PromQL 22개와 알림 쿼리 3개 문법 통과.
- `promtool test rules`: 정상·무요청·수집 부재·짧은 실패와 복구·지속 오류·소수 오류·지도 지연과 요청 수 조건·반환 평균과 잘림 비율 등 11개 시나리오 통과. 알림은 Grafana의 `Last > 0.5`와 같은 숫자 조건을 적용해 평가했어요.
- Grafana 13.2.1의 대시보드 import API로 등록 후 다시 읽어 15개 패널, Prometheus·Loki 입력 매핑, 쿼리 보존 확인.

위 검증은 초기 15개 패널 구성의 기록이에요. 로컬 검증이 운영 데이터의 정확성이나 실제 알림 도착까지 보장하지는 않아요.

### 운영 데이터와 화면 확인

2026-09-11에 Grafana Cloud로 최종 구성을 가져오고 실제 운영 지표를 확인했어요.

- 데이터 패널 17개와 구역·안내 7개를 구성하고 JSON 문법, 고유 패널 ID, 배치 겹침, 지표 쿼리의 최소 간격 `1m`를 확인했어요.
- `up=1`과 운영 지표 수신을 확인했어요. 운영 지도 API를 한 번 조회해 HTTP 200과 요청 수·지연·지도 결과 지표 반영을 확인했어요.
- 상단 요약, 지도·앱 자원·문제 로그 구역을 Chrome에서 확인했어요.
- 2026년 9월 12일(KST), 검증용 JSON 로그 한 건으로 파일 → Alloy → Loki 전송을 확인했어요. Alloy에서 전송 1건·HTTP 204·재시도 및 전송 손실 0건을 확인했고, Chrome의 Explore에서도 같은 로그 한 건을 찾았어요. `verification=true` 로그가 대시보드의 제외 조건에 걸리는 것도 확인했어요. 앱의 실제 오류·느린 요청을 발생시킨 검사는 아니에요.

### 이메일 장애 알림 확인

2026-09-12에 Chrome에서 세 규칙과 이메일 Contact point를 설정했어요.

- `Pheeeew` 폴더의 `pheeeew-prod` 그룹에 세 규칙이 있고, 1분 주기로 모두 `Normal` 상태인 것을 확인했어요.
- 수집 중단은 Pending 2분·No Data=Alerting, 오류와 지도 지연은 Pending 5분·No Data=Normal이에요. 평가 Error는 모두 Alerting이에요.
- 세 규칙에서 `pheeeew-prod-email` 수신처를 직접 선택했어요. 기존 기본 정책과 다른 알림 규칙은 변경하지 않았어요.
- Contact point의 Test 발송 성공과 수신자의 실제 메일 도착을 확인했어요. 운영 장애를 발생시켜 실제 규칙의 Firing부터 전달까지 재현한 검사는 아니에요.

알림 규칙과 수신처는 Cloud에 저장되어 있어요. 저장소에는 기준과 설정 절차를 기록하며 이 README나 대시보드 JSON을 배포해도 알림이 자동 생성되지는 않아요.

## 참고

- [Grafana 대시보드 가져오기](https://grafana.com/docs/grafana/latest/visualizations/dashboards/build-dashboards/import-dashboards/)
- [Grafana No Data와 Error 처리](https://grafana.com/docs/grafana/latest/alerting/fundamentals/alert-rule-evaluation/nodata-and-error-states/)
- [Prometheus histogram_quantile](https://prometheus.io/docs/prometheus/latest/querying/functions/#histogram_quantile)
