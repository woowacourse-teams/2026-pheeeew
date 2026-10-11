# 독립 DB 대기 표본 수집기

`snapshot.sql`은 현재 DB에서 자신을 제외한 세션의 상태를 JSON 한 건으로 반환한다.
독립 연결의 새 read-only 트랜잭션마다 `statement_timeout=500ms`로 실행한다.
`main.py`가 별도 psql 연결로 수집해 제한된 JSONL 파일에 저장한다. 성공 후 10초, 실패 후 30초 대기하며 실행은 겹치지 않는다.

- `visibility=partial`: 상태를 알 수 없는 세션 또는 활동 추적이 꺼진 세션이 있다. 빈 결과와 다르다.
- `unknown_state_count`는 권한 제한 등을 포함한다. `read_all_stats=false`여도 같은 역할의 세션은 보일 수 있다.
- `eligible_count`는 보이는 nonidle 후보 수, `omitted_count`는 20개 제한으로 생략한 후보 수다. idle은 전체 `session_count`에만 포함된다.
- Lock 대기를 먼저 선택하며 세션당 blocker를 한 번 조회한다. PID는 중복 제거 후 최대 5개이고 `blocker_count`는 제한 전 수다. 미조회는 null, 조회했지만 없는 경우는 빈 배열이다. PID 0은 준비된 트랜잭션일 수 있다.
- 5개 제한은 출력 상한이다. `pg_blocking_pids` 내부 비용까지 제한하지는 않는다. 수집 주기는 별도 검토한다.

active의 `query_elapsed_ms`는 쿼리 시작 이후 경과 시간이고 순수 CPU 시간이 아니다.
`state_elapsed_ms`는 상태 변경 이후 경과 시간이며 현재 wait event의 지속 시간이 아니다.
여러 시간은 중첩되므로 합산하지 않는다. wait event가 없다고 CPU 병목으로 확정하지 않는다.
전체 DB의 원자적 스냅샷은 아니다. 조회 중 상태 변경으로 생긴 음수 경과 시간만 0으로 보정하고 알 수 없는 시간은 null로 남긴다.
PID와 `backend_start`는 DB 세션 표본 식별용이며 HTTP 요청 ID와 일대일 연결하거나 메트릭/Loki 라벨로 쓰지 않는다.
SQL 원문·사용자 이름·application_name·클라이언트 주소·요청 값은 출력하지 않는다.

검증: `./gradlew test --tests com.pheeeew.monitoring.DbActivitySnapshotIntegrationTest`
해석 근거: [PostgreSQL 17 통계 가시성](https://www.postgresql.org/docs/17/monitoring-stats.html), [blocker 함수](https://www.postgresql.org/docs/17/functions-info.html).

## 파일 보관 코어 (3b-2)

`store.py`는 전용 디렉터리의 UTC 시간별 `sample-YYYYMMDDHH.jsonl`에 기록한다.
시작 시와 기록 전에 버킷 시작 시각으로 24시간이 지난 파일을 삭제한다. 실제 표본 보관은 대략 23~24시간이며 정리 간격만큼 늦어질 수 있다. 중단 중에는 정리하지 않고 재시작 시 먼저 정리한다.
소유한 JSONL 총량 32MiB를 넘게 될 때는 더 일찍 오래된 버킷부터 삭제한다. 단일 기록은 64KiB 이하다. 알 수 없는 파일은 삭제·용량 집계 대상이 아니다.
같은 디렉터리의 영구 `.lock`에 비차단 `flock`을 적용한다. 새 디렉터리는 0700, 새 파일은 0600이며 다른 디렉터리의 실행은 차단하지 않는다.
부분 쓰기나 저장 실패는 수집 루프를 중단시킨다. 재시작 때 마지막 64KiB 안에서 불완전한 마지막 줄만 제거하고 완성된 줄은 보존한다. 범위를 넘는 손상과 비정상 버킷 파일은 복구하지 않고 중단한다.
매 기록마다 `fsync`하지 않으므로 호스트 강제 종료·전원 장애 시 최근 기록의 내구성은 보장하지 않는다. 디렉터리는 수집기 전용이며 외부 프로그램이 동시에 수정하지 않아야 한다.

로컬 검증: `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s docker/db-observer -p 'test_*.py' -v`

## 독립 실행과 검증

기존 앱/Alloy 배포에는 자동 연결하지 않는다. `compose.yml`은 별도 프로젝트 이름으로 실행한다. 앱 Hikari 풀을 사용하지 않지만 DB의 연결 슬롯과 자원은 사용한다. 새 연결 실패도 `psql_failed` 등 고정 사유로 파일에 남기며 원본 오류는 버린다. 연결 제한 3초, 조회 500ms, 잠금 획득 100ms, 로컬 psql 실행 4초 상한이다. 네트워크 단절 때 서버 backend의 즉시 소멸까지 보장하지 않는다.

1. 별도 환경 파일에 `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGSSLMODE`를 설정한다. 비밀번호는 넣지 않는다. 운영 TLS는 기존 접속 정책을 유지한다.
2. 별도 pgpass 파일에 `host:port:database:user:password`를 기록한다. `:`와 `\`는 libpq 규칙에 따라 역슬래시로 이스케이프한다. 파일은 **UID 10001 소유·0600인 일반 파일**이어야 하며 읽기 전용으로 마운트한다. 시작 시 조건이 다르면 중단한다.
3. 아래 변수에 절대 경로와 기존 DB 네트워크 이름을 지정하고 별도 프로젝트로 실행한다. 환경별 프로젝트 이름/볼륨을 공유하지 않는다.

```bash
export DB_OBSERVER_ENV_FILE=/absolute/path/.env.observer
export DB_OBSERVER_PASSFILE=/absolute/path/db.pass
export DB_OBSERVER_NETWORK=pheeeew-dev_default
docker compose -p pheeeew-db-observer-dev -f docker/db-observer/compose.yml up -d --build
```

SIGTERM/SIGINT는 수집 중단 신호로 처리하고 직접 실행한 psql을 회수한 뒤 종료한다. 저장·시작 실패는 고정 `collector_stopped` 이벤트를 stderr에 한 번 남기고 exit 1로 종료한다. 자동 재시작은 하지 않으므로 운영자는 종료 상태와 파일의 마지막 수집 시각을 확인해야 한다. 빈 JSONL은 정상 판정이 아니다. 원인 확인 후 같은 `up -d`로 재시작하며 `down --volumes`는 보관 이력을 삭제하므로 사용하지 않는다.

파일은 컨테이너 `/data`의 named volume에만 남는다. Grafana/Loki 전송은 아직 연결하지 않았으며 장애 때 시간대로 찾아 비교하는 로컬 증거다. 예: `docker compose -p pheeeew-db-observer-dev -f docker/db-observer/compose.yml cp db-observer:/data ./observer-evidence`. 수집기 메모리 64MiB·CPU 0.25·PID 16개 제한은 초기 경계이며 실제 서버 수신/운영 영향은 별도 검증한다.

`python3 docker/db-observer/verify_runtime.py`는 로컬 Docker에 전용 PostgreSQL 17/PostGIS 3.5와 수집기를 생성해 실제 실행·보관·대기 표본·종료를 검증한다. 필요한 이미지를 빌드/다운로드하고 테스트 컨테이너·네트워크·볼륨은 finally에서 제거한다. 개발/운영 서버에는 접속하지 않는다.
