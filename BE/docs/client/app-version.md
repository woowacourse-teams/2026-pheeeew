# 앱 버전 조회와 업데이트 정책

서버는 플랫폼별 버전 정책을 제공하고 앱은 설치 버전과 비교해 업데이트를 안내해요.

## 앱 연동

앱 시작 시 기기 등록과 인증 전에 `GET /api/v2/app/version?platform=android`를 호출해요. Authorization 헤더는 보내지 않아요.
API의 플랫폼은 대소문자를 구분하지 않지만 DB에는 대문자로 저장해요.

| 결과 | 확인할 내용 |
|---|---|
| 200 | `minSupportedVersion`, `latestVersion`, `storeUrl`이 등록한 값과 일치 |
| 400 `APP_VERSION-001` | 플랫폼 누락, 빈 값, 앞뒤 공백 또는 지원하지 않는 플랫폼 |
| 404 `APP_VERSION-002` | 해당 플랫폼이 미등록이거나 비활성 |

서버는 조회할 때 DB의 활성 정책을 읽어요. 앱에 이미 표시된 화면은 자동으로 바뀌지 않으며 앱이 다시 조회해야 해요.
버전은 `major.minor.patch`의 각 부분을 숫자로 비교해요. 예를 들어 `1.10.0`은 `1.9.0`보다 높아요.

| 설치 버전 | 앱 동작 |
|---|---|
| `minSupportedVersion` 미만 | 업데이트 안내 후 진입 차단 |
| `minSupportedVersion` 이상, `latestVersion` 미만 | 건너뛸 수 있는 업데이트 안내 |
| `latestVersion` 이상 | 정상 진입 |

조회 실패 시 정상 진입하는 방안은 클라이언트와 합의할 사항이며 이 API가 결정하지 않아요.

## 백엔드 버전 관리

개발자가 `app_version` 테이블을 SQL로 등록하고 수정해요. 별도 관리 API나 서버 재배포는 필요하지 않아요.
테이블은 [마이그레이션](../../src/main/resources/db/migration/V20260915__create_app_version.sql)으로 생성하며 초기 데이터는 없어요.

### 1. 운영 기준과 현재 값 확인

- DB 플랫폼 값은 `ANDROID`, `IOS`이며 플랫폼당 한 행만 저장해요.
- 버전은 `1.2.3`처럼 숫자 세 부분으로 입력해요. 앞자리 0, 음수, 사전 출시·빌드 표기는 허용하지 않아요.
- 최소 지원 버전은 최신 버전 이하여야 해요. DB는 `1.9.0 < 1.10.0`처럼 숫자 순서로 검사해요.
- `latest_version`은 배포된 최신 권장 버전이에요. `min_supported_version`은 구버전 사용을 막기로 결정한 경우에만 올려요.
- 등록 시각은 기본값으로 채워져요. 직접 SQL로 수정할 때는 `updated_at`을 명시적으로 갱신해요.

아래 SQL의 버전과 `https://example.com/app`은 예시예요. 실행 전 실제 배포 버전과 스토어 URL로 바꾸고 대상 DB를 확인해요.
각 변경 전에 아래 조회 결과를 저장해요. 변경자와 변경 사유도 함께 남겨요. 시간은 KST와 UTC로 확인해요.

```sql
SELECT current_database() AS database_name,
       platform, min_supported_version, latest_version, store_url, is_active,
       created_at AT TIME ZONE 'Asia/Seoul' AS created_at_kst,
       created_at AT TIME ZONE 'UTC' AS created_at_utc,
       updated_at AT TIME ZONE 'Asia/Seoul' AS updated_at_kst,
       updated_at AT TIME ZONE 'UTC' AS updated_at_utc
FROM app_version
ORDER BY platform;
```

### 2. 최초 등록과 활성화

처음에는 비활성 상태로 등록해요. iOS는 플랫폼을 `IOS`로 바꾸고 해당 스토어 URL을 입력해요.
이미 등록된 플랫폼이면 중복 오류가 발생해요. 덮어쓰지 말고 현재 값을 조회한 뒤 수정해요.

```sql
INSERT INTO app_version (platform, min_supported_version, latest_version, store_url)
VALUES ('ANDROID', '1.0.0', '1.1.0', 'https://example.com/app')
RETURNING *;
```

반환 행의 `is_active`는 `false`이고 생성·수정 시각이 채워져 있어야 해요.
실제 스토어 URL과 설치 가능한 배포 범위를 확인한 뒤 활성화해요.

```sql
UPDATE app_version
SET is_active = true, updated_at = CURRENT_TIMESTAMP
WHERE platform = 'ANDROID' AND is_active = false
RETURNING *;
```

각 UPDATE는 반환 행이 정확히 한 개인지 확인해요. 0행이면 미등록 또는 현재 값 불일치이므로 먼저 다시 조회해요.
버전 변경 예제의 WHERE 조건에는 변경 전 값을 넣어 같은 항목의 다른 변경을 덮어쓰지 않도록 해요.

### 3. 최신 버전과 최소 지원 버전 변경

최신 버전 변경 예제: `1.1.0`에서 `1.2.0`으로 올리고 최소 지원 버전은 유지해요.

```sql
UPDATE app_version
SET latest_version = '1.2.0', updated_at = CURRENT_TIMESTAMP
WHERE platform = 'ANDROID'
  AND latest_version = '1.1.0' AND min_supported_version = '1.0.0'
RETURNING *;
```

최소 지원 버전 변경 예제: `1.0.0`에서 `1.1.0`으로 올려요.
이 변경을 읽은 앱은 `1.1.0` 미만 설치 버전의 진입을 막아요. 대상 사용자가 업데이트를 설치할 수 있는지 먼저 확인해요.

```sql
UPDATE app_version
SET min_supported_version = '1.1.0', updated_at = CURRENT_TIMESTAMP
WHERE platform = 'ANDROID'
  AND min_supported_version = '1.0.0' AND latest_version = '1.2.0'
RETURNING *;
```

잘못된 형식이나 최소 지원 버전이 최신 버전보다 높은 값은 DB 제약으로 거부돼요.
명시적 트랜잭션 안에서 실행했다면 결과 확인 후 COMMIT하고 오류가 났다면 ROLLBACK해요.

### 4. 잘못된 변경 복구와 비활성화

현재 값을 다시 조회하고 그 이후 다른 작업자의 변경이 없는지 확인해요.
저장해 둔 변경 전 값을 사용해 영향을 준 항목만 되돌려요. WHERE에는 복구 대상인 현재 값을 넣어요.

아래는 앞의 두 버전 변경을 취소하여 최소 `1.0.0`, 최신 `1.1.0`으로 되돌리는 예제예요.
스토어 URL과 활성 여부는 유지해요. 시각은 과거 값이 아니라 복구한 현재 시각으로 기록해요.

```sql
UPDATE app_version
SET min_supported_version = '1.0.0', latest_version = '1.1.0',
    updated_at = CURRENT_TIMESTAMP
WHERE platform = 'ANDROID'
  AND min_supported_version = '1.1.0' AND latest_version = '1.2.0'
RETURNING *;
```

정책 제공을 중단할 때는 삭제 대신 비활성화할 수 있어요. 이후 조회 API는 해당 플랫폼에 404를 반환해요.
비활성화가 앱 진입 허용을 보장하지는 않아요. 클라이언트의 조회 실패 처리에 따라 동작해요.

```sql
UPDATE app_version
SET is_active = false, updated_at = CURRENT_TIMESTAMP
WHERE platform = 'ANDROID' AND is_active = true
RETURNING *;
```

변경 후 앱 연동 절차에 따라 조회 API의 응답에 반영됐는지 확인해요.
