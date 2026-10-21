# 지도 조회 단계 계측

`pheeeew_map_read_stage_seconds_{count,sum,bucket,max}`는 공개 메서드의 **포함 시간**이다.
`stage`는 아래 고정 값이고 `outcome`은 `success|error`다. 요청 ID, 좌표, 기기 ID는 태그로 사용하지 않는다.

| stage | 범위 |
| --- | --- |
| `map_pins_controller` | v2 핀 컨트롤러 본문 전체. 서비스 호출과 응답 DTO 변환 포함. 인자 해석 및 JSON 출력 제외 |
| `map_regions_controller` | v2 지역 컨트롤러 본문 전체. 서비스 호출과 응답 DTO 변환 포함. 인자 해석 및 JSON 출력 제외 |
| `map_pins_service` | v2 핀 조회 서비스 전체. SQL fallback도 포함 |
| `map_regions_service` | 지역 조회 서비스 전체. SQL fallback도 포함 |
| `map_pins_device` | 핀 경로에서 외부 프록시를 통과하는 기기·그룹 검증. 지역 준비 메서드 내부 자기 호출은 별도 기록하지 않음 |
| `map_regions_prepare` | 지역 요청 준비: 기기·그룹 검증, 지역 목록 및 집계 준비 상태 조회 |
| `map_pins_cache` | 핀 캐시 find 전체: 스냅샷 접근, 필요 시 재생성, 차단 JDBC 조회, 후보 선택 포함. SQL fallback 신호 반환도 정상 호출 |
| `map_regions_cache` | 지역 summaries 전체: 스냅샷 접근과 필요 시 재생성 포함. fallback 신호 반환도 정상 호출 |
| `map_blocks_lookup` | 차단 JDBC 조회 및 결과 집합 생성. DB 연결 획득·통신·행 매핑 포함 |
| `map_region_list` | RegionRepository.findByLevel 호출 전체 |
| `map_pins_response` | 캐시 결과의 서비스 페이지 조립. 필요한 스탬프 SQL·DTO 조립·커서 생성 포함. 컨트롤러 DTO 변환과 JSON 출력 제외 |

`pheeeew_map_response_write_seconds_*`는 지도 GET 응답의 JSON 변환·출력 시간이다.
순수 직렬화 CPU 시간이 아니라 출력 스트림 대기를 포함한다. 클라이언트의 수신 완료 시간은 아니다.
`route=pins|regions`, `outcome=success|error`는 고정 값이다. 쓰기 성공은 HTTP 200을 뜻하지 않으며 오류 응답 본문도 포함할 수 있다.
기본 Jackson 변환기만 같은 목록 위치에서 감싸며 사용자 정의 하위 변환기는 교체하지 않는다.
현재 사용하지 않는 `@JsonView`처럼 변환기 클래스에 의존하는 advice를 도입할 때는 이 래퍼와의 호환성을 다시 확인해야 한다.

## 해석

- 컨트롤러 > 서비스 > 캐시 > 차단/선택은 중첩된다. 위 단계들의 합은 HTTP 전체 시간이 아니다.
- AOP는 기본 트랜잭션 advice 바깥(`@Order(0)`)에서 측정한다. 새 트랜잭션의 시작·종료를 포함하지만 이미 존재한 외부 트랜잭션의 시작·종료를 포함하지 않는다.
- `map_pins_response`에 스탬프 조회가 포함된다. 기존 Spring Data의 스탬프 메서드 지표를 여기에 더하지 않는다.
- 기존 `pheeeew_map_cache_pin_search_seconds_*`는 후보 선택부터 불변 결과 생성까지다. 기존 범위는 유지한다.
- 캐시 hit/miss/fallback은 기존 `pheeeew_map_cache_requests_total`을 함께 확인한다. 단계의 `success`가 캐시 hit를 뜻하지 않는다.
- 1/5/10/25/50/100/250/500/1000ms 버킷을 제공한다. p95는 버킷 보간 추정이며 1초를 넘는 꼬리를 정확히 표현하지 못한다. count/sum/max를 함께 본다.
- 단계별 평균은 해당 단계가 실행된 호출의 평균이다. 일부 요청만 실행하는 단계와 전체 요청 평균을 바로 더하거나 빼지 않는다. p95끼리 빼서 미계측 시간의 p95를 만들지 않는다.
- CPU 스케줄링·GC·네트워크 대기는 메서드 실행 중이면 포함되지만 원인별로 분리되지 않는다. 인증 필터·인자 해석 등은 컨트롤러 타이머 밖에 남는다.
- 메서드 단계는 기존 느린 요청 로그의 `timings`에도 기록한다. 로그 기준(1초 이상/5xx)은 유지하며 모든 요청을 로그로 남기지 않는다.

## 다음 측정

실제 배포 SHA, 활성 데이터, 100 VU 조건을 고정하고 같은 정상 10분 구간을 비교한다.
새 지표 수집은 앱과 Alloy 설정 반영 이후에만 가능하다. 이 변경만으로 과거 시험을 소급 분해할 수 없다.

```promql
sum by(stage) (increase(pheeeew_map_read_stage_seconds_sum{environment="dev",outcome="success"}[10m]))
/
sum by(stage) (increase(pheeeew_map_read_stage_seconds_count{environment="dev",outcome="success"}[10m]))
```

```promql
histogram_quantile(0.95,
  sum by(stage, le) (increase(pheeeew_map_read_stage_seconds_bucket{environment="dev",outcome="success"}[10m])))
```
