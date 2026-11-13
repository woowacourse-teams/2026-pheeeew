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
| `GET /api/v1/climb/hall?map=v1` | 명예의 전당. `{ "hall": [ { "order", "tag", "nickname", "character", "time", "falls", "finishedAt", "best", "bestFalls", "bestAt" } ] }`. 먼저 꼭대기에 온 순서(`finishedAt` 오름차순)로 50명까지. `time`·`falls`·`finishedAt` 은 처음 완주(초, ISO 시각)이고 다시 완주해도 바뀌지 않는다. `best`·`bestFalls`·`bestAt` 은 그 손님의 가장 빠른 완주(의심 제외)다. 다시 완주한 적이 없으면 처음 완주와 같다. 쿠키 없이 부른다 |

| `POST /api/v1/climb/admin/session` `{ "key": "..." }` | 맞으면 `204` + 관리자 쿠키. 틀리면 `401 ADMIN_KEY_MISMATCH`. 틀린 시도가 IP당 1분에 5번을 넘으면(6번째부터, 맞는 키도) `429 ADMIN_ATTEMPTS_LIMITED`. 키는 본문으로만 받고 해시끼리 `MessageDigest.isEqual` 로 비교한다 |
| `GET /api/v1/climb/admin/session` | 유효한 관리자 쿠키면 `204`, 아니면 `401 ADMIN_UNAUTHORIZED` |
| `DELETE /api/v1/climb/admin/session` | 세션을 끝내고 쿠키를 지운다(`Max-Age=0`). `204` |

관리자 끝점 셋은 관리자 키가 비어 있으면 모두 `404 ADMIN_DISABLED` 다. 손님 쿠키는 필요 없다.

순위 항목: `{ "rank", "tag", "nickname", "character", "best", "finished", "time" }`. `time` 은 완주 시간(초), 완주 전이면 `null`.

## 웹소켓 `/api/v1/climb/ws`

쿠키가 없으면 핸드셰이크 `401`, 허용되지 않은 출처면 `403`. JSON 텍스트, 메시지마다 `t` 가 있다. 메시지는 16KB 까지.

브라우저 → 서버

```json
{ "t": "hello", "v": 1, "build": 1, "map": "v1", "character": "angry" }
{ "t": "pos", "h": 120, "best": 131, "falls": 4, "time": 95, "x": 18 }
{ "t": "zone", "zone": 1, "time": 96 }
{ "t": "finish", "time": 640, "falls": 12 }
{ "t": "restart" }
```

- 숫자 필드는 모두 정수만 받는다(`1.5`, `"3"` 은 `INVALID_MESSAGE`). `h`, `best` 는 칸, `time` 은 게임 시간(초, 내림), `x` 는 가로 위치(픽셀, 반올림한 정수)다.
- `hello` 는 연결 직후 한 번. `character` 는 `defeated`, `tired`, `frustrated`, `annoyed`, `angry` 중 하나.
  같은 맵에 완주하지 않은 등반이 있으면 이어 하고, 없으면 새 등반을 만든다.
- `build` 는 게임 빌드 번호(정수, 없으면 0). `pheeeew.climb.min-client-build`(기본 1, 0 이면 보지 않음)보다 낮으면 등반을 만들지 않고
  `CONNECTION_REPLACED` 로 닫는다. 옛 게임은 이 신호로 멈추고 새로 고침 버튼을 띄운다. 관리자 연결도 같다.
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

서버는 15초마다 Ping 을 보낸다(브라우저가 자동으로 Pong 한다). 일반 플레이는 연결이 끊기면 일시정지하고, 다시 연결하면 `hello` 부터 시작해 이어 한다. 닉네임 등록과 실시간 연결이 필수이며 오프라인 혼자 하기는 제공하지 않는다.

핸드셰이크에 유효한 `climb_admin` 쿠키가 있으면 관리자 연결이다(손님 쿠키도 필요하다). 등반을 만들지 않아서 `pos`·`zone`·`finish`·`restart` 는
오류 없이 버리고(`welcome` 의 `run` 은 `null`), 기록·순위·`event` 에 넣지 않는다. `st` 는 받아 두지만 다른 사람에게 보내지 않는다.
연결은 그대로 두고 `rank`(`me` 는 `null`)와 가까운 사람의 `in`·`sts`·`out`, 다른 사람의 `event` 는 받는다. 게임도 관리자 모드에서는 `hello` 와 `st` 만 보낸다(이중 장치).

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
`climb_hall`(`V20261112_91__create_climb_hall.sql`): 명예의 전당. 손님·맵마다 첫 완주(의심 기록 제외) 한 줄을 그때의 닉네임·꼬리표와 함께 남긴다.
외래 키가 없어 손님 정리·처음부터 다시와 상관없이 남는다. 완주 때와 1분마다 `climb_run` 에서 빠진 완주를 옮긴다(배포 중 옛 서버가 받은 완주도 뒤늦게 들어온다).
`best_seconds`·`best_falls`·`best_finished_at`(`V20261113_92__add_climb_hall_best.sql`): 가장 빠른 완주. 같은 때에 `climb_run` 의 의심 아닌 완주 중 더 빠른 것이 있으면 이것만 바꾼다(시간이 같으면 먼저 낸 기록). 순서와 처음 완주 값은 그대로다. 옛 서버가 넣은 줄을 위해 null 을 허용하고, 읽을 때 null 이면 처음 완주 값을 쓴다.
같은 마이그레이션이 행사 운영자 요청으로 파라디(#f88b)의 완주 기록을 한 번 지웠다.
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

## 감정이 계단 러시 (`/stairs`)

손님 쿠키·`/me`·`/guests` 없이 플레이하며, 매 판 결과 화면에서 닉네임을 입력해 선택적으로 등록한다. 캐릭터 식별자 5종은 기존 등반과 같다.
기존 등반 기록이나 웹소켓에는 영향을 주지 않는다. 웹의 `net.js` BUILD는 그대로다.

| 요청 | 응답·조건 |
|---|---|
| `GET /api/v1/climb/stairs/today` | `{ day: "2026-11-13", seed: 1369517937 }` 형태. 쿠키 불필요. 날짜는 Asia/Seoul, seed는 `(int) day.toEpochDay() ^ 0x51a17e53`이다. |
| `POST /api/v1/climb/stairs/runs` | 쿠키 불필요. `{ day, nickname, character, inputs: [{d:"L", t:0}, {d:"R", t:180}] }`. 응답 `{ height, timeMs, best:{height,timeMs}, rank }`. |
| `GET /api/v1/climb/stairs/ranking?day=2026-11-13` | 쿠키 불필요. 날짜 생략 시 오늘. `{ top:[{rank,nickname,character,height,timeMs}], total }`. top은 닉네임별 그날 최고 상위 200개이며 total은 해당 날짜의 전체 참가 닉네임 수다. tag·me는 없다. 시작·결과 화면은 상위 10명, 전체 순위 창은 최대 200명을 보여 준다. |

점수는 성공한 칸 수. `timeMs`는 첫 입력부터 **마지막 성공 입력**까지다. 첫 입력의 `t`는 반드시 0이고,
이후는 엄격히 증가하는 정수 ms다. 처음 틀린 방향에서 재생을 멈추므로 그 이후 입력은 점수에 포함하지 않는다.
시간 만료로 끝난 판은 만료 전까지 성공한 입력만 제출한다. 첫 입력 전에는 게이지가 줄지 않는다.
서버가 같은 seed로 모든 성공 방향과 입력 사이 게이지를 재생하고, 중간에 게이지가 0 이하이면 거절한다.
입력은 1~5000개, 한 판 최대 600,000ms, HTTP 본문은 **160,000 bytes**까지다(Content-Length 없는 요청도 제한).
정수가 아닌 시간(소수·문자열·누락)은 변환하지 않고 거절한다. 제출 날짜는 오늘 또는 **한국 시간 00:00:00~00:04:59의 어제**만 허용한다.

### 공통 정수 규칙 v2

구현: 웹 `public/stairs/rules.js`, Java `StairsRules`. 게이지 단위는 정수이며 화면 비율 표시만 소수를 사용한다.
Java의 곱셈은 long, JS는 Number 안전 정수 범위 안에서 계산한다. 연속 프레임마다 소수를 누적하지 않는다.

| 항목 | 양쪽의 동일 상수·수식 |
|---|---|
| 초기 게이지 | `cap(0) = 6_000_000` |
| 한 칸 성공 시 보충 | `min(cap(h), 남은 게이지 + 400_000)`. h는 **오른 뒤의 높이**. |
| 높이 h에서 ms당 감소량 | 예전 규칙 `drainRate(h) = 1000 + h * 6` |
| 최대 버팀 시간 | `capMs(h)`: `(0,6000), (300,2000), (600,1200)` 사이 정수 내림 선형 보간. 600칸 이상은 1200ms 고정. |
| 저장량 상한 | `cap(h) = capMs(h) * drainRate(h)` |
| 화면 비율 | `max(0, min(1, 현재 게이지 / cap(현재 높이)))` |
| 다음 입력 직전 게이지 | `직전 성공 후 게이지 - (현재 t - 직전 t) * drainRate(h)` |
| 만료 | 보충 **전** 게이지 `<= 0`. 실패 입력으로 보충하지 않는다. |
| 과속 | 입력 사이 `< 45ms`가 **3회 연속**이면 `400 TOO_FAST`. 45ms 이상이면 연속 횟수를 0으로 초기화한다. 간헐적 멀티터치 1~2회는 허용한다. |
| 생성기 | 첫 10칸은 같은 방향 5칸씩. 이후 묶음마다 `x = seed ^ ((run+1) * 0x9e3779b9)`에 xorshift32(13,17,5). JS는 `Math.imul`, Java는 int 오버플로, unsigned 변환 후 같은 정수 나머지를 사용한다. |
| 묶음·제한 | 일반 묶음은 1~6칸. 1칸 묶음 최대 2연속(번갈아 최대 4칸), 같은 길이 최대 3연속. 연습 구간 경계부터 제한을 적용한다. |
| 함정·난도 | 묶음 길이 `(3~4,1,3~4)`, `(2,1,3~6)`, `(1,1,3~6)`. 새 조각 선택 시 함정 확률은 10칸 10% → 50칸 40% → 800칸 60%, 구간별 정수 내림 보간. 조각 경계에도 제한을 적용한다. |

방향 함수는 `(seed, 0부터 시작하는 칸 번호)`만 사용한다. 그림·프레임·기기·캐릭터와 무관하다.
공유 시험값은 `src/test/resources/stairs/generator-v2.txt`(6개 seed × 2000칸), `gauge-v2.csv`(252개, 구간 경계·정확히 0 포함), `replay-v2.csv`(고정/변동 간격 8개 입력 시나리오)다.
게이지 CSV 열은 `시작 게이지,h,경과 ms,남은 게이지,h+1에서 보충 후 게이지,capMs(h),cap(h)`다.
Java와 JS 시험이 **같은 고정 파일**을 읽고 마지막 성공 칸·시각, 만료 입력 시각·게이지를 검증한다.
104개 seed마다 11~2000칸에서 5칸 번갈아 0건, 같은 묶음 길이 4회 반복 0건, 길이 1/2/3/4/5/6 이상 각각 묶음 수의 5% 이상,
양옆 3칸 이상인 역방향 1칸 함정 30개 이상, 4칸 번갈아 창 비율 5% 미만을 검사한다.

유지에 필요한 초당 칸 수는 `drainRate(h) * 1000 / 400000`이다. 최대 버팀 시간은 해당 높이에서 게이지가 꽉 찬 상태 기준이며, 그 시간에 도달하면 만료된다.
보간은 `floor((ms0 * (h1-h) + ms1 * (h-h0)) / (h1-h0))`로 계산한다. 양수 가중합을 나누므로 Java 정수 나눗셈과 JS 내림이 같다.

| 높이 | 필요 칸/초 | 최대 버팀 시간 |
|---|---:|---:|
| 0 | 2.5 | 6000ms (6초) |
| 100 | 4 | 4666ms (4.666초) |
| 300 | 7 | 2000ms (2초) |
| 500 | 10 | 1466ms (1.466초) |
| 800 | 14.5 | 1200ms (1.2초) |

일정한 333/200/125ms 간격 입력은 각각 113/277/488번째 입력에서 게이지가 만료된다.
300칸에서 꽉 찬 상태는 1999ms 정지 후 입력 성공, 2000/2001ms 정지 후 `GAUGE_EXPIRED`다.
300칸 이후 잔량이 적은 공유 재생 시나리오에서는 마지막 성공 뒤 500ms 정지도 만료된다. 꽉 찬 상태가 500ms 만에 만료되는 규칙은 아니다.
실제 플레이는 방향 오입력으로 더 일찍 끝날 수 있다.

### 저장·정렬·오류

새 마이그레이션 `V20261113_93__create_climb_stairs.sql` 하나로 판 기록 `climb_stairs_run`, 일일 최고 `climb_stairs_best`를 만든다.
판 저장과 최고 갱신은 PostgreSQL 단일 CTE 문이다. `(nickname, day)` 충돌 시 높이가 더 크거나,
높이가 같고 시간이 더 짧을 때만 갱신하므로 동시 제출이 나쁜 기록으로 덮어쓰지 않는다.
순위는 높이 내림차순 → 시간 오름차순 → 최고 기록 저장 시각 오름차순 → 판 ID 순서이며 1부터 순번을 매긴다.
동일 높이·동일 시간은 기존 최고와 등록 순서를 유지한다. 손님 테이블과 연결하지 않는다.
응답 `height/timeMs`는 제출한 판, `best`와 `rank`는 해당 닉네임의 그날 최고 기록과 순위다(200위 밖도 반환).
닉네임은 앞뒤 공백을 정리한 뒤 한글 완성형·영문·숫자 1~10자만 허용하며, 대소문자를 구분한다.
기존 등반의 문자 검증을 재사용하되 등반의 최소 2자 규칙은 유지한다. 같은 닉네임은 누구나 같은 순위 항목을 갱신할 수 있다.

등록은 기존 `ClimbRateLimit`으로 IP당 고정 1분 창에 30회까지 허용한다(실패 요청 포함, 프로세스별 메모리).
IP는 기존 등반처럼 `request.getRemoteAddr()`를 사용한다. 프록시 설정이 처리한 주소를 쓰며 원시 전달 헤더를 직접 신뢰하지 않는다.
본문·입력 상한은 그대로이며, 전용 보안 체인에서 POST를 permitAll로 허용하고 기존 CSRF 비활성화를 유지한다.
계단 API에서는 손님 쿠키 필터를 건너뛰며 웹 요청은 `credentials: omit`으로 쿠키 전송·갱신을 하지 않는다.

오류는 기존처럼 `{code,message}`다. `400 INVALID_INPUTS`, `400 GAUGE_EXPIRED`, `400 TOO_FAST`,
`400 INVALID_DAY`, `400 NICKNAME_LENGTH`, `400 NICKNAME_CHARACTERS`, `413 BODY_TOO_LARGE`, `429 RATE_LIMITED`를 반환한다.
결과 화면에서 Enter로 등록하고, 빈 닉네임이면 입력칸에 포커스한다. 입력 중 Space·방향키는 게임을 조작하지 않는다.
결과 화면은 좌우 터치 게임 입력을 받지 않는다. 입력칸 밖의 Space 또는 ‘바로 다시 하기’로 등록을 건너뛸 수 있다.
제출 중에도 재시작할 수 있으며 이전 판의 응답은 새 결과 화면을 덮어쓰지 않는다.
실패 시 같은 결과 화면에서 닉네임을 수정하거나 ‘기록 등록’을 다시 눌러 재시도한다. 재시작하면 미등록 판은 버린다.
직전 입력 닉네임과 ‘이 탭 최고’는 메모리만 사용하므로 새로고침·탭 닫기 때 사라진다.
닉네임·신원을 쿠키·localStorage·sessionStorage에 저장하지 않는다. 고른 감정이만 localStorage에 저장한다.
재전송은 판 이력에 중복으로 남을 수 있으나 일일 최고·순위는 변하지 않는다.

### 로컬 확인

1. 기존 백엔드를 준비한 환경에서 `cd WEB/climb && PORT=4325 node server.mjs`로 웹만 실행한다.
2. `http://127.0.0.1:4325/stairs`에서 감정이 선택 → 키보드 또는 좌우 터치 → 결과에서 닉네임 등록(또는 건너뛰기) → 재시작을 확인한다.
3. `cd BE && ./gradlew :climb:test`와 `cd WEB/climb && for f in tools/*_test.mjs; do node "$f" || echo FAIL "$f"; done`으로 시험한다.

저장·동시 갱신·상위 200·전체 참가 인원 및 닉네임 순위 시험은 기존 프로젝트 방식인 **별도 Testcontainers PostgreSQL**을 사용한다.
`climb-local-db`나 이미 실행 중인 8080·4320·4321 서버는 사용하거나 변경하지 않는다.

전체 순위 창은 열 때마다 서버에서 다시 조회한다. 이 탭에서 등록에 성공한 닉네임의 해당 날짜 기록을 강조하고 그 줄로 스크롤한다(200위 밖은 목록에 표시하지 않는다). 제목 옆 참가 인원은 `total` 기준이며 한 닉네임의 여러 판은 한 명으로 센다. 닫기 버튼·바깥 영역·Esc로 닫고, 열린 동안 게임 키보드·터치 입력을 막는다.
