# 클라이언트 300m 격자 중심 계산 가이드

> 이전 계약 보존 문서예요. [ADR-0006](../adr/0006-use-independent-uniform-disks-for-sigh-location.md)과 [300m 균등 원 가이드](300m-uniform-disk.md)로 대체됐어요. 아래 내용은 구형 앱의 동작과 당시 계산 근거이며 새 구현에 적용하지 않아요.

한숨 등록 요청에는 사용자의 실제 위치가 아니라, 실제 위치가 속한 `300m × 300m` 격자의 중심 좌표를 전달해요.

## 최종 계약

```text
기기에서 WGS84 실제 위치 획득
→ EPSG:5179로 변환
→ 300m 격자 중심 계산
→ 중심을 WGS84로 역변환
→ 실제 위치 폐기
→ 격자 중심만 서버로 전송
→ 서버가 격자 안의 최종 표시 위치를 한 번 생성
→ 서버 응답의 최종 위치를 지도에 표시
```

- 클라이언트는 격자 안에서 좌표를 무작위로 이동시키지 않아요.
- 서버로 실제 위치를 보내지 않아요.
- 등록 직후 지도에는 요청으로 보낸 격자 중심이 아니라 서버 응답 좌표를 사용해요.

## 계산 순서

1. 기기의 WGS84 좌표를 `longitude`, `latitude` 순서로 준비해요.
2. 좌표를 `EPSG:4326`에서 `EPSG:5179`로 변환해요.
3. 변환 결과를 각각 `easting`, `northing`으로 명명해요.
4. 두 축에 아래 식을 적용해요.
5. 계산한 중심을 `EPSG:5179`에서 `EPSG:4326`으로 역변환해요.

```text
centerEasting  = floor(easting  / 300) * 300 + 150
centerNorthing = floor(northing / 300) * 300 + 150
```

역변환 결과의 `longitude`, `latitude`를 한숨 등록 API에 전달해요.

```text
function toGridCenter(longitude, latitude):
    (easting, northing) = transform4326To5179(longitude, latitude)

    centerEasting  = floor(easting / 300.0) * 300.0 + 150.0
    centerNorthing = floor(northing / 300.0) * 300.0 + 150.0

    return transform5179To4326(centerEasting, centerNorthing)
```

좌표 변환 라이브러리가 `always_xy` 같은 옵션을 제공하면 활성화해 `x = longitude/easting`, `y = latitude/northing` 순서를 고정해요. 배열 위치만 보고 축을 추측하지 않아요.

라이브러리에 `EPSG:5179`가 내장되어 있으면 EPSG 코드로 사용해요. 직접 정의해야 한다면 서버 PostGIS와 같은 정의를 사용해요.

```text
+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996
+x_0=1000000 +y_0=2000000 +ellps=GRS80
+towgs84=0,0,0,0,0,0,0 +units=m +no_defs
```

## 공통 테스트 좌표

다음 값은 PostgreSQL 17·PostGIS 3.5의 `ST_Transform` 결과를 기준으로 해요.

| 단계 | X축 | Y축 |
| --- | ---: | ---: |
| 입력 WGS84 | longitude `126.9780` | latitude `37.5664` |
| EPSG:5179 변환 | easting `953901.103685` | northing `1952020.986470` |
| 300m 격자 중심 | easting `953850.0` | northing `1951950.0` |
| 중심의 WGS84 역변환 | longitude `126.9774258201` | latitude `37.5657576255` |

클라이언트 테스트에서는 최소한 다음을 검증해요.

1. 입력 좌표가 위 격자 중심 `953850.0, 1951950.0`으로 계산되는지 확인해요.
2. 역변환 좌표가 위 WGS84 값과 허용 오차 안에서 일치하는지 확인해요.
3. 같은 실제 좌표가 항상 같은 격자 중심을 만드는지 확인해요.
4. 클라이언트 코드가 임의 오프셋이나 난수를 적용하지 않는지 확인해요.

좌표 변환 라이브러리별 부동소수점 차이는 허용하되, 격자 중심의 `EPSG:5179` 값은 정확히 300m 셀의 중앙값이어야 해요.

## 등록 요청과 응답 처리

`POST /api/v1/sighs`와 `POST /api/v2/sighs`의 `latitude`, `longitude`에 계산한 격자 중심을 넣어요.

```json
{
  "requestId": "5d1ad34e-1e20-4f20-a20e-3825a095fe6b",
  "latitude": 37.5657576255,
  "longitude": 126.9774258201
}
```

- 한 번의 새로운 등록 시도마다 새 `requestId`를 생성해요.
- 동일 등록의 네트워크 재시도에는 같은 `requestId`를 사용해요.
- 성공 응답의 `geometry.coordinates`는 `[longitude, latitude]` 순서예요.
- 등록 직후 지도 이동과 핀 표시는 성공 응답의 좌표를 사용해요.

```json
{
  "type": "Feature",
  "id": 1,
  "geometry": {
    "type": "Point",
    "coordinates": [126.9768, 37.5674]
  },
  "properties": {
    "createdAt": "2026-08-31T10:30:00Z"
  }
}
```

응답 좌표는 서버가 격자 안에서 최초 한 번 확정한 최종 표시 위치이므로 요청의 격자 중심과 다를 수 있어요. 같은 `requestId`로 재시도하면 서버는 최초 응답과 같은 위치를 반환해요.

## 개인정보 처리 주의사항

- 실제 위치를 요청 본문, 모바일 로그, 분석 이벤트 또는 오류 수집 도구에 남기지 않아요.
- 실제 위치를 서버 전송 DTO에 보관하지 않아요.
- 격자 중심 계산이 끝나면 실제 위치에 대한 불필요한 참조를 폐기해요.
- 위도·경도 소수점 자르기로 300m 격자를 흉내 내지 않아요. 경도 1도의 실제 거리는 위도에 따라 달라져요.
- 서버 응답 대신 요청 좌표를 최종 핀 위치로 사용하지 않아요.

결정의 배경과 서버 책임은 [ADR-0002](../adr/0002-use-grid-center-for-sigh-location.md)를 참고해요.
