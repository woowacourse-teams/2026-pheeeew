# 앱 원격 설정 조회와 SQL 운영

관련 이슈: #1036, 공동 연동 이슈: #1031

서버는 플랫폼별 일일 프레스 순위 표시 여부와 공지를 제공해요. 앱의 적용·캐시·60초 갱신은 클라이언트가 담당해요.
이 플래그는 서버 프레스 입력·집계·순위 API·대회 ON/OFF·알림 발송을 변경하지 않아요.
기존 `GET /api/v2/app/version`은 그대로 사용해요.

## API 계약

로그인·기기 등록 전에 `GET /api/v2/app/config?platform=android`를 호출해요. Authorization 헤더는 보내지 않아요.
플랫폼은 `android`, `ios`이며 대소문자를 구분하지 않아요. 앞뒤 공백은 허용하지 않아요.

```json
{
  "revision": 1,
  "dailyPressRankingEnabled": true,
  "noticeMessage": "",
  "updatedAt": "2026-10-29T03:00:00Z"
}
```

| 필드 | 계약 |
|---|---|
| revision | 플랫폼별 양의 정수. 최초 1이며 실제 변경·복구마다 1 증가 |
| dailyPressRankingEnabled | 필수 Boolean. 초기 true, 앱의 일일 순위 표시용 |
| noticeMessage | 필수 일반 텍스트. 초기 빈 문자열이며 빈 문자열은 공지 없음 |
| updatedAt | 필수 UTC ISO 8601 변경 시각. 위 예시는 실제 등록·변경 시각으로 대체 |

공지는 최대 200 Unicode 코드 포인트예요. Java의 UTF-16 길이가 아니라 PostgreSQL `char_length`와 같은 기준이에요.
이모지 하나가 여러 코드 포인트로 구성될 수 있어 화면상 글자 수와는 달라요. 공백과 줄바꿈을 그대로 보존하고 자동 trim하지 않아요.
클라이언트는 일반 텍스트로 표시하며 HTML·Markdown 렌더링이나 자동 링크 동작을 전제로 하지 않아요.

| 결과 | 응답 |
|---|---|
| 200 | 위 네 필드의 최상위 JSON |
| 400 APP_CONFIG-001 | 플랫폼 누락·빈 값·공백·미지원 값. `플랫폼은 android 또는 ios여야 합니다.` |
| 404 APP_CONFIG-002 | 유효한 플랫폼의 설정 행이 없음. `앱 설정을 찾을 수 없습니다.` |

오류는 기존처럼 `code`, `message`로 반환해요. 설정이 없을 때 서버가 기본값으로 대신 응답하지 않아요.
설정 응답은 `Cache-Control: no-store`이고 매 요청 DB의 최신 커밋 값을 조회해요. 앱에서 정상 설정을 명시적으로 저장하는 정책은 별도예요.

로컬에서 실행한 서버의 확인 예시예요. 개발 환경 배포 후에는 실제 배포 주소로 바꿔 클라이언트에 전달해요.
이 문서가 존재한다는 것만으로 개발/운영 배포가 완료된 것은 아니에요.

```sh
curl -i 'http://localhost:8080/api/v2/app/config?platform=android'
curl -i 'http://localhost:8080/api/v2/app/config?platform=ios'
```

## 저장 구조와 초기값

신규 `app_config` 테이블을 사용하며 기존 `app_version`에 필드를 추가하지 않아요.
설정별 컬럼을 두고 범용 키-값·JSONB·기능 플래그 시스템은 도입하지 않아요.
새 설정 종류에는 필요한 컬럼·API 계약·앱 동작을 함께 추가해요. 여러 기능의 ON/OFF 추가가 반복되면 범용 기능 플래그 구조를 검토해요.

[마이그레이션](../../src/main/resources/db/migration/V20261029_1__create_app_config.sql)이 테이블과 Android·iOS 각 한 행을 만들어요.
초기값은 각각 `revision=1`, `daily_press_ranking_enabled=true`, `notice_message=''`예요. 등록·변경 시각은 실제 DB 시각이에요.
플랫폼은 DB에 `ANDROID`, `IOS`로 저장하고 유일성과 필수값·양의 revision·공지 길이는 DB 제약으로 보호해요.
별도 활성 상태나 삭제 운영은 제공하지 않아요. 운영 전 두 플랫폼의 초기값과 조회 결과를 확인해요.

## SQL 운영

플랫폼 한 개씩 변경해요. 개발/운영 DB 쓰기와 배포는 대상 환경에 대한 별도 승인 후 실행해요.
다음 SQL은 절차 예시이며 이 문서를 작성하면서 실제 서버 DB를 변경하지 않았어요.

### 1. 대상과 변경 전 값 확인

```sql
SELECT current_database() AS database_name, current_user AS operator,
       platform, daily_press_ranking_enabled, notice_message, revision,
       created_at AT TIME ZONE 'UTC' AS created_at_utc,
       updated_at AT TIME ZONE 'UTC' AS updated_at_utc
FROM app_config
ORDER BY platform;
```

대상 DB·플랫폼과 현재 revision을 확인하고 변경 전 결과를 보관해요. 변경자·사유·작업 시각을 작업 기록에 남겨요.
복구할 때도 해당 시점의 현재 값을 다시 확인해요. 행을 삭제하거나 revision·시각을 과거 값으로 돌리지 않아요.

### 2. 조건부 변경

아래는 Android의 revision 1을 기준으로 일일 순위를 끄고 공지를 설정하는 예시예요.
플랫폼·기존 revision·새 설정값을 실제 값으로 바꿔요. 공지에 작은따옴표가 있으면 SQL 문자열 규칙에 따라 이스케이프하고, 도구에서는 바인딩을 사용해요.

```sql
BEGIN;

UPDATE app_config
SET daily_press_ranking_enabled = false,
    notice_message = '일일 순위를 잠시 점검하고 있어요.',
    revision = revision + 1,
    updated_at = statement_timestamp()
WHERE platform = 'ANDROID'
  AND revision = 1
  AND (daily_press_ranking_enabled, notice_message)
      IS DISTINCT FROM (false, '일일 순위를 잠시 점검하고 있어요.')
RETURNING platform, daily_press_ranking_enabled, notice_message, revision,
          updated_at AT TIME ZONE 'UTC' AS updated_at_utc;
```

정확히 1행이면 값과 revision을 확인한 뒤 아래 COMMIT을 실행해요. 반환 행은 커밋 전 상태이며 아직 다른 연결에서 조회되지 않아요.

```sql
COMMIT;
```

0행·예상 밖 결과·SQL 오류이면 아래 ROLLBACK을 실행해요. 트랜잭션을 열린 채로 두지 않아요.

```sql
ROLLBACK;
```

0행은 대상 없음·기존 revision 불일치·동일값 중 하나예요. 다시 조회하고 원인을 확인해요.
revision만 최신 값으로 바꿔 자동 재시도하면 다른 작업자의 의도를 덮어쓸 수 있으므로 하지 않아요.
설정값이 같으면 revision과 updatedAt 모두 유지해요. 공지만 바꿀 때도 현재 순위 값을 명시해 같은 절차를 사용해요.
동시에 같은 revision으로 변경하면 첫 성공 이후 나머지는 0행이 되어 기존 변경을 덮어쓰지 못해요.

### 3. 이전 값 복구

위 변경이 커밋됐고 현재 revision이 2임을 다시 확인한 경우의 예시예요.
저장해 둔 변경 전 값으로 복구하되 현재 revision을 조건으로 새 번호 3과 현재 변경 시각을 발급해요.

```sql
BEGIN;

UPDATE app_config
SET daily_press_ranking_enabled = true,
    notice_message = '',
    revision = revision + 1,
    updated_at = statement_timestamp()
WHERE platform = 'ANDROID'
  AND revision = 2
  AND (daily_press_ranking_enabled, notice_message) IS DISTINCT FROM (true, '')
RETURNING platform, daily_press_ranking_enabled, notice_message, revision,
          updated_at AT TIME ZONE 'UTC' AS updated_at_utc;
```

앞의 변경과 동일하게 1행·값 확인 후 COMMIT하고, 0행·오류이면 ROLLBACK 후 재조회해요.
과거 설정을 다시 저장해도 revision과 updatedAt은 과거 값으로 복구하지 않아요.

## 테스트 환경 연동과 시간 측정

1. 승인된 테스트 환경에 배포한 뒤 API 기본 주소·빌드/커밋·양쪽 플랫폼의 초기 설정을 공유해요.
2. 같은 앱 설치 상태에서 위 SQL로 ON → OFF → ON, 공지 변경·빈 문자열 복구를 수행해요. 각 변경 후 커밋하고 API를 다시 조회해요.
3. 플랫폼·변경 전후 값·revision·updatedAt·COMMIT 완료 시각·API 확인 시각을 기록해요. 다른 플랫폼이 유지되는지도 확인해요.
4. 클라이언트는 같은 revision의 수신 성공 시각·앱 적용 시각과 실제 화면 증거를 기록해요.
5. 서버·기기 시계 차이를 확인한 뒤 COMMIT 완료부터 수신·적용까지 지연을 계산해요. 온라인·전경·요청 성공 조건과 오프라인·백그라운드 한계를 구분해요.

`updatedAt`은 UPDATE 실행 시각이며 COMMIT 완료 시각이 아니에요. 트랜잭션을 오래 열면 두 시각의 차이가 커져요.
앱의 60초 갱신은 클라이언트 책임이고 서버가 앱 적용 시간을 보장하지 않아요.
API/DB 자동 테스트 통과와 실제 서버 변경·앱 적용 실측은 서로 다른 증거예요. 미실행 항목을 완료로 표시하지 않아요.
