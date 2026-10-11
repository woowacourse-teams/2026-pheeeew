# 공통 관측 모듈

`BE` 루트는 실행 가능한 Spring Boot 앱이고, `observability`는 앱이 사용하는 Java 라이브러리입니다.
의존 방향은 **앱 → observability**이며 실행 JAR, JVM, 앱 컨테이너는 각각 하나입니다.
코드 경계를 나눈 것으로, 요청 처리나 DB 작업을 비동기화하거나 성능을 격리한 것은 아닙니다.
범용 로그·시간 기록뿐 아니라 앱 도메인 타입에 의존하지 않는 Pheeeew 업무 지표도 포함합니다.

## 책임 경계

| 위치 | 책임 | 주요 클래스·파일 |
| --- | --- | --- |
| 모듈 | 동기 Servlet 요청의 문맥과 시간 누적 | `RequestTiming`, `RequestTimingStage`, `RequestLoggingFilter` |
| 모듈 | 연결 획득 성공·실패 계측과 DataSource 래핑 | `DataSourceTimingStage`, `RequestTimingDataSource`, `RequestTimingDataSourceConfiguration` |
| 모듈 | 요청·예외·예약 작업 오류의 로그 형식 | `RequestLogPolicy`, `RequestLogWriter`, `ExceptionLogFormatter`, `ScheduledTaskErrorHandler` |
| 모듈 | 도메인 타입에 독립적인 지표 등록·누적 | `EmotionMetrics`, `RegionQueryMetrics`, `DeviceChallengeMetrics`, `EmotionReportMetrics`, `EmotionPressMetrics`, `GroupPressMetrics` |
| 모듈 | 활동 기록·집계의 실패 카운터 | `DeviceActivityRecordMetrics`, `DeviceActivityAggregationMetrics` |
| 앱 | 허용할 요청 값과 출력할 단계 목록 | `PheeeewRequestLogPolicy`, `PheeeewRequestTimingStage` |
| 앱 | 업무 계측 지점과 필터·스케줄러 등록 | `RequestServiceTimingAspect`, 업무별 metrics aspect, `RequestLoggingConfiguration`, `SchedulingConfig` |
| 앱·운영 구성 | 프로필, 로그 출력·비동기 큐, 수집·대시보드 | `application*.yml`, `logback-spring.xml`, `docker/alloy/`, 외부 수집기·Grafana |

모듈은 앱의 업무 클래스나 설정을 참조하지 않습니다. `common/logging`이라는 패키지 이름만으로
소속을 판단하지 말고 소스 디렉터리와 위 책임을 함께 확인합니다. 기존 logger 이름과 요청 속성 키를
유지하기 위해 양쪽의 Java 패키지 `com.pheeeew.common.logging`은 유지했습니다.
업무 지표도 기존 패키지를 유지합니다. 지표를 기록할 시점은 앱의 서비스·Aspect가 결정하고,
모듈은 전달받은 건수·시간을 누적합니다. 개인·그룹 프레스는 앱의 `PressCounts`에서 적용 수·상태 수·
감정별 제한 초과량·전체 제한 초과량을 꺼내 `EmotionPressMetrics`·`GroupPressMetrics`에 전달합니다. 제한 계산과 기록 시점은
앱에 남으며, 적용 수가 0이면 빈 요청만 기록하고 제한 초과량은 양수일 때만 기록합니다.
활동 집계 객체나 인증 거절 사유를 받는 지표는 아직 앱에 남아 있습니다.
활동 기록기는 실패 카운터만 모듈에 위임합니다. 캐시 획득·해제, 비동기 작업 제출과 실패 후 재시도,
오류 로그의 민감정보 제외와 60초 제한은 앱의 `DeviceActivityRecorder`가 유지합니다.
집계 스케줄러는 실패 카운터를 `DeviceActivityAggregationMetrics`에 위임합니다. 초기화 재시도,
현재 집계 반영 후 과거 집계 처리, 실패 로그와 실행 주기는 앱에 유지합니다. 활동 Gauge는 아직 앱에 있습니다.

## 등록과 호출

- 앱의 `RequestLoggingConfiguration`이 앱 정책을 Writer에 전달하고 `requestLoggingFilter` 빈 하나를 만듭니다.
  모듈 Filter에는 `@Component`가 없습니다. Boot의 필터 등록을 사용하고 기존 순서
  `Ordered.HIGHEST_PRECEDENCE + 10`을 유지합니다.
- Filter는 새 `correlationId`를 MDC와 `X-Correlation-ID` 응답 헤더에 넣고 요청별 시간 수집을 시작합니다.
  후속 처리가 끝나면 로그를 작성하고 기존 요청 속성·MDC를 복원합니다. 요청으로 받은 추적 ID는 재사용하지 않습니다.
- `RequestTiming.record`는 `Enum`이면서 `RequestTimingStage`를 구현한 단계만 받습니다.
  요청 문맥이 없거나 시간이 음수이면 기록하지 않습니다. 임의 문자열이나 사용자 입력을 단계로 받지 않습니다.
- `RequestTimingDataSourceConfiguration`은 현재 앱의 컴포넌트 스캔으로 발견됩니다. 정적 BeanPostProcessor가
  이름이 `dataSource`인 Hikari 빈만 한 번 감쌉니다. 우선순위는 `HIGHEST_PRECEDENCE`로 Boot의 lazy 연결
  프록시 안쪽에 위치합니다. 연결·예외는 그대로 전달하고 원본 풀의 종료는 Spring에 맡깁니다.
- 앱의 `SchedulingConfig`는 기존 `ThreadPoolTaskSchedulerCustomizer`에서 모듈의 오류 처리기를 설치합니다.
  작업 오류와 로그 생성 오류를 처리한 뒤 반복 작업의 다음 실행을 막지 않습니다.
- 업무 지표 6개와 `DeviceActivityRecordMetrics`는 `@Component`와 `com.pheeeew` 하위 패키지로 앱 스캔에 등록됩니다.
  모듈은 공개 생성자와 `Timer.Sample` 반환 타입에 필요한 `micrometer-core`를 `api`로 제공합니다.
  버전은 앱과 같은 Spring Boot BOM을 따르며 Prometheus exporter 설정은 앱에 둡니다.
- `DeviceActivityAggregationMetrics`는 스케줄러와 동일하게 `prod` 프로필에서만 스캔 등록됩니다.
  기본·`dev` 프로필에는 집계 실패 카운터를 새로 노출하지 않습니다.

`RequestTiming.begin/restore`와 Filter·Writer 생성자는 모듈 밖에서 호출하는 공개 경계입니다.
DevTools에서 앱과 라이브러리의 클래스 로더가 갈릴 수 있으므로 같은 패키지라는 이유로 package-private
접근에 의존하면 안 됩니다. `RequestLogPolicy`의 실제 구현과 업무 단계 enum은 앱에 둡니다.

## 유지하는 로그·계측 계약

- 요청 로그는 5xx에서 `http_request_failed`, 그 외 1,000ms 이상에서 `http_request_slow`를 남깁니다.
  느린 5xx도 오류 로그 한 번만 남깁니다. 로그 생성 실패는 `http_request_log_skipped`로 처리합니다.
- `method`, 경로 템플릿 `route`, `status`, `durationMs`, `correlationId`와 기존 logger 이름을 유지합니다.
  매핑되지 않은 경로·알 수 없는 메서드 원문은 각각 `UNMAPPED`, `OTHER`로 대체합니다.
- 오류 로그의 요청 값은 앱 허용 목록의 경로 변수·쿼리 값만 사용합니다. 경로 변수가 우선하고,
  같은 쿼리 키는 첫 값만 읽습니다. 요청 본문·인증 헤더·쿠키·좌표는 수집하지 않습니다.
  허용 목록이나 값 형식을 넓히는 것은 별도 정책 변경입니다.
- 최초 50개 원인에서 `SQLException`을 찾으면 감싼 예외까지 메시지를 제외하고 유효한 SQL 상태
  또는 `unknown`을 남깁니다. 이 범위를 넘는 원인까지 검사한다는 보장은 없습니다.
  그 외 예외 메시지는 기존대로 기록하므로 새 예외 메시지에 민감한 값을 넣지 않아야 합니다.
- `timings`는 공통 DB 단계 2개 다음 앱 단계 12개의 고정 순서로 출력합니다. 측정된 허용 단계만
  `count`, `totalMs`, `maxMs`로 남깁니다. 서비스 단계는 내부 단계를 포함할 수 있어 합산하지 않습니다.
- DB 획득 시간은 두 `getConnection` 호출의 경과 시간입니다. 풀 대기뿐 아니라 내부 검증 등을 포함하며,
  SQL 실행 시간·순수 대기 시간·연결 점유 시간과 다릅니다. `hikaricp_connections_usage_seconds`는 별도 지표입니다.
- `scheduled_task_failed`, `scheduled_task_log_skipped`와 예약 작업 logger 이름을 유지합니다.
  로그 비동기 큐의 크기·유실·종료 대기 정책과 Alloy 허용 목록은 앱·운영 구성에 그대로 남습니다.
- 업무 지표의 이름, 설명, 타입, 라벨, 사전 등록 조합과 집계 설정을 유지합니다.
  `pheeeew.sigh.list.results`의 `page`·`has_next`, `pheeeew.region.query`의 `operation`·`outcome`과
  그룹 프레스의 `format=counts/state`·`limit=per_state/total`도 유지합니다.
  지역·개인 프레스 조회의 50ms~300초 진단용 버킷 경계를 바꾸지 않습니다. 새 라벨이나 시계열은 추가하지 않습니다.
- 활동 기록 실패는 `pheeeew.activity.record.failures`의 `reason=save_failed/queue_rejected` 두 카운터입니다.
  둘 다 0으로 사전 등록하며, 캐시 해제 뒤 로그 제한 검사 전에 증가시켜 로그가 생략돼도 실패는 계속 집계합니다.
- 집계 실패는 라벨 없는 `pheeeew.activity.aggregation.failures`를 0으로 사전 등록합니다.
  초기화·현재 집계·과거 집계 중 예외가 발생하면 기존 catch 지점에서 한 번 증가하고 로그를 남깁니다.

현재 문맥은 동기 Servlet 요청 하나에 대한 기록입니다. 같은 요청의 비동기·병렬 기록이나 MDC 전파를
지원하는 범용 추적 시스템이 아닙니다. JDBC 실행 추적, 수집기 교체, SQL 최적화·캐시·풀 크기 조정은 이 분리의 범위 밖입니다.

## 빌드와 검증

명령은 `BE`에서 실행합니다. Java 21과 Docker가 필요하며 DB 통합 검증은 PostgreSQL 17/PostGIS 3.5
Testcontainers를 사용합니다. H2로 대체하지 않습니다.

```sh
./gradlew test --no-daemon
./gradlew :bootJar --no-daemon
docker build --platform linux/arm64 -f docker/Dockerfile -t pheeeew-observability:local .
./gradlew :observability:dependencies --configuration runtimeClasspath --no-daemon
```

루트의 `test` 명령은 양쪽 테스트 태스크를 선택합니다. 리포트는 `build/reports/tests/test`와
`observability/build/reports/tests/test`에 생기며 CI도 두 경로를 보관합니다.
실행 JAR의 `BOOT-INF/lib/observability-*.jar`에 공통 클래스가 있고, `BOOT-INF/classes`에는 같은 클래스의
복사본이 없어야 합니다. 모듈에는 앱을 향하는 `project` 의존이 없어야 합니다.

| 검증 | 보호하는 계약 |
| --- | --- |
| 모듈의 Formatter·RequestTiming·DataSource 테스트 | 예외 형식, 요청별 누적, 연결·예외 보존, 중복 래핑 방지 |
| 앱의 `RequestLoggingFilterTest`, `RequestLoggingConfigurationTest` | 앱 정책, 문맥 정리, 클래스 로더 경계, 필터 단일 등록·순서 |
| 앱의 `RequestTimingDataSourceIntegrationTest` | Flyway/JPA, 트랜잭션, 풀 반환·회복, Hikari 지표, HTTP 로그 연결 |
| 앱의 `AsyncLoggingConfigurationTest`, `ScheduledTaskErrorHandlerTest` | 비동기 출력 계약, 실제 스케줄러의 실패 후 다음 실행 |
| 모듈의 `IndependentMetricsTest` | 앱 타입 없이 스캔한 지표의 기존 이름·라벨과 기록 결과 |
| 모듈의 `EmotionPressMetricsTest`, `GroupPressMetricsTest` | 빈 요청, 적용·상태 수, 두 제한 초과량, 그룹 요청 형식·교착 오류의 누적 계약 |
| 모듈의 `DeviceActivityRecordMetricsTest`, 앱의 `DeviceActivityRecorderTest` | 실패 라벨·초기값·누적, 비동기 제출·캐시 해제·재시도와 로그 제한 |
| 모듈의 `DeviceActivityAggregationMetricsTest`, 앱의 스케줄러·집계 서비스 테스트 | 프로필별 지표 노출, 초기화·집계 재시도, 성공 결과 보존과 트랜잭션 경계 |
| 업무 서비스·metrics 테스트 | 업무별 계측 단계와 기존 기능의 연결 |

실제 기동을 다시 검증할 때는 운영 DB·키 대신 일회용 DB와 생성한 테스트 키를 사용합니다.
`bootRun`에는 `SPRINGDOTENV_ENABLED=false`와 명시적인 테스트 환경을 전달하고, 로그 파일명과 회전 경로를
임시 디렉터리로 지정합니다. `prod` 프로필 검증에는 필수 Play Integrity 설정도 가짜 서비스 계정과
프로젝트 번호로 채우며, 실제 인증·S3 외부 호출은 검증 요청에서 제외합니다.

HTTP health와 앱 버전 조회, 서로 다른 추적 ID, 오류·느린 요청의 단일 JSON 이벤트,
Prometheus의 HTTP/Hikari 지표를 함께 확인합니다. SQL 실패·잠금 지연을 넣을 경우 소유한 일회용 DB에만
적용하고 제한 시간·복원·프로세스 및 컨테이너 정리를 포함합니다. 이 검증은 운영 장애 재현이나 성능 보장이 아닙니다.

## 2026-10-11 로컬 검증 결과

대상은 모듈 분리 코드 커밋 `03dba1d7`입니다. 운영 DB·외부 서비스·배포 환경에는 적용하지 않았습니다.

- `./gradlew test --no-daemon`: 성공. 앱 2,175개와 모듈 33개, 총 2,208개 통과이며 실패·오류는 없습니다.
  모듈 테스트는 직전 동일 코드 검증 결과를 재사용해 `UP-TO-DATE`였습니다.
  `SGIS_SOURCE_DIRECTORY`, `MONITORING_REPRO_IMPORT_SQL`이 필요한 테스트 각 1개는 환경 조건에 따라
  건너뛰었습니다. 실제 전국 경계 적재와 부하 실험을 실행한 결과는 아닙니다.
- `bootJar`와 ARM64 Docker 이미지 빌드 성공. 공통 클래스는 중첩 라이브러리 JAR에 한 번씩만 존재하며,
  앱 클래스 영역에 복사본이 없습니다. 모듈의 runtimeClasspath에 루트 프로젝트 의존이 없습니다.
- 일회용 PostgreSQL 17/PostGIS 3.5 DB로 실행 이미지의 `prod` 프로필과 `bootRun`의 `dev` 프로필을 기동했습니다.
  두 경로 모두 health 200, 앱 버전 조회 200, 잘못된 플랫폼 400, 요청별 추적 ID 분리를 확인했습니다.
- 일회용 DB의 테이블을 잠시 개명한 SQL 실패에서 HTTP 500과 `sqlState=42P01`을 확인했습니다.
  각 응답 추적 ID당 오류 JSON은 1개이고, 허용한 `platform`만 남으며 좌표·DB 메시지는 제외됐습니다.
- 2초 동안 테이블 잠금을 유지한 요청은 HTTP 200과 느린 요청 JSON 1개를 남겼습니다.
  이미지의 HTTP 경과 시간은 2,015ms, `bootRun`은 1,991ms였고 각 DB 획득 단계의 `count`는 1이었습니다.
  지연은 연결 획득 후 SQL 잠금에서 발생하도록 만든 것이며 풀 대기 재현 결과가 아닙니다.
- 두 경로에서 Hikari active/max/acquire/usage, HTTP 요청, 앱 버전 지표를 Prometheus 응답으로 확인했습니다.
  종료 후 소유한 앱 프로세스·컨테이너·네트워크·테스트 키를 정리하고 앱 포트가 닫혔음을 확인했습니다.
- `bootRun`의 `restartedMain` 기동과 실제 HTTP 로그 출력을 확인했습니다. 파일 수정에 따른 DevTools 자동
  재시작까지 실행한 것은 아닙니다. 비동기 로그 정책과 예약 작업의 실패 후 재실행은 자동 테스트로 검증했습니다.

검증 준비 중 추가한 `SPRING_DATASOURCE_HIKARI_CONNECTION_INIT_SQL`은 이미 시작된 풀의 설정 바인딩에
실패했습니다. 해당 검증용 설정은 제거하고 일회용 DB 역할에 `statement_timeout=5s`를 설정했습니다.
이 환경변수 경로의 원인이나 변경 전 버전과의 차이는 확정하지 않았으며, 제품 설정을 수정하지 않았습니다.
