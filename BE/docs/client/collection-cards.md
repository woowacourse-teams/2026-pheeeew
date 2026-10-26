# 컬렉션 진행도·직접 수령·목록·상세 조회와 감정 수정 계약

관련 이슈: [#839](https://github.com/woowacourse-teams/2026-pheeeew/issues/839), [#840](https://github.com/woowacourse-teams/2026-pheeeew/issues/840), [#842](https://github.com/woowacourse-teams/2026-pheeeew/issues/842), [#843](https://github.com/woowacourse-teams/2026-pheeeew/issues/843)

## 수령한 카드 목록 조회

`GET /api/v1/collection-cards`에 기기 access token을 `Authorization: Bearer ...`로 전달합니다.
목록과 상세 모두 인증된 기기 소유만 조회하며 응답에 `Cache-Control: no-store`를 적용합니다.
쿼리로 조회 기기를 선택할 수 없습니다. 목록은 카드마다 상세 API를 추가 호출하지 않고 집계·이미지 정보를 함께 제공합니다.

| 쿼리 | 규칙 |
| --- | --- |
| `emotion` | 선택. `FRUSTRATED`, `IRRITATED`, `EXHAUSTED`, `DISCOURAGED`, `ANGRY`. 첫 페이지에서 생략하면 전체 감정 |
| `limit` | 선택. 생략하면 20, 1~50 허용. 페이지마다 지정할 수 있으며 커서에 저장하지 않음 |
| `cursor` | 첫 페이지와 새로고침에서 생략. 다음 페이지는 받은 `nextCursor`를 URL 인코딩해 그대로 전달. 최대 512자 |

`emotion=` 또는 `limit=`처럼 빈 값을 명시하면 400입니다. `cursor=`도 잘못된 커서로 400입니다.

```http
GET /api/v1/collection-cards?emotion=ANGRY&limit=20
GET /api/v1/collection-cards?limit=20&cursor=<nextCursor>
```

두 번째 요청에서 감정을 생략하면 커서의 `ANGRY` 필터를 유지합니다. 같은 감정을 다시 지정해도 됩니다.
다른 감정과 기존 커서를 섞으면 400입니다. 필터를 바꾸려면 커서를 버리고 첫 페이지부터 조회합니다.

```json
{
  "nickname": "스타크",
  "items": [
    {
      "cardId": "20769cf2-1f8a-4b62-8dbe-5b594d5ffb5d",
      "date": "2026-10-21",
      "state": "ANGRY",
      "stampCount": 3,
      "pressCount": 125,
      "images": {
        "characterUrl": "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/characters/angry/03.png",
        "roundStampUrl": "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-round/angry/06.png",
        "straightStampUrl": "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-straight/angry/06.png"
      }
    }
  ],
  "counts": {
    "total": 1,
    "byEmotion": {"FRUSTRATED": 0, "IRRITATED": 0, "EXHAUSTED": 0, "DISCOURAGED": 0, "ANGRY": 1}
  },
  "hasNext": false,
  "nextCursor": null
}
```

각 항목은 상세 응답과 같은 필드를 사용합니다. 항목을 선택하면 `cardId`로 상세 API에 연결합니다.
빈 목록도 200이며 `items=[]`, `hasNext=false`, `nextCursor=null`입니다. `nickname`과 `counts`는 생략하지 않습니다.
감정 필터나 커서 위치 때문에 항목이 없어도 보유 장수는 0이 아닐 수 있습니다.
`hasNext=true`일 때만 `nextCursor`로 이어서 조회하고, 마지막 페이지는 `nextCursor=null`입니다.

### 현재 닉네임과 보유 장수

- `nickname`은 인증된 기기를 조회할 때 읽은 현재 닉네임입니다. 카드 발급 당시 이름을 저장하지 않으며
  목록의 모든 카드 표시·저장·공유에 공통으로 사용합니다. 닉네임 변경 후 목록을 새로고침하면 새 이름을 받습니다.
- 미설정 닉네임은 `null`입니다. 기존 기기 닉네임 계약에 따라 설정 화면으로 연결합니다.
  컬렉션 화면 표시를 위해 닉네임 API를 추가 호출할 필요는 없습니다. 상세·수령 응답에는 닉네임이 추가되지 않습니다.
- `counts.total`은 목록과 같은 날짜 범위의 본인 수령 카드 수이며 `counts.byEmotion`의 다섯 값의 합입니다.
  없는 감정도 0으로 제공합니다. 같은 조합이라도 다른 날짜에 수령했다면 각각 1장입니다.
- 개수에는 목록의 감정 필터와 마지막 커서 위치를 적용하지 않습니다. 전체 화면에서는 `total`,
  감정 필터 화면에서는 해당 `byEmotion` 값을 표시합니다. 카드가 하나도 없으면 모두 0입니다.
- 목록과 개수는 하나의 SQL에서 같은 조회 시점으로 계산합니다. 닉네임은 앞선 기기 조회의 값이며
  이 세 값 모두의 원자적 스냅샷을 보장하는 것은 아닙니다.
  페이지 사이 늦게 카드가 커밋되면 개수는 바뀔 수 있습니다. 총 장수만큼 카드를 이미 내려받았다는 뜻은 아닙니다.
- 커버플로·앨범은 같은 목록 응답을 사용합니다. 끝에 가까워지면 다음 페이지를 가져와 붙이며
  같은 화면에서 표시 방식을 바꿀 때 별도 API는 필요하지 않습니다.

### 날짜 범위와 늦은 저장

첫 요청의 한국 날짜(`Asia/Seoul`) 당일까지 수령한 카드를 카드 날짜·내부 ID 내림차순으로 조회합니다.
오늘 수령 카드도 포함하며 미래 날짜와 조건만 달성한 미수령 카드는 제외합니다.
예를 들어 10월 23일 첫 조회는 10월 23일까지 포함하며, 페이지 이동 중 10월 24일이 되어도 같은 범위를 유지합니다.
커서를 버리고 새로고침하면 10월 24일 수령 카드도 포함됩니다. `/today`는 오늘 진행도·수령 여부 확인에 사용합니다.
수령 직후·자정 전환·필터 변경에는 커서를 폐기하고 목록과 개수를 새로고침합니다.
기존 오늘 제외 방식으로 발급된 커서는 저장된 날짜 상한을 유지합니다. 오늘 카드까지 보려면 커서 없이 다시 요청합니다.

커서는 날짜 범위·감정 필터·마지막 정렬 위치를 유지하며 카드 집합이나 활동 횟수의 스냅샷을 고정하지 않습니다.

| 페이지 사이 늦게 커밋된 카드 위치 | 처리 |
| --- | --- |
| 이미 지나간 위치 | 이어보기에서 되돌아가지 않음. 새로고침하면 확인 가능 |
| 아직 읽지 않은 위치 | 다음 페이지에 포함될 수 있음 |

조회는 다른 트랜잭션의 카드 저장·집계 변경이 끝날 때까지 기다리지 않고 커밋된 데이터를 읽습니다.
이후 조회는 새로 커밋된 집계를 반영하므로 목록과 나중에 연 상세의 숫자가 다를 수 있습니다.
카드 ID·날짜·감정·그림 조합은 그대로입니다. 자정 정산·서버 이미지 생성·추가 쓰기 잠금은 없습니다.

### 목록 오류 처리

| HTTP | 코드 | 처리 |
| --- | --- | --- |
| 400 | `COMMON-001` | 잘못된 감정·페이지 크기·요청 형식 또는 커서 길이 초과 |
| 400 | `COLLECTION-007` | 잘못된 커서 또는 요청 감정과 커서 필터 불일치. 커서 없이 다시 조회 |
| 400 | `COLLECTION-008` | 서비스의 페이지 크기 방어 검증. HTTP 요청 범위 검증은 `COMMON-001` 사용 |
| 401 | 기존 인증·기기 오류 코드 | 유효한 기기 인증 없음 |
| 500 | `COLLECTION-006` | 반환 대상 카드의 스탬프·프레스 집계 중 하나 이상 누락 |
| 503 | `COLLECTION-004` | 카드 자산 또는 이미지 주소 설정 미비 |

반환할 카드 중 집계가 누락되면 페이지 전체를 오류로 반환합니다. 해당 카드를 빼거나 횟수를 0으로 대체하지 않습니다.
다음 페이지 존재 여부만 확인하는 추가 한 건은 이번 응답의 집계 검증 대상이 아닙니다.
500은 기존 서버 ERROR 로그에 오류 코드와 correlationId가 남습니다. 자동 복구를 뜻하지 않으며 앱은 무한 재시도하지 않습니다.
이미지 다운로드 실패는 API 오류와 별개로 처리합니다. 공개 PNG URL에는 서명 만료가 없지만 객체 보존·읽기 권한은 필요합니다.

### 앱에서 남은 연동 확인

- 오늘 진행도와 오늘을 포함한 보유 카드 목록을 연결하고 수령 직후·자정·새로고침 시 기존 커서를 폐기합니다.
- 감정 필터 변경, 빈 목록, 이어보기 종료, 목록에서 상세로 이동을 연결합니다.
- 최상위 닉네임과 전체·감정별 장수를 표시하고 닉네임 미설정·변경을 처리합니다.
- 이미지 URL 3개를 조합하고 최신 횟수를 표시합니다. 이미지 로딩 실패와 API 오류를 구분합니다.
- 실제 기기에서 자정 전환·새로고침·숫자 갱신·고정 조합·배치와 잘림을 확인합니다.
- 오늘 화면의 수령 버튼은 `claimable`, 수령 완료 표시는 `claimed` 로 판단합니다. 감정별 `card`, `remaining` 으로 수령 가능 여부를 계산하지 않습니다.
- 수령 요청이 `409 COLLECTION-009` 이면 오늘 진행도를 다시 조회해 이미 받은 카드를 표시합니다.

## 카드 상세 조회

`GET /api/v1/collection-cards/{cardId}`에 기기 access token을 전달합니다.
오늘과 과거 날짜의 본인 수령 카드를 조회하며 응답은 `Cache-Control: no-store`입니다.
카드 ID는 수령·오늘 조회 또는 카드 목록 응답에서 얻습니다.

```json
{
  "cardId": "20769cf2-1f8a-4b62-8dbe-5b594d5ffb5d",
  "date": "2026-10-21",
  "state": "ANGRY",
  "stampCount": 3,
  "pressCount": 125,
  "images": {
    "characterUrl": "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/characters/angry/03.png",
    "roundStampUrl": "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-round/angry/06.png",
    "straightStampUrl": "https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/v1/stamps-straight/angry/06.png"
  }
}
```

- 날짜·감정·캐릭터·도장 조합은 수령 당시 값으로 고정합니다. `state`는 카드 감정입니다.
- 횟수는 해당 기기·날짜·감정의 `device_daily_stamps`·`device_daily_presses`에서 읽습니다.
  두 횟수와 카드·자산 경로는 한 SQL의 조회 시점을 공유하며 원본 활동 전체를 다시 세지 않습니다.
- 늦게 저장된 전날 활동은 다음 조회에 반영됩니다. 자정 정산, 최종 횟수 고정, 활동 저장 대기나 정산용 잠금은 없습니다.
  자정 전에 허용됐지만 늦게 커밋한 카드도 저장 완료 후 조회할 수 있습니다.
- `cycle`, `combinationIndex`, `cardVersion`, `claimedAt`은 응답에 포함하지 않습니다.
  정산 상태·이미지 작업 상태·서버 완성 이미지 URL도 제공하지 않습니다.
- 앱은 세 PNG와 횟수를 고정 배치·폰트로 조합합니다. 저장·공유한 파일은 생성 당시 모습으로 남으며 이후 조회 숫자와 다를 수 있습니다.

| HTTP | 코드 | 의미 |
| --- | --- | --- |
| 400 | 기존 공통 오류 코드 | 잘못된 UUID |
| 401 | 기존 인증·기기 오류 코드 | 미인증·유효하지 않은 토큰 또는 존재하지 않는 인증 기기 |
| 404 | `COLLECTION-005` | 없는 카드 또는 다른 기기의 카드. 두 경우 모두 `카드를 찾을 수 없습니다.` |
| 500 | `COLLECTION-006` | 발급된 카드의 스탬프 또는 프레스 집계 누락 |
| 503 | `COLLECTION-004` | 카드 자산이나 이미지 주소 설정 미비 |

### 집계·자산 보존과 누락 확인

보유 카드의 일별 집계와 고정 자산은 과거 조회의 원본이므로 삭제하거나 보존 기간을 단축하지 않습니다.
스탬프 원본을 삭제해도 누적 횟수나 카드 소유는 회수하지 않습니다.
발급된 카드의 스탬프 또는 프레스 집계 행이 없으면 목록·상세·오늘 조회는 `500 / COLLECTION-006`을 반환합니다.
응답 메시지는 `카드의 활동 횟수를 조회할 수 없습니다.`이며 누락된 횟수를 0으로 표시하지 않습니다.
미발급 감정의 오늘 진행도는 기존처럼 활동이 없으면 0회를 반환합니다.
공통 서버 오류 로그에 `http_request_failed`, 오류 코드, 요청 경로와 correlationId가 남습니다.
앱은 숫자를 0으로 대체하거나 무한 자동 재시도하지 않고 조회 실패를 안내합니다.
오류 반환은 복구가 아닙니다. 운영자는 오류 코드와 요청 추적 정보를 통해 원인을 조사하고
해당 카드의 날짜·감정 집계와 데이터 변경 이력을 확인해야 합니다. 카드 소유와 조합은 유지합니다.
조회가 원본을 재집계하거나 카드를 자동 회수하지는 않습니다.
공개 자산 URL에는 서명 만료가 없지만 S3 객체 보존과 읽기 권한이 필요합니다.
신규 발급 버전이 비활성화돼도 기존 카드는 저장된 버전으로 조회할 수 있습니다.

## 오늘 진행도 조회

발급된 오늘 카드의 집계가 누락되면 상세 조회와 같은 `500 / COLLECTION-006`을 반환합니다.

`GET /api/v1/collection-cards/today`에 기기 access token을 `Authorization: Bearer ...`로 전달합니다.
날짜와 기기는 서버가 결정합니다. 계정이 아닌 인증된 기기 기준이며 재설치 복구는 보장하지 않습니다.
응답에는 `Cache-Control: no-store`가 설정됩니다.

```json
{
  "date": "2026-10-22",
  "stamps": {"count":0,"target":1,"remaining":1},
  "presses": {"count":70,"target":100,"remaining":30},
  "claimed": false,
  "claimable": false,
  "items": [
    {"state":"FRUSTRATED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":70,"target":100,"remaining":30},"card":null},
    {"state":"IRRITATED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null},
    {"state":"EXHAUSTED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null},
    {"state":"DISCOURAGED","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null},
    {"state":"ANGRY","stamps":{"count":0,"target":1,"remaining":1},"presses":{"count":0,"target":100,"remaining":100},"card":null}
  ]
}
```

- `date`는 한국 시간(`Asia/Seoul`) 기준 오늘 날짜입니다. 다섯 감정은 위 순서로 항상 포함하며 미발급 감정에 활동이 없으면 `count`는 0입니다.
- `stamps.count`는 해당 날짜·기기·감정으로 성공한 신규 등록 횟수입니다. 메모·녹음 유무나 그룹 선택과 관계없이 집계합니다.
- 등록과 스탬프 집계는 함께 커밋·롤백합니다. 같은 `requestId`의 재시도는 다시 집계하지 않으며, 삭제 후 재시도나 다음 날 재시도도 마찬가지입니다.
- 내용 수정·삭제는 이미 인정한 스탬프 횟수를 바꾸지 않습니다. 프레스는 기존 API에서 서버가 인정한 저장 횟수를 사용합니다.
- 수령 조건은 최상위 `stamps`, `presses` 로 판정합니다. 오늘 다섯 감정을 합한 스탬프 1회, 프레스 100회가 목표입니다. `items` 의 감정별 `target`, `remaining` 은 참고용이며 수령 조건이 아닙니다.
- `count`는 목표를 넘겨도 유지하고 `remaining`은 `max(target - count, 0)`입니다. 다른 날짜로 이월하지 않습니다.
- `claimed` 는 오늘 이 기기가 어떤 감정이든 카드를 받았으면 true 입니다. 카드는 기기마다 하루에 한 장입니다.
- `claimable` 은 `claimed` 가 false 이고 최상위 두 `remaining` 이 0 일 때만 true 입니다. true 이면 다섯 감정 중 아무 `state` 로 수령할 수 있습니다.

`card`는 발급된 카드 정보이며 미발급 상태이면 명시적인 `null`입니다. 위 예시는 미발급 응답입니다.
수령 후에는 아래 직접 수령 응답과 같은 카드 객체를 `items[].card`로 반환합니다.
진행도·카드·이미지 경로는 한 SQL의 같은 스냅샷으로 조회하며 발급 잠금을 획득하지 않습니다.
수령 후에도 횟수는 최신 값으로 증가하지만 카드 식별자와 캐릭터·도장 이미지는 유지합니다.
오늘 받은 카드는 다섯 항목 중 한 항목의 `card` 에만 있습니다. 수령 가능 여부는 `claimable`, 수령 완료는 `claimed` 로 판단합니다.
진행도 조회는 활동이나 카드를 생성하지 않습니다. 클라이언트가 목표 달성만으로 발급된 카드 정보를 만들어서는 안 됩니다.

등록·프레스 저장 성공 후 진행도를 다시 조회해 서버가 확인한 횟수로 갱신합니다.
자정을 넘긴 응답의 `date`가 바뀌면 전날 진행도를 새 날짜 값으로 교체합니다.

| 상태 | 의미 | 클라이언트 처리 |
| --- | --- | --- |
| 200 | 오늘 진행도 조회 성공 | 날짜, 합산 진행도, 수령 상태(`claimed`, `claimable`)와 다섯 감정의 횟수 표시 |
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
- 같은 기기, 같은 KST 날짜에서 다섯 감정을 합한 커밋된 스탬프 1회 이상 AND 프레스 100회 이상이어야 합니다. 서버가 한 SQL 스냅샷으로 재확인합니다.
- `state` 는 받을 카드의 감정이며 다섯 감정 중 아무것이나 고를 수 있습니다. 그 감정의 활동 여부는 보지 않습니다.
- 기기마다 KST 하루에 신규 카드 한 장만 발급합니다. 오늘 이미 카드를 받았으면 다른 `state` 요청은 `409 COLLECTION-009` 로 거절하고 추첨 조합을 소비하지 않습니다.
- 조건을 달성해도 자동 발급하지 않습니다. 자정까지 버튼을 누르지 않은 카드는 만료되며 소급 발급·조합 소비하지 않습니다.
- 같은 기기의 수령 요청은 감정과 관계없이 하나씩 처리합니다. 잠금 획득 후와 추첨 직전에 KST 날짜를 검사합니다. 마지막 검사를 자정 전에 통과했다면 커밋, 응답이 자정 뒤여도 유효합니다.
- 중복 클릭·응답 유실 시 **같은 date·state**로 재요청합니다. 이미 받은 카드는 다음 날에도 같은 결과를 반환합니다.
- 수령 실패는 감정·프레스 활동을 취소하지 않습니다. 카드와 추첨 상태만 함께 커밋·롤백합니다.

| 상태 | 오류 코드 | 처리 |
| --- | --- | --- |
| 400 | `COMMON-001` | 날짜·감정의 누락 또는 잘못된 형식 수정 |
| 400 | `COLLECTION-002` | 당일이 아닌 미수령 요청. 오늘 진행도를 다시 조회 |
| 401 | 기존 인증·기기 오류 코드 | 기기 인증 복구 |
| 409 | `COLLECTION-003` | 오늘 합산 조건 미달. 활동 저장 완료 후 진행도 재조회 |
| 409 | `COLLECTION-009` | 오늘 이미 다른 감정 카드를 받음. 오늘 진행도를 다시 조회해 받은 카드를 표시 |
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
서버는 녹음에서 사용하는 기존 `S3Properties`의 버킷·리전·접두사를 재사용합니다.
AWS SDK의 URL 생성 기능으로 `S3_KEY_PREFIX + collection-cards/ + DB 상대 경로`의 공개 이미지 URL을 만듭니다.
URL 생성 시 S3 네트워크 요청이나 서명·임시 접근 권한 발급은 하지 않습니다.
녹음의 업로드·재생은 기존 만료형 서명 URL 방식을 그대로 유지합니다.

개발의 기존 S3 설정이 다음과 같다면:

```text
S3_BUCKET=techcourse-project-2026
S3_REGION=ap-northeast-2
S3_KEY_PREFIX=pheeeew/development/
```

이미지 기본 주소는 `https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/pheeeew/development/collection-cards/`입니다.
운영은 같은 버킷·리전과 `S3_KEY_PREFIX=pheeeew/production/`을 사용하면 운영 경로가 됩니다.
기존 Secret의 실제 값이 다른 경우 그 값으로 URL을 생성하므로 업로드 경로와 일치하는지 확인해야 합니다.
카드에 저장된 버전의 자산을 사용하므로 활성 발급 버전이 바뀌어도 기존 카드를 새 그림으로 대체하지 않습니다.

신규 수령은 `pheeeew.collection.card-version`의 자산 80행·80행이 완전해야 허용합니다.
활성 발급 버전이 비어 있으면 신규 수령은 `COLLECTION-004`로 차단합니다.
기존 카드 응답은 활성 발급 버전과 무관하게 저장된 버전의 자산과 공통 S3 설정으로 반환합니다.
필요한 자산이 없으면 `COLLECTION-004`를 반환하며 카드 재추첨이나 다른 버전 대체를 하지 않습니다.

| 환경 변수 | 개발 | 운영 |
| --- | --- | --- |
| `S3_BUCKET`, `S3_REGION`, `S3_KEY_PREFIX` | 기존 녹음용 필수 Secret 재사용 | 기존 녹음용 필수 Secret 재사용 |
| `COLLECTION_CARD_VERSION` | 기본값 없음 — 필수 등록 | 미등록 시 빈 값, 신규 수령 비활성 |

GitHub 저장소 Settings → Environments → `development` / `production` → Environment secrets에서 관리합니다.
추가 등록할 Secret은 `COLLECTION_CARD_VERSION` 하나이며, 개발 값은 `v1`입니다.
별도의 이미지 기본 URL Secret은 사용하지 않습니다.
배포 workflow가 Secret을 서버 `/opt/pheeeew/.env`에 기록하고 Compose가 앱 컨테이너에 전달합니다.
개발 배포 workflow와 Compose는 발급 버전의 누락·빈 값을 거부합니다.
운영 발급 버전은 실제 이미지 업로드·검증과 날짜 마감 경로 준비 후 `v1`로 설정합니다.
서버 .env만 수정하면 다음 배포에서 덮어써지므로 지속 설정은 GitHub Environment secrets에서 관리합니다.
등록·변경한 Secret은 다음 배포로 앱 컨테이너를 다시 생성할 때 반영됩니다.
공통 S3 설정은 기존 녹음과 동일하게 필수이며 잘못된 설정은 애플리케이션 시작 시 거부합니다.

자산 테이블에 초기 데이터가 들어가는 것만으로 운영 수령을 활성화하지 않습니다.
이번 개발 업로드에서는 240개 이미지 공개 URL의 로딩과 원본 해상도를 검증했습니다.
DB 등록은 실제 PNG 파일의 보존을 대신하지 않으므로 발급에 사용한 S3 객체는 덮어쓰거나 삭제하지 않습니다.

### 테이블 생성과 초기 데이터

`src/main/resources/db/migration/V20261023_1__create_collection_cards_and_draw_states.sql`에
자산 테이블 생성, v1 캐릭터 80행·도장 쌍 80행 INSERT, 카드·추첨 상태 테이블 생성이 포함됩니다.
개발·운영 모두 동일한 상대 경로 데이터를 사용하고 기본 주소만 환경별로 다르게 설정합니다.
스크립트는 이미지 파일을 업로드하지 않습니다. 수동 SQL 실행 후 Flyway가 중복 실행되게 하지 않습니다.

이 생성 스크립트는 영속 DB 적용 전에 정산 상태 컬럼을 제거한 버전입니다.
이전 파일이 적용된 DB가 발견되면 그대로 적용하지 말고 적용 이력을 확인한 뒤 별도 증분 migration을 사용해야 합니다.
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
과거 기록의 백필·소급 카드 발급은 포함하지 않습니다. 날짜 마감이나 서버 이미지 생성은 활성화 조건이 아닙니다.
앱 상세 화면 연동과 환경별 자산 접근을 확인한 뒤 활성화하며 운영 배포는 별도 승인으로 진행합니다.

## 정산 상태 제거

카드에는 정산 상태를 저장하지 않습니다. 조합은 고정하고 횟수는 조회 시 일별 집계에서 읽습니다.
`V20261023_1__create_collection_cards_and_draw_states.sql`이 개발·운영 및 팀의 영속 DB에
적용되지 않았음을 확인한 상태에서 생성 스크립트의 정산 상태 컬럼과 제약을 제거했습니다.
별도의 기본값 설정 마이그레이션은 사용하지 않습니다.
앞으로 이 생성 스크립트가 영속 DB에 적용된 뒤에는 기존 파일을 수정하지 않고 새 마이그레이션을 추가합니다.
