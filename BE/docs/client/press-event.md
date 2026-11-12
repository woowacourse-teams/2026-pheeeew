# 20초 프레스 이벤트 API

두 API 모두 `Authorization: Bearer <accessToken>` 헤더를 사용합니다. 경로의 `eventId`는 이벤트에 부여한 UUID입니다. 서버는 액세스 토큰의 기기로 참가자를 식별합니다.

앱은 3초 카운트다운이 끝나면 20초 동안 감정별 프레스를 집계합니다. **이벤트에서 누른 프레스는 결과 제출 API로만 전송합니다.** 서버는 승인된 도전의 횟수를 개인·전체·일일 프레스 집계와 기존 랭킹에도 반영합니다. 집계 날짜는 서버가 새 도전을 접수한 한국 시간 기준 날짜입니다.

## 결과 제출

`POST /api/v1/press-events/{eventId}/attempts`

`Content-Type: application/json`으로 전송합니다. 클라이언트는 도전마다 `requestId` UUID를 한 번 생성합니다. 다섯 감정 키를 모두 보내고, 누르지 않은 감정은 0을 보냅니다. 각 값과 합계는 0 이상 2,147,483,647 이하의 정수여야 합니다.

```json
{
  "requestId": "d40d8d95-30a5-4c52-84da-63e6481b8b56",
  "counts": {
    "FRUSTRATED": 40,
    "IRRITATED": 32,
    "EXHAUSTED": 20,
    "DISCOURAGED": 15,
    "ANGRY": 25
  }
}
```

성공 응답은 `200 OK`입니다.

```json
{
  "attemptId": "d40d8d95-30a5-4c52-84da-63e6481b8b56",
  "total": 132,
  "bestScore": 156,
  "rank": 3,
  "rankedAt": "2026-11-11T03:00:00Z",
  "replayed": false
}
```

- `attemptId`는 제출한 `requestId`와 같습니다. 게임 종료 다이얼로그에는 이번 도전의 `total`과 이벤트 최고 점수 기준 `rank`를 표시합니다.
- 최고 점수보다 낮거나 같은 도전도 이번 도전의 횟수를 일반 프레스에 모두 가산합니다. 같은 최고 점수를 다시 달성하면 최초 달성 시각을 유지합니다.
- `rankedAt`은 이 도전을 반영한 뒤 서버가 순위를 계산한 시각입니다. 시간 값은 UTC ISO 8601로 반환합니다.
- 네트워크 실패 또는 응답 유실 시 같은 `requestId`와 감정별 횟수로 재시도합니다. 서버는 이벤트·인증 기기·요청 ID 조합으로 멱등성을 판단합니다. 이미 성공한 요청이면 최초 `total`, `bestScore`, `rank`, `rankedAt`과 `replayed: true`를 반환합니다. 이벤트 종료 후에도 이 응답을 받을 수 있으며 프레스는 한 번만 가산합니다.
- 같은 조합으로 유효한 감정별 횟수를 바꿔 보내면 합계가 같아도 `409 Conflict`를 반환합니다. 새 도전에는 새 UUID를 사용합니다.

## 순위 조회

`GET /api/v1/press-events/{eventId}/rankings`

```http
GET /api/v1/press-events/{eventId}/rankings?limit=1
GET /api/v1/press-events/{eventId}/rankings?limit=1&cursor={nextCursor}
```

`limit`의 기본값은 20이며 1~100을 허용합니다. 첫 페이지에서는 `cursor`를 생략합니다. 다음 페이지는 같은 이벤트의 직전 응답에서 받은 `nextCursor`를 그대로 전달합니다. 각 페이지는 조회 시점의 순위를 반영하므로 조회 도중 점수가 올라간 참가자는 앞 페이지로 이동할 수 있습니다. 최신 목록을 다시 표시하려면 첫 페이지부터 조회합니다.

성공 응답은 `200 OK`입니다.

```json
{
  "leader": {
    "publicId": "69644bf9-47fb-422e-970e-ce57c9463885",
    "nickname": "히유",
    "bestScore": 210
  },
  "rankings": {
    "items": [
      {
        "rank": 1,
        "publicId": "69644bf9-47fb-422e-970e-ce57c9463885",
        "nickname": "히유",
        "bestScore": 210,
        "mine": false
      }
    ],
    "hasNext": true,
    "nextCursor": "opaque-cursor"
  },
  "me": {
    "bestScore": 156,
    "rank": 3
  },
  "rankedAt": "2026-11-11T03:00:01Z"
}
```

- 참가자별 최고 점수만 순위에 사용합니다. 점수가 같으면 같은 순위를 부여하고 다음 순위를 건너뜁니다. 예를 들어 1위, 1위, 3위로 표시합니다.
- 동점 목록은 최고 점수를 처음 달성한 도전의 서버 접수 시각, 이벤트 내 공개 UUID 순으로 정렬합니다. `leader`는 전체 목록에서 그 순서의 첫 참가자입니다. 닉네임 미설정은 `익명`으로 표시합니다.
- 참가자가 없으면 `leader: null`, `rankings.items: []`입니다. 본인이 미참여면 `me: null`이고, 참여했다면 현재 페이지에 없어도 내 점수와 순위를 반환합니다.
- 마지막 페이지는 `hasNext: false`, `nextCursor: null`입니다. `rankedAt`은 해당 응답의 순위 기준 시각입니다.
- 등록된 이벤트는 시작 전과 종료 후에도 순위를 조회할 수 있습니다.

## 오류와 재시도

오류 응답은 공통 JSON 형식을 사용합니다.

```json
{
  "code": "PRESS-EVENT-003",
  "message": "같은 도전 식별자로 다른 프레스 횟수가 제출되었습니다."
}
```

| HTTP | 코드 | 조건 또는 처리 |
| --- | --- | --- |
| 400 | `COMMON-001` | JSON 형식, UUID, 페이지 크기 또는 감정 값의 타입이 올바르지 않습니다. 미지원 감정과 정수 범위를 넘는 값도 이 코드로 거절합니다. |
| 400 | `PRESS-EVENT-004` | 감정 키 누락, 음수, 합계가 32비트 정수 범위를 벗어났습니다. |
| 400 | `PRESS-EVENT-006` | 커서 형식이 잘못됐거나 다른 이벤트의 커서를 사용했습니다. 첫 페이지부터 조회합니다. |
| 401 | `AUTH-001`, `DEVICE-004` | 액세스 토큰 또는 기기를 인증할 수 없습니다. 인증을 복구한 뒤 같은 도전은 기존 키로 재시도합니다. |
| 404 | `PRESS-EVENT-001` | 등록된 이벤트를 찾을 수 없습니다. |
| 409 | `PRESS-EVENT-002` | 새 도전의 제출 가능 기간은 이벤트 시작 시각 이상, 종료 시각 미만입니다. |
| 409 | `PRESS-EVENT-003` | 같은 도전 ID의 기존 결과와 감정별 횟수가 다릅니다. |
| 503 | `SERVER_DRAINING`, `SERVER_REPLACED` | 프레스 저장이 일시 중단됐습니다. `Retry-After`가 있으면 그 값을 따르고 같은 키와 본문으로 재시도합니다. |

## 입력 검증과 운영

일반 HTTP 프레스는 감정당 30회와 요청당 100회로 절삭합니다. 일반 HTTP·WebSocket은 기기별 최대 100회, 초당 20회 충전 버킷을 공유합니다. 이벤트 결과 제출은 32비트 정수 범위 내의 횟수를 모두 가산합니다. 도전당 또는 최근 20초 동안의 횟수 상한은 적용하지 않습니다.

이벤트 기간, 기기 인증, 정수 범위와 중복 제출을 서버에서 확인합니다. 결과 합계만 받으므로 실제로 20초 동안 사람이 눌렀는지 검증할 수 없습니다. 시작 세션이나 개별 입력 전송은 현재 계약의 범위 밖입니다. [기존 실시간 프레스 계약](../decisions/realtime-press-contract.md)

운영에서 이벤트를 열 때는 `press_events`에 공개할 UUID와 `starts_at`, `ends_at`을 등록한 뒤 같은 UUID를 앱에 전달합니다. 마이그레이션은 테이블과 인덱스를 만들며 이벤트는 운영 일정에 맞춰 등록합니다.
