# 최신 백엔드를 로컬에 실행한 지도 핀 실험

## 구성

- 백엔드: `origin/be/dev`의 `356c40ab` 전체 구현. Spring Boot의 `local` 프로필과 실제 Flyway/PostGIS DB를 사용한다.
- 클라이언트: 이전 실험의 `6de92800`에 `client-local-backend.patch`를 적용한 별도 측정 앱. 공유 렌더러는 같은 코드이며, 개발 API 호출에 사용하던 앱의 Ktor HTTP 클라이언트를 사용한다. 이전 고정 응답 조건은 HttpURLConnection을 사용했다.
- API: 이전 앱과 동일한 `/api/v1/emotions/map`. 새 `/api/v2/emotions/map`의 콘텐츠 필터/클러스터링 실험은 아니다.
- 전용 DB에 이전 고정 데이터와 같은 좌표·감정 5종·회전각의 핀을 준비한다. 최신 스키마 FK를 만족시키기 위해 실험용 지역 3개를 추가한다.
- 데이터 준비를 요청→응답 계측 전에 완료한다. ADB 파일 제어 채널은 준비 완료만 알려주며, API 응답을 생성하거나 중계하지 않는다.
- 같은 무작위 순서(seed `20261011`), 9개 조건×30회, 페이지당 200개, 처음 50개 3회 준비 실행. 조회 사이에는 빈 지도 프레임을 확인한다.

## 재현

백엔드와 클라이언트를 각각 독립 체크아웃한다. `environment.json`의 정확한 커밋을 사용한다.

1. 백엔드 체크아웃의 `BE` 절대 경로를 `BACKEND_ROOT`로 지정한다.
2. 실험 전용 RSA 키를 만들고 PKCS8 DER 개인키와 SPKI DER 공개키를 base64로 인코딩해 `JWT_PRIVATE_KEY_BASE64`, `JWT_PUBLIC_KEY_BASE64` 환경 변수에 넣는다. 기존 서버 인증 키를 사용하지 않는다.
3. 이 폴더의 `compose.yml`로 실행한다.

```sh
docker compose -f compose.yml up -d --build
```

4. 클라이언트 베이스 체크아웃에서 패치를 적용하고 Android SDK 경로를 설정해 빌드한다.

```sh
git apply /ABSOLUTE/PATH/TO/client-local-backend.patch
cd AN/Pheeeew
./gradlew :mapPinBenchmarkTarget:assembleRelease
adb -s R5CX11AKCYJ install -r mapPinBenchmarkTarget/build/outputs/apk/release/mapPinBenchmarkTarget-release.apk
adb -s R5CX11AKCYJ reverse tcp:18080 tcp:18080
```

다른 기기라면 아래 명령과 `run.py`의 ADB 시리얼을 함께 변경한다. 이 컨트롤러는 전용 컨테이너 `pheeeew-pin-perf-606-db-1`의 **실험용 emotions 행을 교체한다**. 다른 DB를 대상으로 사용하지 않는다.

5. 측정 앱을 새로 실행하고 이 결과 폴더에서 컨트롤러를 실행한다.

```sh
adb -s R5CX11AKCYJ shell am force-stop com.pheeeew.mappinprofile
adb -s R5CX11AKCYJ shell am start -n com.pheeeew.mappinprofile/.MapPinBenchmarkActivity --ei repetitions 30 --ei count -1 --ez reset true
python3 run.py
python3 summarize.py
python3 charts.py
```

`charts.py`는 reportlab이 필요하다. 컨트롤러가 완료 기록을 확인하면 `measurements.jsonl`을 저장한다. 재실행하면 파일을 덮어쓰므로 결과를 먼저 보관한다. `--ei count 200 --ei repetitions 3`처럼 단일 조건을 실행할 때는 DB를 별도로 준비하고 결과를 직접 수집한다.

6. 완료 후 실험 컨테이너를 중지하고 USB 포워딩을 해제한다. DB 볼륨은 재현을 위해 보존할 수 있다.

```sh
adb -s R5CX11AKCYJ reverse --remove tcp:18080
docker compose -f compose.yml stop
```

## 파일

- `report.md`: 구간별·개수별·시간별 결과 및 이전 실험 비교
- `measurements.jsonl`, `pilot.jsonl`: 본 실험 및 예비 실험 원시 기록
- `summary.json`, `runs.csv`, `pages.csv`, `time-counts.csv`: 검증 후 요약
- `environment.json`: 커밋, APK 해시, 자원 및 기기 조건
- `client-local-backend.patch`, `compose.yml`, `seed.sql`, `run.py`: 재현 입력과 제어 절차

인증 키와 토큰은 결과 파일에 포함하지 않는다. 이 로컬 결과는 배포된 개발/운영 서버의 지연과 다르다.

## 중단 기록

첫 본 실험은 201회 측정 후 데이터 준비 제어 파일을 읽는 순간 ADB 파일 교체와 충돌해 중단됐다. `aborted-control-race.jsonl`은 이 중단 기록이며 최종 통계에서 전부 제외한다. 컨트롤러를 임시 파일 전송 후 원자적 이름 변경 방식으로 수정하고 270회를 처음부터 다시 실행했다. 이 재실행도 화면 재시작으로 95회 후 제어가 중단됐다. `resume-base.jsonl`에 95회 성공 기록을 보존하고, 재시작 내성과 기록 보존을 추가한 측정 앱으로 232회까지 수집했다. 그중 1,600개/14번 조건은 API 응답 수신에 실패했다. 이를 재시도하지 않고 보존한 뒤 마지막 38개 조건을 이어서 수행했다. 따라서 최종 기록은 95회 + 137회 + 38회의 세 구간이며 렌더러·API·시간 계측과 조건 순서는 유지됐다. 재개 시에는 `--ez reset true`를 사용하지 않는다. 최종 `measurements.jsonl`에는 두 구간의 270개 고유 실행이 누적되며, 통계는 이 파일만 사용한다.
