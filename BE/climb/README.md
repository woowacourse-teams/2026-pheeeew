# climb

감정이들의 점프 대모험(웹 게임) 전용 모듈이다. 손님 쿠키, 닉네임, 높이 보고, 실시간 순위, 함께 오르기(다른 사람 위치 공유)를 맡는다.
본체 패키지(`device`, `emotion`, `pressrace`, `auth`, `common` 등)를 참조하지 않는다.

## 켜고 끄기

| 설정 | 기본값 | 설명 |
|---|---|---|
| `pheeeew.climb.enabled` | `false` | 꺼져 있으면 climb 의 빈이 하나도 올라오지 않는다. local·dev·prod 프로필에서 켠다. |
| `pheeeew.climb.allowed-origin` | 없음 | 게임 주소 하나. 켤 때 필수다. CORS 와 웹소켓 핸드셰이크가 이 출처만 허용한다. |
| `pheeeew.climb.guest-creates-per-hour` | `10` | IP당 시간당 손님 만들기 한도. |
| `pheeeew.climb.maps.<버전>.top` | `v1`: 663 | 꼭대기 높이(칸). |
| `pheeeew.climb.maps.<버전>.zones` | `v1`: 135, 261, 397, 523, 653 | 구간 경계 높이. |
| `pheeeew.climb.maps.<버전>.min-finish-seconds` | `v1`: 300 | 이보다 빠른 완주는 의심 기록으로 숨긴다. |
| `pheeeew.climb.presence.enabled` | `true` | 함께 오르기만 끈다. 꺼지면 `st` 를 무시하고 `sts`·`in`·`out` 을 보내지 않는다. 순위는 그대로다. |
| `pheeeew.climb.presence.range` | `30` | 내 높이 위아래 이 칸 수 안의 사람만 보인다. |
| `pheeeew.climb.presence.limit` | `20` | 한 사람이 받는 최대 인원(가까운 순서). |
| `pheeeew.climb.admin-key` | 빈 값(환경 변수 `PHEEEEW_CLIMB_ADMIN_KEY`) | 관리자 키. 비어 있으면 관리자 기능이 꺼지고 관리자 끝점이 모두 `404` 다. 값은 환경 변수로만 넣는다. |

`maps` 에 없는 맵 버전은 연결은 되지만 순위에 넣지 않는다.

## 손님 쿠키

`climb_guest`: 무작위 32바이트(base64url). `HttpOnly; Secure; SameSite=Lax; Path=/api/v1/climb; Max-Age=15552000`(180일).
쿠키가 확인될 때마다 응답에 같은 쿠키를 다시 내려 수명을 늘린다. DB 에는 SHA-256 해시만 둔다.
브라우저는 `fetch(url, { credentials: 'include' })` 로 부른다.

## 관리자 쿠키

`climb_admin`: 관리자 키가 맞을 때 서버가 만든 무작위 32바이트(base64url). `HttpOnly; Secure; SameSite=Lax; Path=/api/v1/climb; Max-Age=28800`(8시간).
손님 쿠키와 별개다. 세션은 서버 메모리에만 두어 서버가 다시 뜨면 사라진다(다시 키를 넣는다). 키와 토큰은 로그에 남기지 않는다.

## HTTP

오류 본문은 모두 `{ "code": "...", "message": "..." }` 다.

| 요청 | 응답 |
|---|---|
| `POST /api/v1/climb/guests` `{ "nickname": "히유" }` | `201` + 쿠키, `{ "id", "nickname", "tag" }`. `400 NICKNAME_LENGTH`(2~10자), `400 NICKNAME_CHARACTERS`(한글·영문·숫자), `429 GUEST_CREATE_LIMITED` |
| `GET /api/v1/climb/me` | `{ "id", "nickname", "tag", "run" }`. `run` 은 가장 최근의 완주하지 않은 등반 `{ map, character, height, best, x, falls, time }` 또는 `null`. 쿠키 없음·무효 `401 UNAUTHORIZED` |
| `PATCH /api/v1/climb/me` `{ "nickname" }` | `{ "id", "nickname", "tag" }`. 하루(한국 시간) 3번, 넘으면 `429 NICKNAME_CHANGE_LIMITED` |
| `GET /api/v1/climb/ranking?board=online\|today\|all&map=v1` | `{ "top": [순위 항목], "me": { "rank", "best" } \| null, "online" }`. 쿠키가 없으면 `me` 는 `null`. 잘못된 `board` 는 `400 INVALID_BOARD` |

| `POST /api/v1/climb/admin/session` `{ "key": "..." }` | 맞으면 `204` + 관리자 쿠키. 틀리면 `401 ADMIN_KEY_MISMATCH`. 틀린 시도가 IP당 1분에 5번을 넘으면(6번째부터, 맞는 키도) `429 ADMIN_ATTEMPTS_LIMITED`. 키는 본문으로만 받고 해시끼리 `MessageDigest.isEqual` 로 비교한다 |
| `GET /api/v1/climb/admin/session` | 유효한 관리자 쿠키면 `204`, 아니면 `401 ADMIN_UNAUTHORIZED` |
| `DELETE /api/v1/climb/admin/session` | 세션을 끝내고 쿠키를 지운다(`Max-Age=0`). `204` |

관리자 끝점 셋은 관리자 키가 비어 있으면 모두 `404 ADMIN_DISABLED` 다. 손님 쿠키는 필요 없다.

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
{ "t": "welcome", "me": { "id": "…", "nickname": "히유", "tag": "4f2a" }, "run": null, "online": 3, "serverTime": 1794452375553 }
{ "t": "rank", "top": [ … ], "me": { "rank": 4, "best": 131 }, "near": [ 3등, 4등(나), 5등 ], "online": 3 }
{ "t": "event", "kind": "zone", "nickname": "히유", "tag": "4f2a", "zone": 1 }
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

핸드셰이크에 유효한 `climb_admin` 쿠키가 있으면 관리자 연결이다(손님 쿠키도 필요하다). 등반을 만들지 않아서 `pos`·`zone`·`finish`·`restart` 는
오류 없이 버리고(`welcome` 의 `run` 은 `null`), 기록·순위·`event` 에 넣지 않는다. `st` 는 받아 두지만 다른 사람에게 보내지 않는다.
연결은 그대로 두고 `rank`(`me` 는 `null`)와 가까운 사람의 `in`·`sts`·`out` 은 받는다. 게임도 관리자 모드에서는 보고하지 않는다(이중 장치).

## 함께 오르기

같은 웹소켓에 메시지 종류만 더한다. 위치는 서버 메모리에 접속마다 최신 하나만 두고, 저장하지 않으며 순위에 쓰지 않는다.

브라우저 → 서버

```json
{ "t": "st", "s": [612, 135.42, -180, 420, 1, 0] }
```

- `s` = `[x, h, vx, vy, flags, charge]`. `x` 는 몸 가운데 가로 위치(px, 정수, 0~1280), `h` 는 발 높이(칸, 소수 둘째 자리까지),
  `vx`·`vy` 는 속도(px/초, 정수, 아래가 +), `charge` 는 모은 정도(0~100 정수).
  `flags` 비트: `1` 땅 위, `2` 힘 모으는 중, `4` 왼쪽을 봄, `8` 엎어짐, `16` 미끄러지는 중.
- 움직이는 동안 100ms마다, 가만히 있으면 1초마다 보낸다. `hello` 뒤에만 보낸다.
- 범위 밖(`0 ≤ h ≤ top + 18`, `|v| ≤ 5000` 등)이거나 모양이 틀린 `st` 는 오류 없이 버린다. 서버가 모르는 맵 버전이면 함께 오르기를 하지 않는다.
- 손님당 초당 12개(다른 메시지의 초당 10개와 따로 센다). 넘친 것은 버리고, 계속 넘기면(초당 24개 넘게) `MESSAGE_RATE_LIMITED` 로 닫는다.

서버 → 브라우저(100ms마다, 받는 사람마다)

```json
{ "t": "out", "n": [7] }
{ "t": "in", "p": [{ "n": 9, "nickname": "히유", "tag": "4f2a", "character": "angry" }] }
{ "t": "sts", "s": [[9, 612, 135.42, -180, 420, 1, 0]] }
```

- 보이는 사람: 같은 맵 버전에서 내 마지막 `st` 높이 위아래 `range` 칸 안, 가까운 순서로 `limit` 명. 내가 `st` 를 보내지 않으면 아무도 보이지 않는다.
- `n` 은 접속 번호다. 같은 손님도 다시 연결하면 새 번호가 된다.
- 처음 보이는 사람은 `in`(이름표) 뒤에 `sts` 로 온다. `sts` 의 항목은 `[n, ...s]` 이고, 지난번 뒤로 새 `st` 를 보낸 사람만 들어 있다.
- 범위를 벗어나거나 연결이 끊긴 사람은 `out` 으로 온다. 같은 틱에서는 `out`, `in`, `sts` 순서다.
- 다시 연결하면 처음부터(`in` 부터) 다시 받는다. 브라우저는 끊기면 다른 사람 목록을 비운다.

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

## 관리자 키 넣기

로컬: `BE/.env`(저장소가 무시한다)에 `PHEEEEW_CLIMB_ADMIN_KEY=<키>` 를 한 줄 넣고 `BE` 에서 `./gradlew bootRun` 한다.
`bootRun` 은 개발 전용 의존성 `springboot4-dotenv` 가 작업 폴더의 `.env` 를 읽는다(`JWT_*` 와 같은 방식). 셸 환경 변수로 넘겨도 된다.
형식은 `BE/.env.example` 에 있다.

## 배포

개발·운영 프로필은 등반 기능이 켜져 있다. 기본 `application.yml`의 `enabled: false`는 유지한다.

| 프로필 | 허용하는 게임 출처 | GitHub Environment |
|---|---|---|
| dev | `https://climb-dev.pheeeew.com` | `development` |
| prod | `https://climb.pheeeew.com` | `production` |

관리자 키 전달 경로를 배포 설정에 반영했다. 실제 적용은 서버 배포 뒤다.

1. 각 GitHub Environment에 등록된 `PHEEEEW_CLIMB_ADMIN_KEY` secret을 사용한다.
2. `.github/workflows/be-dev-deploy.yml`, `be-deploy.yml`의 `.env 갱신` 단계가 키를 환경 변수로 받고,
   `{ ... } > env.tmp` 안에서만 파일로 출력해 서버 `/opt/pheeeew/.env`로 전달한다. 키를 로그로 출력하지 않는다.
3. `BE/docker/compose.dev.yml`, `compose.prod.yml`의 `x-app.environment`가 앱에 전달한다.
   `compose.local.yml`의 `app.environment`도 같은 변수를 받는다.
   `${PHEEEEW_CLIMB_ADMIN_KEY:-}`이므로 빈 값이면 관리자 기능만 꺼진다.
4. 서버를 해당 환경에 배포한 뒤 게임을 수동 업로드한다. 순서와 되돌리는 방법은
   [게임 배포 문서](../../WEB/climb/README.md#배포)를 따른다.
