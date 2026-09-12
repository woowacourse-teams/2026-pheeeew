# Architecture Decision Records

ADR은 코드만으로 알기 어려운 **기술 결정의 이유와 감수한 결과**를 팀원에게 남기는 기록이에요.

완벽한 결정만 기록하지는 않아요. 현재 상황에서 단점이나 위험을 알고도 선택한 기술적 타협이라면 그 이유와 재검토 조건을 함께 남겨요.

## 언제 작성하나요?

다음 두 조건을 모두 만족하면 ADR을 작성해요.

1. 실제로 선택했거나 의식적으로 보류한 결정이 있어요.
2. 다음 중 하나 이상에 해당해요.
   - 되돌리거나 교체하는 비용이 커요.
   - 구조·정합성·성능·운영 또는 공개 API에 영향을 줘요.
   - 선택 이유가 코드만 봐서는 드러나지 않아요.
   - 같은 논의가 반복될 가능성이 높아요.

다음 내용은 ADR로 작성하지 않아요.

- 아직 선택하지 않은 아이디어나 TODO
- 네이밍·포맷 같은 코드 스타일
- 쉽게 되돌릴 수 있는 평범한 구현 방법
- 작업 내용 요약이나 회의록
- 별다른 선택이 없었던 단순 버그 수정

## 작성 형식

```markdown
# ADR-NNNN: 무엇을 결정한다

## Status

Accepted (YYYY-MM-DD)

## Context

- 결정이 필요해진 상황
- 당시의 제약과 확인된 사실

## Decision

무엇을 선택했고 왜 선택했는지 1~3문장으로 쓴다.

## Alternatives

- 실제로 검토했지만 선택하지 않은 대안과 이유

## Consequences

- (+) 얻는 점
- (-) 감수하는 점
- (=) 추가로 생기는 책임이나 중립적인 변화
- 재검토: 다시 판단할 조건

## Compliance

- 필요한 경우에만 테스트·리뷰 등 검증 방법을 쓴다.
```

- `Context`에는 결론을 미리 정당화하지 않고 당시의 사실과 제약을 적어요.
- `Decision`에는 선택과 핵심 이유만 적어요.
- `Alternatives`에는 실제로 검토한 대안만 적어요.
- `Consequences`에는 장점뿐 아니라 비용과 위험도 함께 적어요.
- 기본적으로 한 화면 또는 30~50줄 안에서 핵심만 기록해요.

## Status

| 상태 | 의미 |
| --- | --- |
| `Proposed` | 아직 팀이 논의 중인 제안 |
| `Accepted` | 팀이 채택했고 현재 유효한 결정 |
| `Rejected` | 제안을 검토했지만 채택하지 않음 |
| `Deprecated` | 더 이상 유효하지 않으며 직접 대체한 결정은 없음 |
| `Superseded by ADR-NNNN` | 새로운 ADR이 기존 결정을 대체함 |

## 파일 이름과 번호

- 파일명은 `NNNN-short-english-title.md`로 작성해요.
- 제목은 `ADR-NNNN: 무엇을 결정한다`처럼 결정문으로 작성해요.
- 번호는 4자리로 순차 증가시키며 삭제된 번호도 재사용하지 않아요.
- ADR 하나에는 하나의 결정만 기록해요.
- 결정이 바뀌면 기존 내용을 덮어쓰지 않고 새 ADR을 작성해 연결해요.

## 현재 ADR 목록

| 번호 | 결정 | 한 줄 요약 |
| --- | --- | --- |
| [0001](0001-use-postgresql-with-postgis.md) | PostgreSQL과 PostGIS 사용 | 지도 영역 조회를 공간 타입·함수와 GiST 인덱스로 처리해요 |
| [0002](0002-use-grid-center-for-sigh-location.md) | 격자 중심과 최종 표시 위치 분리 | ADR-0006으로 대체됐어요. 이전 격자 계약의 근거를 보존해요 |
| [0003](0003-share-postgis-testcontainer-per-jvm.md) | PostGIS 테스트 자원 공유 | 컨테이너와 같은 Data JPA Context를 테스트 JVM 단위로 공유해요 |
| [0004](0004-skip-transaction-on-idempotent-save.md) | 멱등 저장 경로의 트랜잭션 | 선조회, 삽입, 재조회를 독립된 트랜잭션으로 두어 제약 위반 뒤에도 복구해요. 증명을 실은 경로의 예외는 ADR-0015 에 있어요 |
| [0005](0005-soft-delete-sigh.md) | 한숨 소프트 삭제 | 한숨을 지우지 않고 표시만 해서 신고 이력과 외래 키를 함께 지켜요 |
| [0006](0006-use-independent-uniform-disks-for-sigh-location.md) | 클라이언트·서버 독립 균등 원 이동 | 두 단계에서 각각 반경 300m를 적용해요. 구형 앱 전환·실제 앱 검증 전에는 배포하지 않아요 |
| [0007](0007-split-signed-access-token-and-stored-refresh-token.md) | 기기 인증 토큰 구조 | 서명만 검증하는 30분 access token과 해시로만 저장하는 refresh token을 나눠 써요 |
| [0008](0008-limit-device-registration-retry-window.md) | 기기 등록 재시도 창 | 등록 후 5분 이내의 재시도에만 토큰을 다시 발급해 요청 식별자의 유효 기간을 잘라요 |
| [0009](0009-keep-previous-refresh-token-on-registration-retry.md) | 등록 재시도의 이전 토큰 처리 | 재시도에서 기존 토큰을 폐기하지 않아요. 경합에서 정상 응답을 받은 앱이 잠기기 때문이에요 |
| [0010](0010-use-alloy-and-grafana-cloud-for-production-monitoring.md) | 운영 모니터링에 Alloy와 Grafana Cloud 사용 | Proposed: 운영 EC2에서 수집하고 지표·로그 저장과 조회는 Cloud에 맡겨요 |
| [0011](0011-use-servlet-filter-for-http-request-logging.md) | HTTP 요청 로그와 추적 문맥을 필터에서 처리 | Proposed: 동기 MVC 처리 전후에 추적 문맥을 관리하고 오류·느린 요청을 기록해요 |
| [0012](0012-limit-exported-http-logs-and-use-aggregate-metrics.md) | 요청 통계 집계와 외부 전송 로그 제한 | Proposed: 지표로 요청 통계를 관찰하고 검토된 서버 오류·느린 요청 로그만 전송해요 |
| [0013](0013-validate-token-purpose-in-decoder-not-authority.md) | 토큰 용도 검증 위치 | 용도를 권한으로 바꾸지 않고 디코딩 단계에서 검사해 다른 용도의 토큰이 401로 나가게 해요 |
| [0014](0014-call-play-integrity-without-google-client-library.md) | Play Integrity 호출 방식 | 구글 클라이언트 라이브러리를 쓰지 않고 이미 있는 기구로 직접 호출해요 |
| [0015](0015-verify-attestation-only-when-token-is-present.md) | 무결성 증명 검증 시점 | 토큰이 있을 때만 검증해요. 강제 잠금은 설정이 아니라 코드 상수로 둬요 |
| [0016](0016-consume-challenge-with-conditional-update.md) | challenge 1회용 보장 | 조건부 UPDATE 의 영향 행 수로 판정해 동시 요청에서 한 건만 통과시켜요 |
| [0017](0017-skip-attestation-on-registration-retry.md) | 등록 재시도의 증명 검증 | 새 기기를 만드는 경로만 증명을 요구해요. 재시도는 challenge 1회용 때문에 면제해요 |
| [0018](0018-limit-google-decode-attempts-per-challenge.md) | challenge 당 구글 호출 상한 | 구글을 부르기 전에 시도 수를 올려 challenge 한 건을 5회로 묶어요 |
| [0019](0019-restrict-service-account-token-uri-to-https-and-loopback.md) | 서비스 계정 토큰 주소 제한 | HTTPS 와 루프백 리터럴만 허용해요. 이름 해석은 재바인딩 때문에 막아요 |
| [0020](0020-limit-daily-google-decode-calls-per-project.md) | 구글 호출 하루 예산 | 날짜별 예산 행으로 분산 요청까지 묶어요. 넘으면 정상 등록도 막혀요 |

## AI 사용

작성자는 ADR의 상황, 선택, 대안과 감수할 점을 직접 설명할 수 있어야 해요. AI는 이미 확인된 내용을 정리하거나 문장을 다듬는 용도로 사용하고, 검증하지 않은 근거와 트레이드오프를 새로 만들지 않아요.

## 참고

- [Michael Nygard, Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [MADR ADR Template](https://adr.github.io/madr/decisions/adr-template.html)
