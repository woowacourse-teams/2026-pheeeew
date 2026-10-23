# 컬렉션 진행도·직접 수령과 감정 수정 계약

관련 이슈: [#839](https://github.com/woowacourse-teams/2026-pheeeew/issues/839), [#840](https://github.com/woowacourse-teams/2026-pheeeew/issues/840)

## 오늘 진행도 조회

`GET /api/v1/collection-cards/today`에 기기 access token을 `Authorization: Bearer ...`로 전달합니다.
날짜와 기기는 서버가 결정합니다. 계정이 아닌 인증된 기기 기준이며 재설치 복구는 보장하지 않습니다.
응답에는 `Cache-Control: no-store`가 설정됩니다.

```json
{
  "date": "2026-10-22",
  "items": [
    {"state":"FRUSTRATED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":70,"target":100,"remaining":30},"card":null},
    {"state":"IRRITATED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null},
    {"state":"EXHAUSTED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null},
    {"state":"DISCOURAGED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null},
    {"state":"ANGRY","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null}
  ]
}
```

- `date`는 한국 시간(`Asia/Seoul`) 기준 오늘 날짜입니다. 다섯 감정은 위 순서로 항상 포함하며 활동이 없으면 `count`는 0입니다.
- `stamps.count`는 해당 날짜·기기·감정으로 성공한 신규 등록 횟수입니다. 메모·녹음 유무나 그룹 선택과 관계없이 집계합니다.
- 등록과 스탬프 집계는 함께 커밋·롤백합니다. 같은 `requestId`의 재시도는 다시 집계하지 않으며, 삭제 후 재시도나 다음 날 재시도도 마찬가지입니다.
- 내용 수정·삭제는 이미 인정한 스탬프 횟수를 바꾸지 않습니다. 프레스는 기존 API에서 서버가 인정한 저장 횟수를 사용합니다.
- 같은 날·같은 감정의 목표는 스탬프 1회와 프레스 100회입니다. `count`는 목표를 넘겨도 유지하고 `remaining`은 `max(target - count, 0)`입니다. 다른 날짜·감정으로 이월하지 않습니다.

`card`는 발급된 카드 정보이며 미발급 상태이면 명시적인 `null`입니다. 위 예시는 미발급 응답입니다.
수령 후에는 아래 직접 수령 응답과 같은 카드 객체를 `items[].card`로 반환합니다.
진행도·카드·이미지 경로는 한 SQL의 같은 스냅샷으로 조회하며 발급 잠금을 획득하지 않습니다.
수령 후에도 횟수는 최신 값으로 증가하지만 카드 식별자와 캐릭터·도장 이미지는 유지합니다.
`card != null`이면 수령 완료, `card == null`이면서 두 `remaining`이 0이면 수령 가능, 나머지는 조건 미달입니다.
진행도 조회는 활동이나 카드를 생성하지 않습니다. 클라이언트가 목표 달성만으로 발급된 카드 정보를 만들어서는 안 됩니다.

등록·프레스 저장 성공 후 진행도를 다시 조회해 서버가 확인한 횟수로 갱신합니다.
자정을 넘긴 응답의 `date`가 바뀌면 전날 진행도를 새 날짜 값으로 교체합니다.

| 상태 | 의미 | 클라이언트 처리 |
| --- | --- | --- |
| 200 | 오늘 진행도 조회 성공 | 날짜와 다섯 감정의 횟수·부족분 표시 |
| 401 | 토큰이 없거나 유효하지 않음, 또는 인증된 기기가 없음 | 기존 기기 인증 복구 흐름 적용 |
| 503, `COLLECTION-001` | 조회 도중 날짜 변경이 반복됨 | 진행도 재조회 |
| 503, `COLLECTION-004` | 수령한 카드의 자산 또는 이미지 주소 설정 미비 | 서버 자산 복구 후 재조회 |

## 카드 직접 수령

`POST /api/v1/collection-cards/claim`에 기기 access token과 다음 JSON을 전달합니다.
화면에서 조회한 날짜를 그대로 보내며 소유 기기는 토큰으로 결정합니다.

```json
{"date":"2026-10-23","state":"ANGRY"}
```

신규 수령과 재요청 모두 HTTP 200과 `Cache-Control: no-store`를 반환합니다.

```json
{
  "cardId":"a8ce0347-6f21-4c62-9a7e-1b30d5e0c9aa",
  "date":"2026-10-23",
  "state":"ANGRY",
  "images": {
    "characterUrl":"https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/characters/angry/03.png",
    "roundStampUrl":"https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-round/angry/06.png",
    "straightStampUrl":"https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-straight/angry/06.png"
  }
}
```

- 추첨 회차(`cycle`)·조합 번호(`combinationIndex`)·자산 버전(`cardVersion`)·수령 시각(`claimedAt`)은 서버 내부에서 관리하며 응답에 포함하지 않습니다.
- 같은 기기·KST 날짜·감정의 커밋된 스탬프 1회 이상 AND 프레스 100회 이상이어야 합니다. 서버가 한 SQL 스냅샷으로 재확인합니다.
- 조건을 달성해도 자동 발급하지 않습니다. 자정까지 버튼을 누르지 않은 카드는 만료되며 소급 발급·조합 소비하지 않습니다.
- 기기·감정별 수령 요청만 직렬화합니다. 잠금 획득 후와 추첨 직전에 KST 날짜를 검사합니다. 마지막 검사를 자정 전에 통과했다면 커밋·응답이 자정 뒤여도 유효합니다.
- 중복 클릭·응답 유실 시 **같은 date·state**로 재요청합니다. 이미 받은 카드는 다음 날에도 같은 결과를 반환합니다.
- 수령 실패는 감정·프레스 활동을 취소하지 않습니다. 카드와 추첨 상태만 함께 커밋·롤백합니다.

| 상태 | 오류 코드 | 처리 |
| --- | --- | --- |
| 400 | `COMMON-001` | 날짜·감정의 누락 또는 잘못된 형식 수정 |
| 400 | `COLLECTION-002` | 당일이 아닌 미수령 요청. 오늘 진행도를 다시 조회 |
| 401 | 기존 인증·기기 오류 코드 | 기기 인증 복구 |
| 409 | `COLLECTION-003` | 조건 미달. 활동 저장 완료 후 진행도 재조회 |
| 503 | `COLLECTION-004` | 자산 또는 이미지 주소 설정 미비. 서버 자산 복구 후 재시도 |

## 카드 이미지 자산

클라이언트는 응답의 `images.characterUrl`, `images.roundStampUrl`, `images.straightStampUrl`을 그대로 로딩합니다.
JSON 다운로드·조합 번호 계산·기본 URL 조립은 필요하지 않습니다.
카드의 배치·폰트는 앱의 고정 디자인을 사용하며 자산 버전을 해석하지 않습니다.
원형·일자형 도장은 별도 PNG이며 앱이 각각 정해진 위치에 배치합니다.

서버의 자산 원천은 RDS의 두 테이블입니다. JSON 파일을 읽거나 S3에 목록 조회를 요청하지 않습니다.

| 테이블 | v1 데이터 | 저장 내용 |
| --- | --- | --- |
| `collection_character_assets` | 5감정 × 16종 = 80행 | 버전·감정·번호·캐릭터 경로·해상도 |
| `collection_stamp_assets` | 5감정 × 16쌍 = 80행 | 버전·감정·번호·원형/일자형 경로·각 해상도 |

카드에는 기존 버전·감정·조합 번호를 저장하고, 조합에서 자동 계산되는 캐릭터/도장 번호로 자산 테이블을 참조합니다.
없는 자산을 참조하는 카드와 발급 카드가 참조하는 자산의 삭제는 외래 키로 막습니다.
같은 버전의 그림은 유지하고 변경 시 새 버전의 자산을 등록합니다. 자산 수정 API는 제공하지 않습니다.

DB의 경로는 `v1/characters/angry/03.png`처럼 `collection-cards/` 기준 상대 경로입니다.
서버는 환경별 `pheeeew.collection.asset-base-url`에 이 경로를 붙여 공개 이미지 URL을 만듭니다.
개발 기본 주소는 `https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/`입니다.
카드에 저장된 버전의 자산을 사용하므로 활성 발급 버전이 바뀌어도 기존 카드를 새 그림으로 대체하지 않습니다.

신규 수령은 `pheeeew.collection.card-version`의 자산 80행·80행이 완전해야 허용합니다.
활성 버전 미설정은 신규 수령만 차단합니다. 기존 카드 응답은 저장된 버전의 자산과 기본 주소가 있으면 반환합니다.
필요한 설정·자산이 없으면 `COLLECTION-004`를 반환하며 카드 재추첨이나 다른 버전 대체를 하지 않습니다.

개발·운영 모두 환경 변수로 자산 설정을 주입할 수 있습니다.

| 환경 변수 | 개발 기본값 | 운영 기본값 |
| --- | --- | --- |
| `COLLECTION_ASSET_BASE_URL` | 없음 — 필수 등록 | 빈 값 |
| `COLLECTION_CARD_VERSION` | 없음 — 필수 등록 | 빈 값 |

개발은 두 환경 변수를 반드시 등록해야 합니다. 기본값은 없으며 누락 시 Spring 설정을 해석할 수 없습니다.
개발 배포 workflow와 Compose도 누락·빈 값을 거부합니다.
운영은 개발 주소나 버전을 기본값으로 사용하지 않습니다. 실제 이미지 업로드·검증과 날짜 마감 경로 준비 후
`COLLECTION_ASSET_BASE_URL=https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/production/collection-cards/`와
`COLLECTION_CARD_VERSION=v1`을 배포 환경에 주입합니다. 기본 주소는 HTTPS이며 끝에 `/`가 있어야 합니다.
운영 환경 변수가 없으면 신규 수령은 `COLLECTION-004`로 차단합니다.

GitHub 저장소 Settings → Environments → `development` / `production` → Environment secrets에서
`COLLECTION_ASSET_BASE_URL`과 `COLLECTION_CARD_VERSION`을 등록합니다. Variables가 아니라 Secrets를 사용합니다.
개발 Secret 값은 위 개발 이미지 주소와 `v1`입니다.
배포 workflow가 Secret을 서버 `/opt/pheeeew/.env`에 기록하고 Compose가 앱 컨테이너에 전달합니다.
개발 Secret은 필수이고 운영 Secret은 미등록 시 빈 값으로 전달해 수령 비활성 상태를 유지합니다.
서버 .env만 수정하면 다음 배포에서 덮어써지므로 지속 설정은 GitHub Environment secrets에서 관리합니다.
등록·변경한 Secret은 다음 배포로 앱 컨테이너를 다시 생성할 때 반영됩니다.

카드 없는 오늘 진행도는 자산 설정 없이도 조회할 수 있지만, 기존 카드가 있는데 이미지 주소 설정이 없으면
카드를 숨기거나 `null`로 대체하지 않고 `COLLECTION-004`를 반환합니다.
자산 테이블에 초기 데이터가 들어가는 것만으로 운영 수령을 활성화하지 않습니다.
이번 개발 업로드에서는 240개 이미지 공개 URL의 로딩과 원본 해상도를 검증했습니다.
DB 등록은 실제 PNG 파일의 보존을 대신하지 않으므로 발급에 사용한 S3 객체는 덮어쓰거나 삭제하지 않습니다.

### 테이블 생성과 초기 데이터

`src/main/resources/db/migration/V20261022_3__create_collection_cards_and_draw_states.sql`에
자산 테이블 생성, v1 캐릭터 80행·도장 쌍 80행 INSERT, 카드·추첨 상태 테이블 생성이 포함됩니다.
개발·운영 모두 동일한 상대 경로 데이터를 사용하고 기본 주소만 환경별로 다르게 설정합니다.
스크립트는 이미지 파일을 업로드하지 않습니다. 수동 SQL 실행 후 Flyway가 중복 실행되게 하지 않습니다.

이 파일은 1번 작업에서 로컬 커밋된 뒤 이번 변경으로 수정됐습니다.
이전 파일을 이미 적용한 영속 DB에는 그대로 적용할 수 없습니다. 적용 이력을 확인하고 별도 증분 migration을 만들어야 합니다.
체크섬을 맞추기 위한 무조건적인 `repair`나 데이터 초기화는 하지 않습니다.
현재 변경은 임시 PostgreSQL/PostGIS Testcontainers에서만 검증하며 개발·운영 RDS에 적용하지 않습니다.
이미 개발 S3에 올린 JSON은 이 구현에서 사용하지 않으며, 이번 작업에서 원격 파일을 삭제하지 않습니다.

## 감정 종류 변경 제한

다음 수정 API 모두에 적용합니다.

- `PUT /api/v1/emotions/{emotionId}`
- `PUT /api/v3/emotions/{emotionId}`
- `PUT /api/v4/emotions/{emotionId}`

요청의 필수 `state` 필드는 유지합니다. 상세 조회로 받은 원래 감정 값을 그대로 전달해야 합니다.
같은 값은 허용하며 다른 값은 HTTP 400과 다음 오류로 거절합니다.

```json
{
  "code": "EMOTION-015",
  "message": "등록한 감정의 종류는 변경할 수 없습니다."
}
```

소유권·삭제 여부를 먼저 확인합니다. 소유자가 아니거나 삭제된 감정에는 기존 404 계약을 유지합니다.
유효한 수정 요청에서 감정 종류가 다르면 새 녹음 확인·연결이나 내용 변경 전에 거절하며 기존 내용과 집계는 유지합니다.

메모 수정, 녹음 유지·교체, 메모와 녹음 사이의 전환은 기존 버전별 계약을 유지합니다.
v1은 내용 없음을 허용하고 v3·v4는 MEMO 또는 AUDIO가 필요합니다.
v1·v3의 그룹 선택 규칙과 v4의 기존 그룹 연결 유지·`groupId` 입력 거부 규칙도 유지합니다.

### 기존 앱 대응

수정 화면에서 감정 변경 선택을 제거하고 원래 `state`를 보냅니다.
`EMOTION-015`를 받으면 감정 종류는 변경할 수 없음을 안내하며, 같은 잘못된 요청을 자동 재시도하지 않습니다.
사용자가 편집한 내용을 보존한 채 원래 감정으로 수정할 수 있도록 안내합니다.
구버전 앱도 다른 `state`를 보내면 수정이 거절되므로 클라이언트 대응과 배포 순서를 함께 확인해야 합니다.

과거 스키마는 `state = null`을 허용합니다. 그런 기존 기록은 필수 `state`로 동일 값을 전달할 수 없어
내용 수정도 `EMOTION-015`로 거절됩니다. 운영에 해당 기록이 존재하는지는 아직 확인하지 않았습니다.
배포 전에 존재 여부를 확인하고, 존재하면 별도 처리 정책을 결정해야 합니다. 이 변경은 임의 감정 지정이나 백필을 수행하지 않습니다.

## 적용 범위와 활성화

이 문서는 BE 계약과 클라이언트 연동 안내입니다. 앱 화면 구현·검증과 배포는 별도 작업입니다.
모든 쓰기 서버가 집계를 반영하는 버전으로 전환됐는지 확인하고 완전한 한국 시간 날짜부터 활성화합니다.
과거 기록의 백필·소급 카드 발급은 포함하지 않습니다. 완전한 컬렉션 날짜 마감 경로가 준비되기 전에는 중간 단계만 운영 활성화하지 않습니다.
