# 피유 등반 실시간 서버 설계 (초안)

작성일 2026-11-12. 상태: 초안. 서버 코드는 아직 한 줄도 쓰지 않았다.

## 1. 목표

- 로그인 없이, 접속한 브라우저마다 **쿠키**로 구분되는 손님으로 플레이한다.
- 처음 들어오면 **닉네임 입력 창**이 뜬다.
- 게임하는 동안 **실시간 순위(최고 높이)** 를 화면에서 본다.
- 2단계(선택): 같은 탑을 오르는 다른 사람의 감정이가 화면에 보인다.

하지 않는 것(1단계): 플레이어끼리 충돌, 채팅, 서버가 물리를 계산하는 방식, 계정 연동.

## 2. 지금 서버에서 확인한 것 (Pheeeew/BE)

| 항목 | 확인한 사실 | 설계에 주는 영향 |
|---|---|---|
| 스택 | Spring Boot 4.1, Java, PostgreSQL(Flyway), Tomcat | 같은 스택으로 `climb` 패키지를 추가한다 |
| 웹소켓 | `spring-boot-starter-websocket`, STOMP 아님. `AbstractWebSocketHandler`를 직접 구현(`PressRaceWebSocketHandler`). `PressRaceWebSocketConfiguration`이 `/api/v1/press-race`를 등록 | 의존성과 컨테이너 설정(메시지 16KB 제한)은 그대로 쓴다. **핸들러 등록 한 줄과 새 핸들러는 필요하다** |
| 인증 | 웹소켓 연결에 `JwtAuthenticationToken`(기기 JWT) 필수. 기기 등록은 Play Integrity / App Attest 검증을 거친다 | **웹 브라우저는 기기 인증을 통과할 수 없다.** 손님 쿠키 인증을 따로 만든다 |
| 보안 설정 | `SecurityConfig`가 경로별로 `authenticated()`를 건다 | `/api/v1/climb/**`는 JWT 없이 열고, 쿠키로 직접 확인한다 |
| 운영 | 연결 끊기 전 `SERVER_DRAINING`으로 닫는 배포 절차, Ping/Pong 감시, 송신 시간 제한(2초) | 같은 방식을 따른다(작게) |
| 허용 출처 | `pheeeew.press-race.allowed-origins` 설정값 | 등반용 허용 출처 설정을 따로 둔다(브라우저라서 필수) |

확인하지 못한 것: 운영 서버 인스턴스 수, nginx의 웹소켓 프록시 설정, 게임을 올릴 도메인.

## 3. 큰 그림

```
브라우저(게임)                         Pheeeew BE
  ├─ POST /api/v1/climb/guests  ─────▶ 손님 만들기 + 쿠키 발급
  ├─ GET  /api/v1/climb/me      ─────▶ 쿠키로 손님·진행 조회
  └─ WS   /api/v1/climb/ws      ◀────▶ 높이 보고 ↑ / 순위 ↓ (1초마다)
                                        ├─ 메모리: 접속자, 순위표
                                        └─ PostgreSQL: 손님, 기록
```

- **물리는 브라우저가 계산한다.** 서버는 높이 보고를 받아 순위를 만들고 뿌린다.
- 순위표는 서버 메모리에 두고, 기록은 주기적으로 DB에 쓴다.

## 4. 손님 신원 (쿠키)

### 흐름
1. 게임이 열리면 `GET /api/v1/climb/me`를 부른다.
2. `401`이면 닉네임 입력 창을 띄운다.
3. `POST /api/v1/climb/guests { "nickname": "..." }` → 서버가 손님을 만들고 쿠키를 내려 준다.
4. 감정이를 고르고, 웹소켓에 연결한다(쿠키가 핸드셰이크에 자동으로 실린다).

### 쿠키
| 속성 | 값 | 이유 |
|---|---|---|
| 이름 | `climb_guest` | |
| 값 | 무작위 32바이트(base64url) | 추측 불가. 서버 DB에는 **SHA-256 해시만** 저장한다 |
| `HttpOnly` | 켬 | 스크립트가 읽지 못하게 |
| `Secure` | 켬 | HTTPS 전용 |
| `SameSite` | `Lax` | 게임과 API가 같은 사이트(하위 도메인)로 결정됨 |
| `Max-Age` | 180일, 접속할 때마다 연장 | |
| `Path` | `/api/v1/climb` | 다른 API에 실리지 않게 |

### 닉네임 규칙
- 2~10자, 한글·영문·숫자. 앞뒤 공백 제거. 금칙어 검사(기존 `DeviceNickname` 규칙이 있으면 재사용).
- 중복 허용. 순위표에서는 같은 이름을 구분하려고 짧은 꼬리표(예: `피유#4f2a`)를 붙인다.
- 변경: `PATCH /api/v1/climb/me { "nickname" }`, 하루 3번.

### 남용 방지
- 손님 만들기: IP당 시간당 10번.
- 쿠키를 지우면 새 손님이 된다(기록이 이어지지 않는다). 로그인이 없으므로 받아들인다.

## 5. 실시간 순위

### 순위의 기준
- **최고 높이(칸)**. 같으면 먼저 도달한 사람이 위.
- 완주자는 높이가 같으므로 **걸린 시간**이 짧은 순.

### 순위표 종류 (6절 결정 2)
| 표 | 내용 | 1단계 |
|---|---|---|
| 지금 접속자 순위 | 지금 플레이 중인 사람들의 현재 최고 높이 | 넣는다 |
| 오늘의 순위 | 오늘(KST) 기록한 최고 높이 | 넣는다 |
| 역대 순위 | 맵 버전별 전체 기록 | 넣는다(완주자 명예의 전당 겸용) |

### 화면에 보내는 것
- 상위 10명 + 내 순위 + 내 위아래 1명씩. **1초마다**(바뀐 게 있을 때만).
- 접속자 수.
- 사건 알림(선택): "○○ 님이 2구간을 넘었어요", "○○ 님이 완주했어요".

## 6. 웹소켓 규약 `/api/v1/climb/ws`

JSON 텍스트. 모든 메시지에 `t`(종류)가 있다. 메시지 크기 제한은 기존과 같은 16KB.

### 브라우저 → 서버
| `t` | 내용 | 주기 |
|---|---|---|
| `hello` | `{ v: 규약 버전, map: 맵 버전, character }` | 연결 직후 1번 |
| `pos` | `{ h: 현재 높이, best: 최고 높이, falls, time, x }` | **착지할 때마다**, 그리고 최대 2초에 1번 |
| `zone` | `{ zone: 넘은 구간 번호, time }` | 구간을 넘을 때 |
| `finish` | `{ time, falls }` | 완주할 때 |

### 서버 → 브라우저
| `t` | 내용 | 주기 |
|---|---|---|
| `welcome` | `{ me: { id, nickname, tag }, run: 저장된 진행, online, serverTime }` | 연결 직후 |
| `rank` | `{ top: [{ tag, nickname, character, best, finished, time }], me: { rank, best }, near: [...], online }` | 1초마다(변화 있을 때) |
| `event` | `{ kind: 'zone' \| 'finish', nickname, zone }` | 일어날 때 |
| `error` | `{ code }` 후 연결 종료 | |

### 연결 관리 (기존 프레스 서버 방식을 따름)
- Ping 15초, Pong 없으면 30초에 종료.
- 같은 손님이 새로 연결하면 이전 연결을 `CONNECTION_REPLACED`로 닫는다(탭 2개 방지).
- 배포할 때 `SERVER_DRAINING`으로 닫는다. 브라우저는 1초, 2초, 4초… 간격으로 다시 연결한다.
- 손님당 메시지 초당 10개 제한. 넘으면 종료.
- 송신 대기열은 손님당 최신 `rank` 1개만 유지한다(밀리면 옛것을 버린다).

## 7. 부정 방지

서버가 물리를 계산하지 않으므로 완벽히 막을 수는 없다. 순위표가 망가지지 않을 정도로 한다.

| 검사 | 규칙 |
|---|---|
| 범위 | `0 ≤ h ≤ 맵 꼭대기`, `best ≥ h` |
| 상승 속도 | 한 번의 보고 사이에 오른 높이가 물리 한계를 넘으면 버린다(보통 점프 8칸, 분사 판 16칸. 2초에 20칸 초과는 불가능) |
| 순서 | 구간을 건너뛴 `zone`, 구간을 다 넘지 않은 `finish`는 버린다 |
| 시간 | 서버가 연결 시간을 따로 잰다. 보고된 `time`이 서버 시간보다 크게 짧으면 버린다 |
| 완주 최소 시간 | 맵 버전별 하한(예: 5분) 미만 완주는 순위에서 뺀다 |
| 맵 버전 | `hello.map`이 서버가 아는 버전이 아니면 순위에 넣지 않는다 |

의심 기록은 지우지 않고 `suspect` 표시만 해서 순위에서 숨긴다.

## 8. 저장 (PostgreSQL, Flyway)

```sql
-- 손님
create table climb_guest (
  id            uuid primary key,
  tag           varchar(4)  not null,           -- 닉네임 뒤 꼬리표
  nickname      varchar(10) not null,
  token_hash    bytea       not null unique,    -- 쿠키 값의 SHA-256
  created_at    timestamptz not null,
  last_seen_at  timestamptz not null
);

-- 한 번의 등반(처음부터 다시 하면 새 줄)
create table climb_run (
  id            uuid primary key,
  guest_id      uuid not null references climb_guest(id),
  map_version   varchar(16) not null,
  character     varchar(16) not null,
  best_height   int  not null default 0,
  best_at       timestamptz,
  height        int  not null default 0,        -- 이어하기용 현재 위치
  x             int  not null default 0,
  falls         int  not null default 0,
  play_seconds  int  not null default 0,
  finished_at   timestamptz,
  finish_seconds int,
  suspect       boolean not null default false,
  started_at    timestamptz not null,
  updated_at    timestamptz not null
);
create index climb_run_rank on climb_run (map_version, best_height desc, best_at) where not suspect;
create index climb_run_finish on climb_run (map_version, finish_seconds) where finished_at is not null and not suspect;
```

- DB 쓰기: 손님당 10초에 1번 이하 + 구간 통과·완주·연결 종료 때. 착지할 때마다 쓰지 않는다.
- 진행 저장: 지금은 브라우저 `localStorage`에 있다. 서버에도 저장하면 쿠키가 살아 있는 한 이어할 수 있다. `localStorage`는 서버가 안 될 때의 예비로 남긴다.
- 180일 동안 접속이 없는 손님과 그 기록은 지운다(역대 순위에 오른 기록은 닉네임만 남긴다).

## 9. 서버 구조: 게임 전용 Gradle 모듈 `climb`

결정(2026-11-12): Pheeeew BE에 **게임 전용 모듈**을 만들고 등반 관련 코드는 전부 거기에 둔다.

### 지금 BE의 모양
- `settings.gradle`: 루트 `pheeeew` + 하위 모듈 `observability` 1개. 앱 본체는 루트의 `src/`에 있다.
- 그래서 같은 방식으로 하위 모듈 `climb`을 추가한다(`observability`가 선례다).

```
BE/
├─ settings.gradle            // include 'climb' 한 줄 추가
├─ build.gradle               // implementation project(':climb') 한 줄 추가
├─ src/ ...                   // 앱 본체. 등반 코드는 넣지 않는다
└─ climb/
    ├─ build.gradle           // java-library, Spring BOM (observability/build.gradle 과 같은 틀)
    └─ src/
        ├─ main/java/com/pheeeew/climb/
        │   ├─ ClimbSecurityConfiguration      // 등반 전용 보안 필터 체인
        │   ├─ presentation/
        │   │   ├─ ClimbWebSocketConfiguration // /api/v1/climb/ws 등록, 허용 출처
        │   │   ├─ ClimbGuestHandshakeInterceptor // 쿠키 → 손님 확인
        │   │   ├─ ClimbWebSocketHandler       // 메시지 해석, 연결 관리
        │   │   └─ ClimbGuestController        // POST /guests, GET·PATCH /me, GET /ranking
        │   ├─ application/
        │   │   ├─ ClimbGuestService           // 손님 만들기, 쿠키 토큰
        │   │   ├─ ClimbRunService             // 높이 보고 검사, 기록 갱신
        │   │   └─ ClimbRankingService         // 메모리 순위표, 1초 방송
        │   ├─ domain/                         // ClimbGuest, ClimbRun, ClimbNickname, ClimbMap
        │   └─ infra/                          // ClimbGuestRepository, ClimbRunRepository
        ├─ main/resources/db/migration/        // V..__create_climb_tables.sql (등반 테이블만)
        └─ test/java/com/pheeeew/climb/        // 모듈 단위 테스트
```

### 모듈 경계 규칙
1. **`climb`은 앱 본체의 어떤 패키지도 쓰지 않는다**(`device`, `emotion`, `pressrace`, `auth` 등). Gradle 의존 방향이 `본체 → climb` 한쪽이라 컴파일 단계에서 막힌다.
2. 본체도 `climb`의 클래스를 직접 부르지 않는다. 스프링이 `com.pheeeew` 아래를 훑어 빈으로 올릴 뿐이다(패키지가 `com.pheeeew.climb`이라 컴포넌트 스캔, 엔티티 스캔, JPA 저장소 스캔에 그대로 잡힌다).
3. 본체에서 고치는 것은 **두 줄**뿐이다: `settings.gradle`의 `include 'climb'`, `build.gradle`의 `implementation project(':climb')`.
4. 닉네임 금칙어처럼 본체와 같이 쓰고 싶은 규칙이 있어도 `climb`이 본체를 참조하지 않는다. 필요하면 규칙을 `climb` 안에 따로 둔다(작은 중복을 받아들인다).

### 보안: 본체의 `SecurityConfig`를 고치지 않는다
- `climb` 모듈이 자기 `SecurityFilterChain`을 따로 등록한다. `securityMatcher("/api/v1/climb/**")` + 본체 체인보다 앞선 `@Order`.
- 이 체인 안에서만: JWT 검사 없음, 손님 쿠키 필터, 게임 출처 하나만 허용하는 CORS(`allowCredentials`), CSRF는 `SameSite=Lax` + 출처 검사로 막는다.
- 본체 체인은 `/api/v1/climb/**`를 보지 않으므로 기존 기기 JWT 규칙에 영향이 없다.

### 웹소켓: 기존 설정을 고치지 않는다
- `ClimbWebSocketConfiguration`이 `WebSocketConfigurer`를 하나 더 구현해 `/api/v1/climb/ws`만 등록한다. 스프링은 여러 `WebSocketConfigurer`를 모두 적용한다.
- 메시지 크기 제한(16KB)을 정하는 컨테이너 빈은 본체의 `pressRaceWebSocketContainer`가 이미 등록한다. 서블릿 컨테이너 전체에 적용되므로 `climb`은 따로 만들지 않는다.
- `PressRaceWebSocketHandler`, `PressSnapshotSender`는 참조하지 않는다(경계 규칙 1). Ping/Pong 감시와 송신 제한은 같은 방식으로 `climb` 안에 작게 다시 만든다.

### DB
- 같은 PostgreSQL, 같은 Flyway. 마이그레이션 파일을 `climb/src/main/resources/db/migration/`에 두면 클래스패스에서 합쳐져 같이 실행된다. 버전 번호(`V날짜_순번`)가 본체 파일과 겹치지 않게 한다.
- 테이블 이름은 전부 `climb_`로 시작한다. 본체 테이블에 외래 키를 걸지 않는다.

### 설정
- `pheeeew.climb.enabled`(기본 꺼짐): 꺼져 있으면 `climb`의 빈이 하나도 올라오지 않는다(`@ConditionalOnProperty`). 이벤트 기간에만 켜거나, 문제가 생기면 끄고 다시 배포한다.
- `pheeeew.climb.allowed-origin`: 게임 주소 하나.
- `pheeeew.climb.map-versions`: 맵 버전별 꼭대기 높이와 구간 높이.

### 테스트
- `climb` 모듈 안: 도메인, 서비스, 순위표, 메시지 검사 단위 테스트(스프링 없이).
- 본체 쪽: 통합 테스트 1~2개(쿠키 발급 → 웹소켓 연결 → 높이 보고 → 순위 수신). 본체의 Testcontainers(PostgreSQL)를 그대로 쓴다.

### 순위표와 방송
- 순위표: 메모리의 정렬 구조(`ConcurrentSkipListSet`). 서버가 뜰 때 DB에서 상위 100명을 읽어 채운다.
- 방송: 스케줄러 1개가 1초마다 상위 10명을 한 번 만들고, 접속자마다 "내 순위" 부분만 붙여 보낸다.
- REST `GET /api/v1/climb/ranking?board=today|all`은 웹소켓 없이도 순위를 볼 수 있게 한다(제목 화면용).

### 규모
- 접속자 1,000명 기준: 들어오는 보고 초당 약 500~1,000개(착지 간격 1~2초), 나가는 방송 초당 1,000개 × 약 1KB. Tomcat 한 대로 충분하다.
- **서버가 2대 이상이면** 메모리 순위표가 서버마다 달라진다. 그때는 (가) 순위 방송만 DB 조회로 바꾸거나 (나) Redis를 둔다. 1단계는 1대 기준으로 만들고, 인스턴스 수를 확인한 뒤 정한다(6절 결정 3).

## 10. 게임(브라우저) 쪽 변경

- 닉네임 입력 창(첫 접속), 닉네임 표시.
- HUD에 순위 패널: 상위 5명 + 내 순위. 접었다 펼 수 있게.
- 진행 막대(오른쪽 탑)에 다른 사람들의 최고 높이를 점으로 표시.
- 연결이 끊겨도 게임은 계속된다(혼자 하기). 다시 연결되면 순위가 돌아온다.
- 맵 버전 문자열: 맵이 바뀌면 올린다. 버전이 다르면 순위가 따로 간다.

## 11. 단계

| 단계 | 내용 | 서버 예상 |
|---|---|---|
| 1 | 손님 쿠키 + 닉네임 + 높이 보고 + 실시간 순위(접속자/오늘/역대) + 기록 저장 | 3~4일 |
| 2 | 다른 사람의 감정이가 화면에 보임(위치를 초당 10번 주고받고 사이를 보간). 채널당 20명 | 2~3일 |
| 3 | 초대 코드 방, 친구끼리 경주(동시 출발), 완주 명예의 전당 꾸미기 | 미정 |

2단계 메모: 물리가 결정론적이라 "점프 입력(위치, 방향, 차지)"만 보내고 상대 브라우저가 재생하는 방식도 가능하다. 통신량은 가장 적지만 어긋남 보정이 필요해서, 위치 보간 방식을 먼저 권한다.

## 12. 정해야 하는 것

1. ~~게임을 어느 주소에 올리나?~~ **결정(2026-11-12): API와 같은 사이트의 하위 도메인.** 쿠키는 `SameSite=Lax`, `Domain`은 지정하지 않고 API 호스트 전용으로 둔다. 브라우저는 `fetch(..., { credentials: 'include' })`로 부르고, 서버는 CORS에서 게임 출처 하나만 허용 + `Access-Control-Allow-Credentials: true`. 웹소켓 핸드셰이크는 허용 출처 검사로 막는다.
2. **순위표 범위**: 접속자 / 오늘 / 역대 중 무엇을 게임 중에 기본으로 보여 줄까? 권장: 게임 중에는 "지금 접속자", 제목 화면에는 "오늘"과 "역대".
3. ~~운영 서버 인스턴스 수~~ **결정(2026-11-12): 1대.** 순위표는 서버 메모리에 둔다. 배포로 서버가 다시 뜨면 DB에서 상위 기록을 읽어 다시 채우고, 접속자는 자동으로 다시 연결한다. 나중에 2대 이상이 되면 9절의 대안을 적용한다.
4. **진행 저장을 서버에도 할까?** 권장: 한다(쿠키가 있으면 이어하기). `localStorage`는 예비.
5. **닉네임 중복**: 허용 + 꼬리표(권장) / 중복 금지.
6. **크리스마스 이벤트 기간에만 여는가?** 그렇다면 순위표를 이벤트 단위로 나눈다(`map_version`에 이벤트 이름을 넣으면 된다).
