# climb

피유 등반(웹 게임) 전용 모듈이다. 손님 쿠키, 닉네임, 높이 보고, 실시간 순위를 맡는다.
본체 패키지(`device`, `emotion`, `pressrace`, `auth`, `common` 등)를 참조하지 않는다.

## 켜고 끄기

| 설정 | 기본값 | 설명 |
|---|---|---|
| `pheeeew.climb.enabled` | `false` | 꺼져 있으면 climb 의 빈이 하나도 올라오지 않는다. 로컬 프로필만 켠다. |
| `pheeeew.climb.allowed-origin` | 없음 | 게임 주소 하나. 켤 때 필수다. CORS 와 웹소켓 핸드셰이크가 이 출처만 허용한다. |
| `pheeeew.climb.guest-creates-per-hour` | `10` | IP당 시간당 손님 만들기 한도. |
| `pheeeew.climb.maps.<버전>.top` | `v1`: 663 | 꼭대기 높이(칸). |
| `pheeeew.climb.maps.<버전>.zones` | `v1`: 135, 261, 397, 523, 653 | 구간 경계 높이. |
| `pheeeew.climb.maps.<버전>.min-finish-seconds` | `v1`: 300 | 이보다 빠른 완주는 의심 기록으로 숨긴다. |

`maps` 에 없는 맵 버전은 연결은 되지만 순위에 넣지 않는다.

## 손님 쿠키

`climb_guest`: 무작위 32바이트(base64url). `HttpOnly; Secure; SameSite=Lax; Path=/api/v1/climb; Max-Age=15552000`(180일).
쿠키가 확인될 때마다 응답에 같은 쿠키를 다시 내려 수명을 늘린다. DB 에는 SHA-256 해시만 둔다.
브라우저는 `fetch(url, { credentials: 'include' })` 로 부른다.

## HTTP

오류 본문은 모두 `{ "code": "...", "message": "..." }` 다.

| 요청 | 응답 |
|---|---|
| `POST /api/v1/climb/guests` `{ "nickname": "피유" }` | `201` + 쿠키, `{ "id", "nickname", "tag" }`. `400 NICKNAME_LENGTH`(2~10자), `400 NICKNAME_CHARACTERS`(한글·영문·숫자), `429 GUEST_CREATE_LIMITED` |
| `GET /api/v1/climb/me` | `{ "id", "nickname", "tag", "run" }`. `run` 은 가장 최근의 완주하지 않은 등반 `{ map, character, height, best, x, falls, time }` 또는 `null`. 쿠키 없음·무효 `401 UNAUTHORIZED` |
| `PATCH /api/v1/climb/me` `{ "nickname" }` | `{ "id", "nickname", "tag" }`. 하루(한국 시간) 3번, 넘으면 `429 NICKNAME_CHANGE_LIMITED` |
| `GET /api/v1/climb/ranking?board=online\|today\|all&map=v1` | `{ "top": [순위 항목], "me": { "rank", "best" } \| null, "online" }`. 쿠키가 없으면 `me` 는 `null`. 잘못된 `board` 는 `400 INVALID_BOARD` |

순위 항목: `{ "rank", "tag", "nickname", "character", "best", "finished", "time" }`. `time` 은 완주 시간(초), 완주 전이면 `null`.

## 웹소켓 `/api/v1/climb/ws`

쿠키가 없으면 핸드셰이크 `401`, 허용되지 않은 출처면 `403`. JSON 텍스트, 메시지마다 `t` 가 있다. 메시지는 16KB 까지.

브라우저 → 서버

```json
{ "t": "hello", "v": 1, "map": "v1", "character": "angry" }
{ "t": "pos", "h": 120, "best": 131, "falls": 4, "time": 95, "x": 18 }
{ "t": "zone", "zone": 1, "time": 96 }
{ "t": "finish", "time": 640, "falls": 12 }
{ "t": "restart" }
```

- 숫자 필드는 모두 정수만 받는다(`1.5`, `"3"` 은 `INVALID_MESSAGE`). `h`, `best` 는 칸, `time` 은 게임 시간(초, 내림), `x` 는 가로 위치(픽셀, 반올림한 정수)다.
- `hello` 는 연결 직후 한 번. `character` 는 `defeated`, `tired`, `frustrated`, `annoyed`, `angry` 중 하나.
  같은 맵에 완주하지 않은 등반이 있으면 이어 하고, 없으면 새 등반을 만든다.
- `pos` 는 착지할 때마다, 최대 2초에 한 번. `zone`·`finish` 를 보내기 전에는 그 높이까지 오른 `pos` 를 먼저 보낸다. `restart` 는 새 등반을 만든다. 이전 기록은 오늘·역대 순위에 남는다.

서버 → 브라우저

```json
{ "t": "welcome", "me": { "id": "…", "nickname": "피유", "tag": "4f2a" }, "run": null, "online": 3, "serverTime": 1794452375553 }
{ "t": "rank", "top": [ … ], "me": { "rank": 4, "best": 131 }, "near": [ 3등, 4등(나), 5등 ], "online": 3 }
{ "t": "event", "kind": "zone", "nickname": "피유", "tag": "4f2a", "zone": 1 }
{ "t": "error", "code": "CONNECTION_REPLACED" }
```

- `rank` 는 1초마다, 바뀐 것이 있을 때만 온다. 기준은 지금 접속자 순위다.
- `event` 는 같은 맵 접속자 모두에게 간다. 완주는 `"kind": "finish"`, `"zone": null`.
- `error` 뒤에는 연결이 닫힌다. 닫힘 사유 문자열도 같은 코드다.

| 코드 | 닫힘 상태 | 브라우저가 할 일 |
|---|---|---|
| `SERVER_DRAINING` | 1012 | 배포 중. 1초, 2초, 4초… 간격으로 다시 연결한다. |
| `CONNECTION_REPLACED` | 1008 | 같은 손님이 다른 탭에서 연결했다. 다시 연결하지 않는다. |
| `HEARTBEAT_TIMEOUT` | 1008 | Pong(또는 메시지)이 30초 동안 없었다. 다시 연결한다. |
| `MESSAGE_RATE_LIMITED` | 1008 | 초당 10개를 넘겼다. |
| `HELLO_REQUIRED`, `UNSUPPORTED_VERSION`, `INVALID_MESSAGE` | 1008 | 메시지를 고친다. |

서버는 15초마다 Ping 을 보낸다(브라우저가 자동으로 Pong 한다). 끊겨도 게임은 계속하고, 다시 연결하면 `hello` 부터 한다.

## 보고 검사

맞지 않는 보고는 오류 없이 버린다.

- `0 ≤ h ≤ top`, `h ≤ best ≤ top`.
- 최고 높이는 2초 창마다 20칸까지만 오른다(연결할 때 기준점을 다시 잡는다).
- `zone` 은 순서대로만, 그리고 그 구간 경계 높이 이상의 최고 높이(`pos` 의 `best`)를 받은 뒤에만.
- `finish` 는 모든 구간을 넘고 최고 높이가 꼭대기(`top`) 이상일 때만.
- 완주 `time` 은 연결 시간과 비교하지 않는다(탭이 숨겨지거나 확인 창이 떠 있으면 게임 시간이 멈춘다).
  `min-finish-seconds` 보다 빠른 완주는 지우지 않고 의심 표시만 해서 순위에서 숨긴다.

## 저장

`climb_guest`, `climb_run` 두 테이블(`V20261112_90__create_climb_tables.sql`). 기능이 꺼져 있어도 마이그레이션은 돈다.
등반은 손님당 10초에 한 번, 그리고 구간 통과·완주·처음부터 다시·연결 종료·배포 종료 때 쓴다.
순위표는 서버 메모리에 두고 서버가 뜰 때 DB 에서 채운다(서버 1대 기준). 180일 동안 접속하지 않은 손님과 기록은 하루 한 번 지운다.
