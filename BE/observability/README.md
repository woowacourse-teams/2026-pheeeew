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
| 모듈 | 도메인 타입에 독립적인 지표 등록·누적 | `EmotionMetrics`, `RegionQueryMetrics`, `DeviceChallengeMetrics`, `EmotionReportMetrics`, `EmotionPressMetrics`, `GroupPressMetrics`, `MapCacheMetrics` |
| 모듈 | 지도 캐시의 쓰기 잠금 대기·보정 시간과 결과 기록 | `MapCacheLockMetrics`, `MapCacheMaintenanceMetrics` |
| 모듈 | 플랫폼별 앱 버전 조회 성공 카운터 | `AppVersionMetrics` |
| 모듈 | 기기 인증의 결과 카운터와 고정된 지표 사유 목록 | `AppAttestMetrics`, `AppAttestRejection`, `PlayIntegrityMetrics`, `PlayIntegrityRejection` |
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
`AppAttestRejection`·`PlayIntegrityRejection`은 인증 정책이 아닌 지표의 고정된 `reason` 목록입니다. 인증 검증과 거절 판단,
오류 코드·예외 전파는 앱의 검증기에 남고, 모듈은 앱이 선택한 사유로 카운터만 기록합니다.
앱 버전 조회는 앱의 `AppVersionMetricsFilter`가 경로·HTTP 메서드·응답 상태와 `AppPlatform` 해석을 담당합니다.
모듈의 `AppVersionMetrics`는 고정된 Android·iOS 기록 메서드만 제공하고 HTTP 요청이나 앱 도메인 타입을 받지 않습니다.

### 앱에 유지하는 관측 코드

직접 Micrometer 지표를 등록하는 업무 지표 클래스는 모듈에 있습니다.
앱에 남은 관측 코드는 다음과 같이 업무 결과를 해석하고 기록 시점을 결정합니다.

| 앱 코드 | 유지하는 책임 |
| --- | --- |
| `EmotionMetricsAspect`, `EmotionPressQueryMetricsAspect`, `RegionQueryMetricsAspect` | Repository·Service 포인트컷, 작업 구분, 반환값 해석, 성공·실패 판단과 모듈 호출 |
| `EmotionRegionQueryTimingAspect`, `RequestServiceTimingAspect`, `PheeeewRequestTimingStage` | 요청별 업무 단계와 측정 범위 결정 |
| `AppVersionMetricsFilter` | 앱 버전 조회의 HTTP 성공 조건과 플랫폼 해석 |
| 업무 서비스·인증 검증기 | 제한·신고·인증 판단과 해당 결과의 기록 시점 |

`EmotionMetricsAspect`와 `EmotionPressQueryMetricsAspect`의 `Timer.Sample`은 모듈이 시작한 측정의 핸들입니다.
앱은 이 핸들을 다시 모듈에 전달하며 Timer를 직접 등록하지 않습니다. 포인트컷이나 도메인 DTO까지 모듈로
옮기면 앱 업무 구조를 다시 참조하게 되므로 현재 경계를 유지합니다. HTTP·JVM·Hikari·Spring Data 자동 지표와
프로필별 exporter·공통 태그·버킷 설정, Alloy 허용 목록은 앱·운영 구성에 남습니다.

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
- 업무 지표와 `AppVersionMetrics`, `AppAttestMetrics`, `PlayIntegrityMetrics`는 `@Component`와 `com.pheeeew` 하위 패키지로 앱 스캔에 등록됩니다.
  모듈은 공개 생성자와 `Timer.Sample` 반환 타입에 필요한 `micrometer-core`를 `api`로 제공합니다.
  버전은 앱과 같은 Spring Boot BOM을 따르며 Prometheus exporter 설정은 앱에 둡니다.

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
- 앱 버전 조회는 `pheeeew.app.version.checks`와 `platform=android/ios`를 0으로 사전 등록합니다.
  앱 필터는 컨텍스트 경로를 고려한 `/api/v2/app/version`의 GET 요청이 예외 없이 완료되고 2xx일 때만 기록합니다.
  플랫폼 대소문자 해석과 잘못된 값의 처리도 앱에 유지합니다.
- App Attest는 `pheeeew.device.appattest.accepted`를 라벨 없이 0으로 등록합니다.
  `pheeeew.device.appattest.rejected`는 해당 거절을 처음 기록할 때 생성하며, `reason` 값은 기존 15개
  enum 이름을 대문자 그대로 사용합니다. 사유별 사전 등록이나 새 라벨을 추가하지 않습니다.
- Play Integrity는 `pheeeew.device.attestation.accepted`·`skipped`를 라벨 없이 0으로 등록합니다.
  `rejected`·`verdict`는 기록할 때 생성하며, `reason`은 기존 14개 enum 이름을 그대로 사용합니다.
  `appRecognition`은 기존 네 판정값을 유지하고 그 외 문자열은 `UNKNOWN`으로 묶습니다.
  `deviceIntegrity`는 `true/false`를 유지합니다. 원본 판정에 따른 인증 허용·거절은 앱에서 결정합니다.

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
| 모듈의 `AppAttestMetricsTest`, `PlayIntegrityMetricsTest`, 앱의 등록·인증 라우팅 통합 테스트 | 초기값, 기록 시 등록·사유별 누적, UNKNOWN 제한과 기존 인증 흐름 연결 |
| 업무 서비스·metrics 테스트 | 업무별 계측 단계와 기존 기능의 연결 |
| 모듈의 `AppVersionMetricsTest`, 앱의 필터·컨트롤러 테스트 | 플랫폼별 초기값·누적, HTTP 성공 조건·예외·플랫폼 해석과 미집계 조건 |

실제 기동을 다시 검증할 때는 운영 DB·키 대신 일회용 DB와 생성한 테스트 키를 사용합니다.
`bootRun`에는 `SPRINGDOTENV_ENABLED=false`와 명시적인 테스트 환경을 전달하고, 로그 파일명과 회전 경로를
임시 디렉터리로 지정합니다. `prod` 프로필 검증에는 필수 Play Integrity 설정도 가짜 서비스 계정과
프로젝트 번호로 채우며, 실제 인증·S3 외부 호출은 검증 요청에서 제외합니다.

HTTP health와 앱 버전 조회, 서로 다른 추적 ID, 오류·느린 요청의 단일 JSON 이벤트,
Prometheus의 HTTP/Hikari 지표를 함께 확인합니다. SQL 실패·잠금 지연을 넣을 경우 소유한 일회용 DB에만
적용하고 제한 시간·복원·프로세스 및 컨테이너 정리를 포함합니다. 이 검증은 운영 장애 재현이나 성능 보장이 아닙니다.

## BE 활동 통계 정리

[#736](https://github.com/woowacourse-teams/2026-pheeeew/issues/736)에서 서버 활동 수집·집계와 전용 DTO·변환기·메트릭을 제거했습니다.
앱 버전 호출 계측, HTTP·JVM·Hikari·지도 캐시 지표와 공통 요청·예약 작업 오류 로그는 유지합니다.
활동 테이블·과거 데이터·기존 Flyway는 보존합니다. 코드 제거는 운영 PostHog 수신이나 통계 전환 완료의 증거가 아닙니다.

아래 검증 기록은 당시 커밋에 대한 이력입니다. 삭제된 클래스·설정·활동 지표의 검증 결과는 현재 기능 설명이 아닙니다.

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

## 2026-10-12 지표 이동 완료 후 로컬 검증 결과

대상은 코드 커밋 `0cd2a4f1`입니다. 아래 결과는 이전 검증과 별도로 수행했으며 운영 배포·외부 수집기 수신을
검증한 결과는 아닙니다. 제품 코드와 설정을 변경하지 않고 실행 시 테스트 환경만 주입했습니다.

```sh
./gradlew test :bootJar :observability:dependencies --configuration runtimeClasspath --no-daemon
```

- 1분 21초에 성공했습니다. 앱 테스트 2,173개 중 2,171개 통과·2개 건너뜀, 모듈 64개 통과로
  총 2,235개 통과·2개 건너뜀·실패 및 오류 0개입니다. 모듈 테스트와 `bootJar`는 동일 코드의 직전 결과를
  재사용하여 `UP-TO-DATE`였습니다. 실제 앱 기동에는 이 실행 JAR를 사용했습니다.
- 건너뛴 것은 `SGIS_SOURCE_DIRECTORY`가 필요한 전국 경계 검증과 `MONITORING_REPRO_IMPORT_SQL`이 필요한
  부하 재현 테스트 각 1개입니다. 실제 전국 데이터 적재·부하 실험을 수행한 것은 아닙니다.
- 중첩 `observability` JAR의 클래스 항목 32개가 각각 한 번만 존재하고 앱 클래스 영역의 복사본은 0개입니다.
  업무 지표 클래스 12개가 모듈에 있고 앱 DTO·변환기·스케줄러는 앱에 있습니다. 모듈의 프로젝트 타입 import는
  모두 모듈 내부 타입으로 해석되며, 빌드 및 runtimeClasspath에 앱을 향하는 project 의존이 없습니다.

일회용 PostgreSQL 17/PostGIS 3.5 컨테이너와 메모리에서 생성한 RSA 테스트 키를 사용하여
`java -jar build/libs/pheeeew-0.0.1-SNAPSHOT.jar`를 `prod`, `dev` 순서로 각각 기동했습니다.
DB·HTTP는 루프백의 임시 포트를 사용하고 로그도 임시 디렉터리에 저장했습니다. 수집 확인을 위해 실행 인자로만
`--pheeeew.activity.aggregation-interval=PT2S`를 지정했습니다. 앱 버전 정책 두 행은 일회용 DB에만 넣었습니다.

| 실제 HTTP·Prometheus 검증 | 결과 |
| --- | --- |
| 두 프로필의 `/actuator/health`, `/actuator/prometheus` | 모두 HTTP 200 |
| Android·iOS 앱 버전 조회와 잘못된 플랫폼 조회 | 각각 200·200·400, 두 성공 요청의 추적 ID가 서로 다름 |
| `pheeeew_app_version_checks_total` | 각 플랫폼이 1씩 증가, 잘못된 플랫폼 요청은 증가시키지 않음 |
| 기기 challenge 발급 | HTTP 200, `pheeeew_device_challenge_issued_total` 1 증가 |
| 두 프로필의 업무 지표 | 사전 등록하는 주요 metric family 24개 노출, 활동 기록 실패의 두 reason은 각각 0 |
| `prod` 활동 지표 | Android·iOS DAU·MAU가 모두 실제 0, 집계·수집 시작 시각은 유한한 Unix timestamp, 집계 실패 0 |
| `dev` 활동 지표 | DAU·MAU·집계 시각·수집 시작 시각·집계 실패 지표 모두 미노출 |
| `prod`에서 증명 없는 Android·iOS 등록 요청 | 각각 HTTP 403, Play Integrity·App Attest의 `reason=ATTESTATION_REQUIRED` 카운터가 각각 1로 생성 |
| 지연 등록하는 인증 지표 | 최초 scrape에서 rejected·verdict 미노출; 거절 요청 뒤 해당 rejected만 생성 |
| 자동 지표와 공통 태그 | HTTP 요청과 Hikari active/max/acquire/usage 노출, 모든 업무 지표 sample에 `application=pheeeew`와 프로필별 `environment` 유지 |

인증 성공과 Google·Apple·S3 외부 호출은 이 서버 검증에서 실행하지 않았습니다. KST 자정 전후, 미집계·누락의
`NaN`, 실패 시 마지막 성공 결과 유지, 인증 성공·거절 사유별 누적과 `UNKNOWN` 처리는 자동 테스트로 검증했습니다.
이번에는 Docker 앱 이미지 빌드, `bootRun`/DevTools, SQL 실패·잠금 지연 주입을 다시 수행하지 않았습니다.
종료 후 두 서버 프로세스와 일회용 DB를 제거하고 HTTP 포트가 닫힌 것을 확인했습니다. 테스트 키는 파일로 저장하지 않았습니다.
