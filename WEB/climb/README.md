# 감정이들의 점프 대모험

감정이(좌절이, 지침이, 답답이, 짜증이, 분노) 하나를 골라 높은 탑을 오르는 웹 게임이다. 스페이스바를 길게 눌러 힘을 모았다가 놓으면 뛴다. 한 번 떨어지면 한참 아래로 미끄러질 수 있다. 뉴욕 빌딩 숲에서 크리스마스 마을, 구름 위 성, 열기구 축제 하늘, 로켓 발사대와 우주를 지나 달까지 6구간이다. 서버가 있으면 닉네임으로 순위에 오르고 함께 오르는 다른 사람이 반투명하게 보인다. 서버가 없으면 혼자 오른다.

## 실행

빌드 단계가 없다(PixiJS 8, ES 모듈).

```
npm start            # http://127.0.0.1:4320
npm run check        # 맵 검증기: 다섯 감정이 모두 꼭대기까지 오를 수 있는지
node tools/admin_test.mjs
node tools/bgm_test.mjs
node tools/net_test.mjs
node tools/ghosts_test.mjs
```

순위·함께 오르기 서버(`/api/v1/climb`)는 `CLIMB_BACKEND`(기본 `http://127.0.0.1:8080`)로 넘긴다.

## 조작

| 키 | 하는 일 |
|---|---|
| ◀ ▶ (A D) | 걷기 |
| Space | 길게 눌러 모으고, 놓으면 점프. 공중에서 방향키를 한 번 누르면 살짝 움직인다 |
| N | 처음부터 |
| Tab | 순위 패널 접기 |
| G | 다른 사람 보이기·숨기기 |
| J | 움직임 방식 비교(젤리 / 예전) |
| M | 소리 전체 끄기·켜기 |
| B | 배경음악만 끄기·켜기 |

주소 옵션: `?debug` 시험 화면(충돌 상자, 화면 경계, `window.game`). 관리자 모드는 `/admin` 경로다.

## 관리자 모드 (`/admin`)

배포 전에 원하는 구간으로 바로 가서 난이도를 직접 해 보는 모드다. 서버가 관리자 키를 확인해 준 뒤에만 켜진다(`?admin` 방식은 없앴다).
키는 서버 환경 변수 `PHEEEEW_CLIMB_ADMIN_KEY` 에만 있고 게임 코드에는 없다. 끝점과 쿠키는 `BE/climb/README.md` 에 있다.

1. `/admin` 으로 들어가면 관리자 세션을 확인한다(`GET /api/v1/climb/admin/session`). 세션이 있으면 바로 켜진다.
2. 없으면 키 창이 뜬다. 키는 요청 본문으로 보낸다. 틀리면 "키가 맞지 않아요", 너무 많이 틀리면 "잠시 뒤에 다시 해 주세요",
   서버에 닿지 않거나 관리자 기능이 꺼져 있으면 "관리자 기능이 꺼져 있어요". 키가 맞을 때까지 게임을 시작하지 않는다. 취소하면 `/` 로 간다.
3. 세션은 8시간이다. 서버가 다시 뜨면 다시 넣는다. 관리자 카드의 "나가기"는 세션을 끝내고 `/` 로 간다.

로컬에서 켜기:

1. `BE/.env` 에 `PHEEEEW_CLIMB_ADMIN_KEY=<키>` 를 넣는다(형식은 `BE/.env.example`).
2. `BE` 에서 `./gradlew bootRun`(local 프로필, 8080).
3. `WEB/climb` 에서 `npm start`(4320, `/api/v1/climb` 를 8080 으로 넘긴다).
4. `http://127.0.0.1:4320/admin` 에 들어가 키를 넣는다.

| 조작 | 하는 일 |
|---|---|
| 게임 화면 클릭 | 그 자리로 옮긴다. 지형 속이면 위쪽 가장 가까운 빈 곳, 공중이면 그 자리에서 떨어진다 |
| PageUp / `]` | 한 화면(18칸) 위의 가장 가까운 발판 |
| PageDown / `[` | 한 화면 아래의 가장 가까운 발판 |
| 1 ~ 6 | 그 구간의 시작 쉼터(1은 시작 지점) |
| Backspace / R | 되돌리기: 마지막 점프(땅을 떠나기) 직전에 서 있던 자리 |
| V | 전체 지도. 누른 곳으로 옮기고 닫힌다 |

- 서버에 아무것도 보고하지 않는다(위치, 구간, 완주, 다시 시작, 자세). 닉네임 창 없이 혼자 하기로 시작한다.
  서버도 관리자 쿠키가 있는 연결의 보고는 순위에 넣지 않는다.
- 저장은 `pheeeew-climb-admin` 키를 따로 쓴다. 실제 기록과 `?debug` 기록을 건드리지 않는다.
- 오른쪽 아래에 높이, 화면 번호, 구간, 칸 좌표를 보인다. `window.game`도 켜진다.

## 배경음악

`public/bgm.js`. 효과음과 같은 AudioContext를 쓰고 첫 키 입력이나 클릭 뒤에 시작한다. 제목·고르기 화면 곡과 6구간 각각의 곡이 있고, 구간 경계를 3칸 넘어가면 2초에 걸쳐 넘어간다. 필요한 곡만 그때 받고, 다음 구간 경계 6칸 앞에서 다음 곡을 미리 받는다. 탭이 숨겨지면 멈춘다.

## 크레딧

음악은 모두 CC BY 4.0이다(https://creativecommons.org/licenses/by/4.0/). 자세한 내용과 변경 사항은 [`public/assets/bgm/CREDITS.md`](public/assets/bgm/CREDITS.md)에 있다.

- "Clear Blue Sky", "Tranquil Fields (Peaceful - Loop Ready)", "Space Ambience", "Moon" by Alexander Nakarada (https://creatorchords.com)
- "Bending Air 78 BPM Loop" — Music by Josh Lim (https://www.joshjameslim.com), from "cozy cats - lofi beat loops to game to" (https://joshjameslim.itch.io/cozy-cats-lofi-beat-loops-to-game-to)
- "With Me" by Sabine Bloch x Fachhochschule Dortmund, OER Tracks (https://www.orca.nrw/oer/oer-erstellen/oer-tracks/)
- "Magic Lights (CALM CLASSICAL)" — Music by Keys of Moon (Serjo De Lua) (https://soundcloud.com/keysofmoon, https://www.youtube.com/c/keysofmoonmusic), source: https://soundcloud.com/keysofmoon/magic-lights

변경 사항: WAV 2곡("Bending Air", "With Me")은 웹용 AAC 192kbps로 변환했다. MP3 5곡은 파일을 바꾸지 않았다. 게임에서 곡마다 음량을 맞추고, 반복할 때 앞뒤 무음을 건너뛰거나 끝 3초를 처음과 겹친다.

## 배포

Cloudflare Pages에 `WEB/climb/public` 폴더를 그대로 올린다. 빌드 단계와 자동 배포는 없다.
아래 명령은 **저장소 루트**에서 실행한다.

| 환경 | 게임 주소 | API 주소 | Pages 프로젝트 |
|---|---|---|---|
| 개발 | https://climb-dev.pheeeew.com | https://api-dev.pheeeew.com | `pheeeew-climb-dev` |
| 운영 | https://climb.pheeeew.com | https://api.pheeeew.com | `pheeeew-climb` |

```sh
# 개발
npx wrangler pages deploy WEB/climb/public --project-name pheeeew-climb-dev --branch main

# 운영
npx wrangler pages deploy WEB/climb/public --project-name pheeeew-climb --branch main
```

`index.html`이 게임을 불러오기 전에 `api-config.js`의 호스트 선택 함수로 `window.CLIMB_API`를 정한다.
운영·개발 게임 도메인만 각각의 API `/api/v1/climb`을 쓴다. `localhost`, `127.0.0.1`, `*.pages.dev` 등은
같은 출처의 `/api/v1/climb` 기본값을 유지한다. 로컬은 `server.mjs`가 백엔드로 넘긴다.
Pages 기본·미리 보기 주소에는 API 프록시가 없고 서버의 허용 출처도 아니므로 혼자 하기로 동작한다.
Pages가 API 경로에도 HTML을 `200`으로 반환하면 서버에 닿지 않은 것으로 처리한다.
REST 요청에는 쿠키를 포함하며, 웹소켓은 같은 API 주소를 `https` → `wss`로 바꿔 연결한다.

`_redirects`는 `/admin`, `/admin/` 요청에 `index.html`을 반환한다.
`_headers`는 `/assets/*`에 7일, JS·MJS·CSS·HTML에 5분의 브라우저 캐시를 설정한다.
파일 이름에 버전이 없으므로 `immutable`은 사용하지 않는다. 같은 이름의 그림·음악을 교체하면
기존 브라우저 캐시가 최대 7일 남을 수 있다.

### 병합·배포 순서

1. #1204 병합 → #1205의 대상을 `be/dev`로 바꿔 병합 → 등반 켜기 PR의 대상을 `be/dev`로 바꿔 병합한다.
2. `be/dev` → `develop` PR을 만들고 CI 통과 후 병합한다. 개발 서버 배포가 끝나면 개발용 게임을 Pages에 올려 확인한다.
   개발 CD는 `develop` push에서 실행되므로 `be/dev` 병합만으로는 배포되지 않는다.
3. `develop` → `main` PR을 만든다. 운영 반영 승인을 받아 병합하고 기존 수동 운영 배포 절차로 서버를 배포한 뒤 운영용 게임을 올린다.
   전체 반영 경로는 `be/dev` → `develop` → `main`이다.

이 변경이 서버에 배포되는 순간 해당 환경의 등반 API가 켜진다.
게임을 올리기 전에는 Pages에 자리 표시 페이지만 보이지만 API는 이미 접근할 수 있다.

### 올린 뒤 확인

- 처음 방문한 브라우저에서 닉네임 창이 뜨고 순위 패널이 보이는지 확인한다.
- `/admin`과 `/admin/`에서 기존 관리자 세션이 없으면 키 입력 창이 뜨는지 확인한다.
- 브라우저 콘솔에 CORS·쿠키 오류가 없고, API 요청의 쿠키와 `wss` 연결이 정상인지 확인한다.
- 개발에서 위 항목을 먼저 확인한 뒤 운영 게임을 올린다. 실제 도메인의 쿠키·CORS는 배포 후 확인한다.

### 되돌리기와 한도

- 게임: Cloudflare 대시보드의 해당 Pages 프로젝트 → Deployments에서 이전 정상 운영 배포의
  `Rollback to this deployment`로 되돌린다. 서버와 호환되는 배포를 선택한다.
- 서버: 해당 `application-dev.yml` 또는 `application-prod.yml`의 `pheeeew.climb.enabled`를 `false`로
  바꾸고 기존 절차로 서버를 다시 배포한다.
- [Pages 파일 크기 한도](https://developers.cloudflare.com/pages/platform/limits/)는 파일 하나당 **25 MiB**다.
  현재 가장 큰 파일은 약 12 MB로 한도 이내다.
