# 지도 조회 단계 계측

v2·v4의 동기 지도 GET 요청을 구분한다. 이 문서는 저장소의 계측·수집·대시보드 계약이다.
코드와 JSON 변경만으로 서버 배포·Grafana Cloud 적용·부하 시험을 수행한 것은 아니다.
과거 v2 시험을 새 v4 타이머로 소급 분해할 수 없다.

## 요청 조건과 캐시 경로

`pheeeew_map_read_requests_total`은 대상 요청 종료 시 한 번 기록한다.
태그는 `api_version=v2|v4`, `route=pins|regions`, `query`, `page`, `decision`, `outcome`이다.

| 태그 | 고정 값과 의미 |
| --- | --- |
| `query` | `all_default`, `month_current_default`, `month_other_default`, `other`, `unclassified` |
| `page` | `first`, `next`, `unknown` |
| `decision` | `unclassified`, `eligible`, `cache_attempt`, `cache_hit`, `cache_load`, `cache_fallback`, `bypass_period`, `bypass_filter`, `bypass_transaction`, `bypass_level`, `empty` |
| `outcome` | `success`, `client_error`, `server_error` |

`*_default`는 `scope=ALL`, `contentType=ALL` 조건이다. 월 조건은 KST 달력 기준으로
종료일의 한 달 전부터 종료일까지다. 현재 KST 날짜와 종료일이 같으면 `month_current_default`,
다르면 `month_other_default`다. 앱 기본값과 동일한 날짜 형태라는 뜻이며 기본 버튼 사용을 증명하지 않는다.
기간·scope·content는 검증된 필터와 해석된 cursor에서 분류한다. 날짜 파라미터 없는 후속 페이지도
전체 기간으로 취급하지 않고 cursor의 날짜를 유지한다.

`query!="unclassified"`는 **검증된 조회 조건의 요청** 분모이며 HTTP 성공 요청만의 분모가 아니다.
분류 이후 기기 조회·DB·출력 실패도 알려진 query에 포함된다. 분류 전 인증·입력·cursor 오류는
`unclassified`로 남는다. `decision`은 실제 선택한 경로이고 `outcome`은 측정 필터 복귀 시점의 처리 결과다.
`outcome`은 처리되지 않은 예외 또는 관측된 5xx면 `server_error`, 관측된 4xx면 `client_error`,
그 외에는 `success`다. 컨테이너의 후속 오류 처리나 클라이언트의 응답 수신 완료를 확정하지 않는다.
`eligible`·`cache_attempt`는 후속 실패 등으로 최종 캐시 결과에 도달하지 못한 요청에도 남을 수 있다.
캐시 hit 이후 응답 실패도 가능하다. `bypass_*`는 캐시 미시도, `cache_fallback`은 캐시 시도 후 SQL 전환이다.

대시보드는 기본 한 달 비중(현재 한 달/검증된 조회), 전체 우회율(정책 우회/검증된 조회),
한 달 우회율(현재 한 달 정책 우회/현재 한 달)을 별도로 표시한다. 분모가 0·없음·표본 부족이면 빈 값이다.
관측된 분모가 양수인데 해당 분자 시계열이 없으면 0%로 표시한다. 일부 기간의 수집만으로도 값이
나올 수 있으므로 수집 연결을 함께 확인한다. 기존 `pheeeew_map_cache_requests_total`은 캐시 접근만
세므로 전체 지도 요청의 hit 비율로 해석하지 않는다.

## 단계 시간

`pheeeew_map_read_stage_seconds_{count,sum,bucket,max}`는 **포함 시간**이다.
`api_version=v2|v4|unscoped`, `outcome=success|error`, 아래 고정 `stage`로 구분한다.
`unscoped`는 대상 HTTP 요청 문맥 없이 호출된 단계이며 v2나 v4 요청으로 합치지 않는다.

| stage | 범위 |
| --- | --- |
| `map_pins_controller`, `map_regions_controller` | 컨트롤러 본문. 서비스·응답 DTO 변환 포함, 인자 해석·JSON 출력 제외 |
| `map_pins_service`, `map_regions_service` | 조회 서비스 전체. SQL fallback 포함 |
| `map_pins_device` | 핀 경로의 기기·그룹 검증. 지역 준비 내부 자기 호출은 별도 표본이 아님 |
| `map_regions_prepare` | 기기·그룹 검증, 지역 목록·집계 준비 상태 조회 |
| `map_pins_cache` | 스냅샷·필요 시 재생성·차단 조회·후보 선택. 정상 반환이 hit를 뜻하지 않음 |
| `map_regions_cache` | 스냅샷·필요 시 재생성. fallback 신호도 정상 반환 가능 |
| `map_blocks_lookup`, `map_region_list` | 차단 JDBC 및 지역 목록 조회. 연결 획득·통신·매핑 포함 |
| `map_pins_response` | 캐시 결과의 스탬프 조회·페이지 DTO·cursor 조립 |
| `map_pins_db_path`, `map_regions_db_path` | DB 경로 서비스 전체. 검증·조회·매핑·페이지 조립을 포함하며 순수 SQL 시간이 아님 |
| `map_jwt_decode` | JWT decode 전체. 서명·클레임 검증을 포함하며 서명 시간만이 아님 |
| `map_filter_total` | 지도 측정 필터에서 관측한 처리 범위. 공통 HTTP 관측과 경계가 다를 수 있음 |
| `map_pre_mvc` | 측정 필터 진입부터 MVC 진입까지. 보안 필터·인증·디스패치·매핑 포함 |
| `map_mvc_pre_controller` | MVC 진입부터 컨트롤러 진입까지. 인자 해석 등 사전 처리 포함 |
| `map_response_processing` | 컨트롤러 반환 후 MVC 완료까지. JSON 변환·출력을 포함할 수 있음 |
| `map_filter_return` | MVC 완료 후 측정 필터 복귀까지. 후속 필터·버퍼 복사 등이 포함될 수 있음 |
| `map_json_write` | JSON 변환·출력. 아래 응답 출력 타이머와 같은 작업을 중복 합산하지 않음 |

`pheeeew_map_response_write_seconds_*`도 `api_version=v2|v4`, `route=pins|regions`,
`outcome=success|error`를 가진다. 순수 직렬화 CPU가 아니라 출력 스트림 대기를 포함한다.
쓰기 성공은 HTTP 200이나 클라이언트 수신 완료를 뜻하지 않는다. 기본 Jackson 변환기만
같은 목록 위치에서 감싸고 사용자 정의 하위 변환기는 바꾸지 않는다.

v2 지역 요약은 기존 응답 버퍼를 거쳐 gzip을 적용하지만 v4는 같은 압축 설정 대상이 아니다.
v2 converter의 버퍼 쓰기와 이후 응답 복사·압축을 v4의 직접 출력과 동일한 구간으로 취급하지 않는다.
진단 표본의 `responseContentLength`는 알려진 경우만 기록하며 실제 전송 바이트와 항상 같지 않다.
알 수 없는 길이는 0으로 채우지 않는다. `contentEncoding=identity|gzip|other`도 함께 본다.

## 해석과 표본 로그

- 컨트롤러 > 서비스 > DB/캐시 > 차단/후보는 중첩된다. 포함 시간을 합산하지 않으며 서로 다른 p95를 빼지 않는다.
- AOP는 기본 트랜잭션 advice 바깥에서 측정한다. 새 트랜잭션 시작·종료는 포함하지만 이미 열린 외부 트랜잭션 전체를 포함하지 않는다.
- 1/5/10/25/50/100/250/500/1000ms 버킷을 쓴다. 분위수는 보간값이고 1초를 넘는 꼬리는 count/sum/max 및 표본 로그로 보완한다.
- 기존 후보 탐색 타이머는 차단 SQL을 제외한다. 연결 획득·단계·서비스의 표본 수가 다르므로 평균도 임의 차감하지 않는다.
- CPU 스케줄링·GC·I/O 대기는 단계 실행 중이면 포함되지만 원인별로 분해되지 않는다. 클라이언트 네트워크·수신 및 Servlet 진입 전 큐 대기는 별도다.
- 없는 단계는 미관측이다. 401·입력 오류처럼 컨트롤러에 도달하지 않은 요청에 0ms 단계들을 만들지 않는다.

기존 1초 이상/5xx 로그는 유지한다. 추가 진단은 `MapReadDiagnostics` logger의
`map_read_sample` INFO 이벤트이며 **dev에서 명시적으로 활성화한 경우에만** 기록한다.
기본 비활성, 무작위 1%와 100ms 이상 추가 표본을 합쳐 인스턴스당 초당 최대 5건이다.
`pheeeew_map_diagnostic_logs_total{result="sampled|suppressed"}`로 기록/상한 생략 수를 확인한다.
이 표본으로 전체 p95·요청량·hit 비율을 계산하지 않는다.

진단 표본의 `observedStatus`는 필터 복귀 시점에 응답 객체에서 읽은 상태다.
`unhandledFailure`는 예외가 필터 밖으로 전파되는지, `responseCommitted`는 이때 응답이 이미
커밋됐는지를 기록한다. 200 응답 일부를 보낸 뒤 출력 예외가 발생하면 `observedStatus=200`,
`unhandledFailure=true`, `responseCommitted=true`, `outcome=server_error`가 함께 남는다.
응답 커밋 전 예외도 이후 컨테이너 오류 처리로 상태가 바뀔 수 있으므로 최종 전송 상태로 부르지 않는다.

허용 필드는 API 버전·경로 종류·조건/경로 분류·관측 응답 상태·예외 전파/응답 커밋 여부·선정 이유·기간(ms)·고정 단계별
count/totalMs/maxMs·알려진 응답 길이·인코딩과 기존 correlationId다. 토큰·좌표·cursor·본문·개인 ID는
기록하지 않는다. correlationId를 지표나 Loki 라벨로 추가하지 않는다. 운영 단일/블루그린 Alloy는
이 logger를 허용하지 않으며 dev도 정확한 logger+event+INFO 조합만 허용한다.

새 카운터는 유한한 라벨 조합만 가진다. 요청 카운터의 단순 조합 상한은 인스턴스당 1,980개다.
단계 20개×버전 3개×성공/실패 2개×(유한 버킷 9개+Inf+count/sum/max)는 1,560개,
출력 타이머는 104개, 진단 카운터는 2개다. 이는 배포·환경 라벨을 제외한 이 네 계열의
이론상 상한이며 실제 관측 조합·등록 방식과 기존 다른 지표는 별도로 확인한다.

## 로컬 검증과 이후 측정

`python3 docker/alloy/verify.py`는 세 설정의 지표 허용·환경/인스턴스 라벨·문법과 실제 로그
파이프라인의 개발 허용/운영 차단을 가짜 자료·외부 통신 없는 컨테이너로 확인한다.
`python3 docker/grafana/verify.py`는 대시보드 원문 쿼리의 분모, 무호출·미수집·버전/환경/실패 제외,
호출 수 가중평균을 promtool 합성 자료로 검증한다. 이 검증은 Cloud 수신이나 성능 시험을 대신하지 않는다.

별도 승인된 실행에서는 배포 SHA·발생기·DB·자원·날짜·bbox·페이지·응답량·인코딩을 고정하고,
저부하 기준선 뒤 같은 목표 RPS를 독립 반복한다. 실제 시작/완료/오류/미발생 요청도 함께 기록한다.
한 달 SQL과 전체 기간 캐시 경로는 결과 집합이 다를 수 있어 차이를 순수 캐시 효과로 부르지 않는다.
계측·프로파일 오버헤드와 실행 순서 효과를 구분하고, 확인된 원인과 남은 가설을 분리한다.
