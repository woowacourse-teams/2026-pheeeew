# SGIS 경계 준비와 검증

자동 배포나 Flyway에서 실행하지 않는 수동 운영 도구예요. 개발·운영 DB 적재는 대상별 별도 승인을 받아요.
다운로드 ZIP과 생성 SQL은 Git 밖에 보관해요. 기대 개수는 원본 DBF에서 확인하며 적재 결과에 맞춰 바꾸지 않아요.

## 준비 환경

`shp2pgsql`과 Python 3의 `pyproj`가 필요해요. 통합 테스트의 합성 SHP 생성에는 `pyshp`도 필요해요.
애플리케이션 서버에 설치할 필요는 없어요. SQL 실행 환경에는 `psql`, 대상 DB에는 PostGIS가 필요해요.
2025년 2분기의 시도·시군구·읍면동 `.shp/.dbf/.shx/.prj/.cpg`를 같은 원본 폴더에 풀어요.

```bash
bash scripts/regions/prepare-import.sh /path/to/sgis-source /path/to/new-import.sql
```

준비는 DB에 접속하지 않아요. 기존 출력 파일은 덮어쓰지 않아요.

## 승인한 DB에서 실행

먼저 Flyway로 행정구역 테이블을 생성해요. DB 접속 정보는 실행 환경의 `PGHOST/PGPORT/PGDATABASE/PGUSER`와
보호된 인증 설정을 사용해요. 암호를 SQL 파일이나 저장소에 넣지 않아요.

```bash
psql -X --single-transaction -v ON_ERROR_STOP=1 -f /path/to/new-import.sql
psql -X --single-transaction -v ON_ERROR_STOP=1 \
  -v sido_count=17 -v sigungu_count=252 -v emd_count=3559 -f scripts/regions/verify.sql
```

개수 예시는 2025년 2분기 원본 3종의 DBF 헤더에서 확인한 값이에요. 검증 실패는 검증 완료로 표시하지 않아요.
검증은 같은 레벨의 면적 겹침을 거부하고 경계선 접촉은 허용해요.
예외는 아래에 기록한 `SGIS_2025_2Q` 읍면동 14쌍뿐이며, 쌍별 교집합 면적이 1㎡ 이하일 때만 허용해요.
면적은 4326 교집합을 5179로 변환해 계산해요. 완전 포함·동일 도형은 목록과 면적에 관계없이 거부해요.
원본 도형은 보정하지 않아요. 분류 SQL은 겹침 안의 좌표도 코드 오름차순으로 하나의 행정동에만 연결해요.
신규 감정은 최종 저장 좌표를 분류해요. 기존 감정의 분류는 아래 수동 백필 배치로 처리해요.
타입·좌표계·도형 유효성·표시점 포함·코드와 부모 관계는 적재 시 기존 스키마 제약으로 검사해요.
이 검사만으로 원본 동일성, 부모·자식 경계의 공간적 일치나 영역 누락까지 증명하지는 않아요.
전체 실제 자료의 적재·검증 결과는 별도 검증 단계에서 확인해요.

## 실제 원본으로 재검증

원본을 푼 경로를 지정하면 PostgreSQL 17 / PostGIS 3.5 Testcontainers에서 전국 자료를 검증해요.
위 준비 도구와 Docker가 필요해요. 원본 경로를 지정하지 않으면 이 테스트는 실행하지 않아요.

```bash
SGIS_SOURCE_DIRECTORY=/path/to/sgis-source ./gradlew test --rerun-tasks \
  --tests com.pheeeew.region.SgisDatasetValidationIntegrationTest
```

검증 대상은 실제 원본의 적재, 레벨별 개수, 스키마 제약, 같은 레벨의 면적 겹침과 행정동 표시점 5개의 분류예요.
표시점 분류는 저장 경계와 분류 코드의 연결 검사이며 실제 주소와 경계의 정확성까지 증명하지는 않아요.
외부 원본은 Gradle 입력으로 추적하지 않으므로 재실행을 강제하고 테스트 결과의 실행 1건·건너뜀 0건을 확인해요.

## ZIP 3종에서 전달할 SQL 준비

준비·격리 검증과 실제 서버 적재는 별개예요. 아래 준비를 완료해도 개발·운영 실행 승인을 뜻하지 않아요.
시도·시군구·읍면동 ZIP을 각각 DB에 넣는 대신 세 원본으로 만든 SQL 하나를 전달해요.
ZIP과 SQL은 Git 밖에 보관하고, SQL을 만든 뒤 수정하면 다시 검증해요.

### 원본을 풀고 SQL 생성

먼저 ZIP 3개의 SHA-256을 아래 원본 기록과 대조해요. 같지 않으면 승인된 겹침 예외를 그대로 사용하지 않아요.
새 작업 폴더의 `source`에 세 ZIP을 풀어요. 아래 경로는 예시이며 기존 폴더나 SQL은 덮어쓰지 않아요.

```bash
REGION_PACKAGE_DIR=/path/to/new-region-package
mkdir "$REGION_PACKAGE_DIR"
mkdir "$REGION_PACKAGE_DIR/source"
for level in sido sigungu dong; do
  unzip "/path/to/downloads/bnd_${level}_00_2025_2Q.zip" -d "$REGION_PACKAGE_DIR/source"
done
bash scripts/regions/prepare-import.sh "$REGION_PACKAGE_DIR/source" \
  "$REGION_PACKAGE_DIR/sgis-2025-2Q-import.sql"
```

원본 DBF의 레코드 수는 시도 17·시군구 252·읍면동 3,559개예요. SQL 안에서 5179 경계를 4326으로 변환해요.
생성 SQL에는 원본 staging 생성·적재, `regions` 입력과 staging 삭제가 모두 포함돼요.
Flyway가 테이블·데이터셋 행을 만든 뒤에 실행하며, 기존 경계가 있으면 재적재를 거부해요.

### 승인한 서버로 전달

검증한 SQL과 같은 작업 커밋의 `verify.sql`을 EC2 작업 폴더에 전달해요. 원본 ZIP은 DB 서버에 필요하지 않아요.
전송 전후 SHA-256을 대조해요. macOS는 `shasum -a 256`, Linux는 `sha256sum`을 사용할 수 있어요.
SQL은 크므로 압축해서 전달할 수 있어요. 압축을 풀고 SQL의 체크섬을 대조한 뒤 아래 실행 명령을 사용해요.
`shp2pgsql`·`pyproj`는 서버에 설치하지 않아요. 실행 환경에는 `psql`만 필요해요.

개발 DB 컨테이너에서는 EC2 호스트의 SQL을 표준 입력으로 전달해요. 이 방식은 컨테이너 안에 파일을 복사하지 않아요.
아래 명령은 전달한 두 파일이 있는 EC2 작업 폴더에서, 해당 대상의 적재 승인 후에 실행해요.

```bash
docker exec -i pheeeew-dev-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X --single-transaction \
    -v ON_ERROR_STOP=1 -f -' < sgis-2025-2Q-import.sql
docker exec -i pheeeew-dev-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X --single-transaction \
    -v ON_ERROR_STOP=1 -v sido_count=17 -v sigungu_count=252 -v emd_count=3559 -f -' < verify.sql
```

운영 RDS는 접근 가능한 EC2에서 승인된 접속·인증·TLS 설정으로 실행해요.
대상 확인은 아래 백필 절차의 `current_database()`, `current_user`와 데이터셋 조회를 사용해요.

```bash
psql -X --single-transaction -v ON_ERROR_STOP=1 -f sgis-2025-2Q-import.sql
psql -X --single-transaction -v ON_ERROR_STOP=1 \
  -v sido_count=17 -v sigungu_count=252 -v emd_count=3559 -f verify.sql
```

각 종료 코드 0을 확인해요. 적재와 품질 검증은 각각 별도 트랜잭션이므로 적재 성공만으로 공개하지 않아요.
적재 실패는 그 트랜잭션을 롤백해요. 연결이 끊겼다면 커밋 여부를 추측하지 말고 경계 개수와 검증 시각을 확인해요.
검증 실패 후 이미 들어간 경계를 임의 삭제하거나 재적재하지 않아요. 실패 원인과 실제 DB 상태를 먼저 검토해요.
이 절차는 `backfill_verified_at`을 설정하지 않아요. 기존 감정 백필과 완료 검증은 아래 별도 절차예요.

2026-10-04에 받은 ZIP 3종의 체크섬을 대조하고 전달용 SQL을 생성해 격리 검증했어요.
SQL 크기는 639,871,612바이트이며 SHA-256은
`8ff11e6b9c62b40cc2d6aec98ec236bff5b452910a818462ad47d816f3647a60`이에요.
이 SQL의 격리 검증 1건이 실패·오류·건너뜀 없이 통과했어요.
PostgreSQL 17.5 / PostGIS 3.5 이미지에서 Flyway 26개 적용과 전국 적재·품질·표시점 5개 분류를 확인했어요.
이 결과는 운영 적재·백필 완료나 성능 개선의 증거가 아니에요.

## 2026-10-04 격리 검증 결과

PostgreSQL 17.5 / PostGIS 3.5.2 Testcontainers에서 받은 전국 원본을 검사했어요.
시도 17·시군구 252·읍면동 3,559개는 적재와 스키마 제약을 통과했어요.
합성 품질 테스트 8건은 통과했고 실제 자료 테스트 1건은 면적 겹침 검사에서 실패했어요. 건너뛴 테스트는 없어요.
실제 자료는 검증 완료로 표시되지 않았고, 표시점 5개 분류는 앞선 실패로 실행하지 않았어요.
개발·운영 적재나 감정 백필은 실행하지 않았어요.

변환된 경계에서 읍면동 14쌍의 면적 겹침이 발견됐고, 그 14쌍 모두 원본 EPSG:5179에서도 겹쳤어요.
시도·시군구의 변환 경계에서는 면적 겹침이 발견되지 않았고, 원본 도형 3,828개는 모두 유효했어요.
아래 면적은 `ST_Area(ST_Transform(ST_Intersection(a.boundary, b.boundary), 5179))`로 계산한 제곱미터예요.
관측 범위는 약 0.000989~0.185260㎡이며, 작은 면적이 감정 분류·집계 영향이 없음을 보장하지는 않아요.
이 최초 실행은 무겹침 기준으로 실패했어요. 이후 14쌍에 한정한 쌍별 1㎡ 이하 예외를 승인받아 적용했어요.
승인된 예외도 도형 보정이나 집계 영향이 없다는 보장을 뜻하지는 않아요.

승인한 예외를 적용한 뒤 같은 원본으로 강제 재실행했어요. 품질 회귀 13건·분류 회귀 9건·실제 자료 1건,
총 23건이 실패·오류·건너뜀 없이 통과했어요. 실제 전국 경계의 검증 완료 표시와 행정동 표시점 5개 분류도 통과했어요.
실행은 격리된 Testcontainers에 한정됐으며 `backfill_verified_at`은 설정하지 않았어요.

| 코드 쌍 | 지역 | 변환 경계 교집합 면적(㎡) |
| --- | --- | ---: |
| `23090590` / `23090600` | 용현5동 / 학익1동 | 0.185260 |
| `23090600` / `23090610` | 학익1동 / 학익2동 | 0.110716 |
| `23040600` / `23090600` | 옥련2동 / 학익1동 | 0.059969 |
| `23090660` / `23090760` | 주안2동 / 용현1·4동 | 0.050198 |
| `23090670` / `23090760` | 주안3동 / 용현1·4동 | 0.032228 |
| `23090570` / `23090760` | 용현3동 / 용현1·4동 | 0.031554 |
| `23090540` / `23090760` | 숭의4동 / 용현1·4동 | 0.018837 |
| `23090600` / `23090740` | 학익1동 / 문학동 | 0.011352 |
| `23040530` / `23090600` | 연수1동 / 학익1동 | 0.008461 |
| `23090610` / `23090760` | 학익2동 / 용현1·4동 | 0.008203 |
| `23040560` / `23090600` | 청학동 / 학익1동 | 0.005086 |
| `23010540` / `23090600` | 신흥동 / 학익1동 | 0.004823 |
| `23090590` / `23090760` | 용현5동 / 용현1·4동 | 0.001564 |
| `23090560` / `23090760` | 용현2동 / 용현1·4동 | 0.000989 |

받은 ZIP의 SHA-256은 다음과 같아요. 이는 이번 검증 원본의 기록이며 도구가 원본 동일성을 자동 보장하는 기능은 아니에요.
운영 준비 전 이 체크섬과 원본 ZIP을 대조해요. 다른 원본에 이 예외 목록을 그대로 적용하지 않아요.

| ZIP | SHA-256 |
| --- | --- |
| `bnd_sido_00_2025_2Q.zip` | `69964db53e0c97289c27f58569b7845781f63d340c8f91146dfd4da8a09ddc31` |
| `bnd_sigungu_00_2025_2Q.zip` | `3413451b0243d7f3a4b41636fec27bbb19cf855e7f7bdca84bd33fbad91fcbbc` |
| `bnd_dong_00_2025_2Q.zip` | `6a675f0c5ae569f33fbe903d620d804fb2672ca65790ea53b23aba7118fa9ec7` |

적재와 검증은 같은 데이터셋 메타 행을 잠가요. 제공된 도구는 기존 경계를 덮어쓰거나 기존 검증 시각을 갱신하지 않아요.
이 불변성은 제공된 도구의 계약이며, DB 권한을 가진 운영자의 임의 SQL을 차단하는 장치는 아니에요.
검증 완료 후 새 감정 분류를 활성화해요. 경계 적재·검증은 `backfill_verified_at`을 설정하지 않아요.

## 기존 감정 백필 배치

자동 배포·애플리케이션 시작·Flyway에서 실행하지 않아요. 개발과 운영은 각각 별도 승인을 받아요.
승인한 커밋의 `backfill-batch.sql`을 EC2 작업 폴더에 복사하고, 아래 명령은 그 폴더에서 실행해요.
행정동 참조 컬럼과 검증된 `SGIS_2025_2Q` 경계가 준비돼 있어야 해요.
현재 백필·완료 검증은 `V20261005_1__add_emd_region_distance_classification.sql`의 공통 분류 함수도 필요해요.
접속 계정의 읽기·감정 분류 컬럼 갱신·임시 테이블 생성 권한을 확인해요.
백필에는 `psql`만 필요하고 `shp2pgsql`·`pyproj`는 필요하지 않아요.

### 대상 DB와 범위 확인

개발 Compose의 DB 컨테이너는 `pheeeew-dev-postgres`예요. 개발 EC2에서 연결해요.

```bash
docker exec -it pheeeew-dev-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X'
```

운영은 RDS에 접근할 수 있는 EC2의 `psql`로 연결해요. 승인한 `PGHOST`·`PGPORT`·`PGDATABASE`·`PGUSER`와
인증·TLS 설정을 사용해요. 암호는 SQL이나 명령에 넣지 않고 보호된 인증 설정 또는 프롬프트를 사용해요.

```bash
psql -X
```

연결 후 두 환경에서 같은 조회를 실행해 대상·준비 상태·건수를 확인해요.

```sql
SELECT current_database(), current_user;
SELECT dataset_key, boundaries_verified_at, backfill_verified_at FROM public.region_datasets;
SELECT count(*) AS total_count,
       count(*) FILTER (WHERE region_classified_at IS NULL) AS unclassified_count,
       COALESCE(max(id), 0) AS upper_id
FROM public.emotions;
```

`boundaries_verified_at`이 없으면 백필은 실패해요. 확인한 ID 상한을 작업 기록에 남기고 같은 상한으로 반복해요.
이 상한은 한 번의 작업 범위예요. 아직 커밋하지 않은 낮은 ID나 상한 밖의 미분류 행까지 없다는 보장은 아니에요.
`\q`로 연결을 종료한 뒤 셸에서 `REGION_BACKFILL_UPPER_ID`에 확인한 상한을 지정해요.

### 한 배치 실행

아래 100건은 작게 시작하는 예시예요. 실제 데이터 규모·쿼리 계획·일반 요청 영향을 보고 조정해요.
백필 전용 인덱스는 추가하지 않아요. 필요성이 확인되면 별도 승인한 작업용 인덱스를 검토해요.

개발 EC2에서는 호스트의 파일을 컨테이너 `psql`의 표준 입력으로 전달해요.

```bash
: "${REGION_BACKFILL_UPPER_ID:?조회한 ID 상한을 먼저 지정하세요}"
REGION_BACKFILL_BATCH_SIZE=100
docker exec -i pheeeew-dev-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X --single-transaction \
    -v ON_ERROR_STOP=1 -qAt -v batch_size="$1" -v upper_id="$2" -f -' \
  sh "$REGION_BACKFILL_BATCH_SIZE" "$REGION_BACKFILL_UPPER_ID" < backfill-batch.sql
```

운영 EC2에서는 같은 파일을 RDS 연결에 전달해요.

```bash
: "${REGION_BACKFILL_UPPER_ID:?조회한 ID 상한을 먼저 지정하세요}"
REGION_BACKFILL_BATCH_SIZE=100
psql -X --single-transaction -v ON_ERROR_STOP=1 -qAt \
  -v batch_size="$REGION_BACKFILL_BATCH_SIZE" -v upper_id="$REGION_BACKFILL_UPPER_ID" -f backfill-batch.sql
```

### 결과와 재실행

한 호출은 한 배치·한 트랜잭션이에요. 종료 코드 0을 확인한 뒤 출력 숫자를 커밋된 처리 건수로 기록해요.
성공한 배치는 다시 처리하지 않아요. 오류가 나면 그 배치는 롤백되고, 원인을 해결한 뒤 같은 상한으로 재실행해요.
현재 연결이 끊긴 경우 커밋 여부를 추측하지 않고 미분류 잔량을 확인해 다시 실행해요.

같은 DB의 배치·완료 검증 도구에 두 정수 advisory lock 키 `(596, 1)`을 예약해요.
동시 배치는 기다리지 않고 `다른 감정 행정동 백필 배치가 실행 중입니다.` 오류와 비정상 종료로 거부해요.
커밋·롤백·접속 종료 시 잠금이 해제돼요. 배치 호출 사이에는 잠금이 없고 전체 작업자 소유권을 유지하지 않아요.
이 잠금은 같은 키를 사용하는 도구끼리만 적용돼요. 일반 감정 수정과의 충돌은 행 잠금으로 처리해요.
잠금 충돌은 성공한 0건 배치와 달라요. 충돌한 호출을 완료로 세지 않아요.

처리 0건은 완료를 뜻하지 않아요. 잠긴 행을 건너뛰거나 지정한 상한까지만 처리했을 수 있어요.
위 전체 미분류 건수와 상한 내 잔량을 구분하고, 잠긴 행이 풀린 뒤 같은 범위로 다시 실행해요.
이 배치는 `backfill_verified_at`을 설정하지 않아요. 잔량 조회 한 번만으로 운영 동시성까지 검증됐다고 판단하지 않아요.

### 전체 백필 완료 검증

SGIS 경계를 적재·검증한 뒤 모든 감정 등록을 새 서버로 전환하고 구버전의 진행 중 등록을 끝내요.
앱 버전과 관계없이 새 서버는 신규 감정을 분류하므로, 백필 중에도 등록할 수 있어요.
구버전 서버 전체를 중단할 필요는 없지만 구버전 코드가 감정을 등록하지 않게 해야 해요.
앱스토어·플레이스토어 배포 완료나 사용자 0명은 백필 완료의 조건이 아니에요.
실제 트래픽이 적은 시간에 작은 배치부터 실행하고 일반 요청과 DB 부하를 관찰해요.
접속 계정의 `emotions` SHARE 테이블 잠금 권한과 `region_datasets` 조회·행 잠금·완료 시각 갱신 권한도 확인해요.

배치와 동일하게 승인한 파일을 EC2 작업 폴더에 전달한 뒤 실행해요. 개발 EC2에서는:

```bash
docker exec -i pheeeew-dev-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X --single-transaction \
    -v ON_ERROR_STOP=1 -qAt -f -' < backfill-verify.sql
```

운영 EC2에서는 승인된 RDS 접속 설정을 사용해요.

```bash
psql -X --single-transaction -v ON_ERROR_STOP=1 -qAt -f backfill-verify.sql
```

같은 advisory lock 획득 후 `emotions`의 쓰기를 막는 테이블 잠금을 대기 없이 시도해요.
진행 중 쓰기나 다른 백필과 충돌하면 실패하며, 잠금 획득 뒤 전체 미분류를 검사해요.
ID 상한·삭제·공개 여부로 범위를 줄이지 않아요. 분류 시각이 없는 기록과 새 규칙으로 배정 가능한 NULL 코드가 없어야 해요.
코드가 NULL이어도 분류 시각이 있고 새 규칙의 서비스 범위 밖이면 처리 완료예요.
경계 검증과 위 두 잔량 0건을 확인한 동일 트랜잭션에서 완료 시각을 기록해요. 종료 코드 0을 확인해요.
재실행도 전체를 다시 검사하고 최초 완료 시각을 보존해요. 오류는 트랜잭션을 롤백해요.
검사 중 등록·수정·삭제는 잠시 대기할 수 있어요. 각 SQL 명령은 5초 제한이며 전체 트랜잭션 시간 제한은 아니에요.
실제 데이터에서 5초 내 검사가 끝난다는 보장은 없어요. 시간 초과를 성공으로 판단하거나 무조건 제한을 늘리지 않아요.

완료 시각은 위 운영 전제 아래 쓰기를 막고 미분류 0건을 확인한 시각이에요. 영구적인 NULL 금지 제약은 추가하지 않아요.
구버전 서버로 롤백하거나 직접 SQL로 미분류를 넣으면 이 전제가 깨지고 최초 완료 시각만으로 현재 상태를 보장할 수 없어요.
그 경우 지역 요약 제공을 보류하고 신규 등록 경로를 복구한 뒤 잔량 백필·전체 검증을 다시 수행해요.

명령은 저장소의 개발 Compose·운영 RDS 구조를 기준으로 한 절차예요. 실제 서버 접속·권한·부하는 아직 검증하지 않았어요.

## 1km 인접 지역 정책으로 전환

이 절차는 기존 SGIS 경계를 다시 적재하지 않아요. 포함 판정이 우선이며 미매칭만 폴리곤까지 타원체 최단 거리
1,000m 이하의 행정동에 배정해요. 동률은 코드 오름차순이고 감정의 저장 좌표는 바꾸지 않아요.
신규 등록의 범위 밖 좌표는 `EMOTION-014`·400, 경계 미검증은 `EMOTION-013`·503이에요.
기존 기록·수정·삭제는 유지하고 성공한 requestId 재시도는 기존 결과를 반환해요. 기존 기기별 1초 제한은 먼저 적용돼요.

개발·운영 각각 승인한 대상에서 다음 순서로 진행해요. 시작 SQL 실행, 서버 배포, 재분류는 별도 승인 대상이에요.

1. 앞의 대상 DB 확인과 경계 검증 상태를 확인해요. 같은 커밋의 `reclassification-start.sql`,
   `reclassification-batch.sql`, `backfill-verify.sql`을 EC2 작업 폴더에 전달하고 체크섬을 대조해요.
2. 서버 배포 전에 시작 SQL로 `backfill_verified_at`만 해제해요. 시작 SQL은 기존 테이블만 사용해요.
   커밋 뒤 새 준비 검사부터 지역 집계는 503이에요. 이미 검사를 통과한 요청은 완료될 수 있어요.
3. 새 서버와 공통 함수 마이그레이션을 배포하고 구버전의 감정 등록·진행 중 쓰기를 종료해요.
   앱스토어·플레이스토어 배포 완료는 필요하지 않아요. 경계 검증이 유지되므로 새 등록은 분류할 수 있어요.
4. 아래 잔량과 ID 상한을 확인하고 재분류 배치를 반복해요. 분류 시각이 없는 잔량은 일반 백필도 필요해요.
5. 두 잔량이 없어지면 앞의 `backfill-verify.sql`로 전체 완료를 검증해요. 종료 코드 0인 경우에만 집계를 다시 열어요.

### 전환 시작

개발 EC2의 DB 컨테이너에서는:

```bash
docker exec -i pheeeew-dev-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X --single-transaction \
    -v ON_ERROR_STOP=1 -qAt -f -' < reclassification-start.sql
```

운영 EC2에서는 앞의 승인된 RDS 접속 설정을 사용해요.

```bash
psql -X --single-transaction -v ON_ERROR_STOP=1 -qAt -f reclassification-start.sql
```

시작 SQL의 출력 `t`와 종료 코드 0을 확인해요. 경계 검증 시각과 감정은 변경하지 않아요.
전환 중 시작 SQL 재실행은 무해하지만, 완료 후에는 실행하지 않아요. 완료 표시를 다시 해제하기 때문이에요.
시작만 해제하고 배포·재분류가 중단되면 집계는 계속 503이에요. 완료 시각을 임의로 복구하지 말고 잔량·배포 상태를 점검해요.

### 재분류 잔량과 배치

공통 함수 배포 뒤 승인한 DB 연결에서 조회해요. ID 상한은 앞의 백필 절차처럼 작업 기록에 남겨요.

```sql
SELECT to_regprocedure('public.find_emd_region_code(geometry)') AS classifier,
       to_regclass('public.idx_regions_emd_geography_gist') AS geography_index;
SELECT COALESCE(max(id), 0) AS upper_id,
       count(*) FILTER (WHERE region_classified_at IS NULL) AS unclassified_count,
       count(*) FILTER (WHERE region_code IS NULL AND public.find_emd_region_code(location) IS NOT NULL) AS assignable_null_count
FROM public.emotions;
```

대상은 지역 코드가 NULL이고 새 함수로 배정 가능한 기록이에요. 범위 밖 NULL을 LIMIT 전에 제외해 선두의 먼 좌표가
뒤의 대상을 막지 않게 해요. 기존 배정·범위 밖 기록과 좌표·내용·감사 시각·version은 보존해요.
배정되는 기록만 지역 코드와 분류 시각을 갱신해요. 행 잠금과 조건부 갱신으로 동시 수정·배정을 보존해요.
시작을 누락하고 집계가 열린 상태에서 실제 대상이 있으면 실패해요. 이 오류를 0건으로 처리하지 않아요.

개발 EC2에서는:

```bash
: "${REGION_BACKFILL_UPPER_ID:?조회한 ID 상한을 먼저 지정하세요}"
REGION_BACKFILL_BATCH_SIZE=100
docker exec -i pheeeew-dev-postgres sh -c \
  'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -X --single-transaction \
    -v ON_ERROR_STOP=1 -qAt -v batch_size="$1" -v upper_id="$2" -f -' \
  sh "$REGION_BACKFILL_BATCH_SIZE" "$REGION_BACKFILL_UPPER_ID" < reclassification-batch.sql
```

운영 EC2에서는 같은 상한과 배치 크기를 지정하고:

```bash
: "${REGION_BACKFILL_UPPER_ID:?조회한 ID 상한을 먼저 지정하세요}"
REGION_BACKFILL_BATCH_SIZE=100
psql -X --single-transaction -v ON_ERROR_STOP=1 -qAt \
  -v batch_size="$REGION_BACKFILL_BATCH_SIZE" -v upper_id="$REGION_BACKFILL_UPPER_ID" -f reclassification-batch.sql
```

시작·일반 백필·재분류·완료 검증은 같은 `(596, 1)` 잠금을 사용해요. 잠긴 행은 건너뛰므로 0건만으로 완료를 선언하지 않아요.
배치가 실패하면 전체 배치를 롤백해요. 완료 후 대상 없는 배치 재시도는 0건이고 완료 시각을 보존해요.
완료 검증 이후의 구버전·직접 SQL 쓰기까지 영구 차단하지 않아요. 그런 쓰기가 재유입되면 시작부터 다시 전환해요.

### 2026-10-05 로컬 정책 검증

위 ZIP 3종과 전달용 SQL의 SHA-256을 다시 대조한 뒤 기존 SQL을 사용했어요. 원본이나 경계를 보정하지 않았어요.
Testcontainers `postgis/postgis:17-3.5`의 PostgreSQL 17.5 / PostGIS 3.5.2에서 Flyway 27개와 전국 적재·품질 검증을 통과했어요.
기존 마이그레이션 26개로 전국 경계를 적재·검증한 뒤 정책 마이그레이션 1개를 적용하는 업그레이드 경로도 같은 검증을 통과했어요.
완료한 실행의 컨테이너 제한은 CPU 2개·메모리 2GiB예요. 개발·운영 DB나 실제 감정 원본을 사용하지 않았어요.

합성 좌표 3,690개는 행정동 표시점 3,559개, 코드순 매 225번째 행정동의 첫 폴리곤 첫 꼭짓점에서
45도 간격으로 500m 이동한 128개, 미국·원점·도쿄의 먼 좌표 3개예요.
포함 규칙에서는 NULL 5개, 새 규칙에서는 NULL 3개였어요. 인접 미매칭 2개는 전체 행정동의 타원체 최단 거리
정렬 결과와 일치했고 재분류됐어요. 표시점·500m 이동점은 모두 배정됐고 먼 좌표 3개는 제외됐어요.
거리 후보 조회는 geography GiST, 완료 잔량 조회는 기존 지역 코드 인덱스를 사용했어요.
시작·배치·완료·완료 후 0건 재시도와 비분류 필드 보존을 확인했어요. 완료 SQL 2회는 기존 명령별 5초 제한 안에서 성공했어요.

최초 512MiB 제한의 적재 `psql`은 종료 코드 137로 끝났고 통과하지 못했어요. OOM 원인은 확정하지 않았어요.
이어진 2GiB 실행은 JSON 필드 보존 비교에서 실패했어요. psql의 `Etc/UTC`와 JDBC 초기 `Asia/Seoul`을 확인하고,
양 세션을 UTC로 통일한 재실행에서 날짜 필드를 제외하지 않은 동일 비교와 전체 검증을 통과했어요.
실패한 비교의 원인은 시간대 표현 차이로 추정하며, 해당 실행은 통과로 집계하지 않아요.

이 결과는 개발 서버 512건의 재배정 수·메모리 한도나 운영의 5초 완료를 보장하지 않아요.
HTTP 요청 수·응답 바이트·반환 객체 수와 실제 기기의 CPU·메모리·프레임은 이번에 측정하지 않았어요.
실제 DB의 잔량·쿼리 계획·트래픽 영향은 각 대상의 실행 승인 후 확인해요. 시간 초과는 실패로 처리해요.
