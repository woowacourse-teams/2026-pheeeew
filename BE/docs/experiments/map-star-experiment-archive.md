# 지도 별 위치 후속 실험 보존 안내

2026-09-07 클라이언트와 서버가 각각 반경 300m 균등 원에서 좌표를 고르는 방향으로 진행하기 전에, E006~E009의 코드·원본·그림을 함께 보존해요. 이 문서는 실험 보존 안내이며 새 제품 계약이나 서비스 구현 완료를 뜻하지 않아요.

## 보존 범위

| 실험 | 비교한 내용 | 원본 결과 | 그림 |
| --- | --- | --- | --- |
| [E006](e006-virtual-center/README.md) | 격자 중심 D와 가상 중심 V·V300 | [실행 기록](e006-virtual-center/results/2026-09-07/README.md) | PNG 6장 |
| [E007](e007-client-location/README.md) | 격자 기반 D와 격자 없는 G·L, 반복 관측 | [실행 기록](e007-client-location/results/2026-09-07/README.md) | PNG 5장 |
| [E008](e008-age-style/README.md) | 같은 D/G 좌표·생성 시간에 따른 색상·감쇠 | [실행 기록](e008-age-style/results/2026-09-07/README.md) | PNG 37장·GIF 6개 |
| [E009](e009-location-pipeline/README.md) | 서버 단일 원·클라이언트와 서버 이중 원·기존 D | [실행 기록](e009-location-pipeline/results/2026-09-07/README.md) | PNG 4장 |

실행 코드와 테스트는 [실험 패키지](../../src/test/java/com/pheeeew/sigh/experiment/e001/)에 있어요. 서비스 런타임에서 호출하지 않아요. E006·E007은 기존 E001 sampler와 통계를, 네 실험의 그림은 E001 sprite를 재사용해요. E008·E009는 E007의 합성 좌표 CSV를 입력으로 사용해요. 이 의존 파일을 지우면 과거 결과를 다시 만들 수 없어요.

각 실험의 `results/2026-09-07/raw/`에는 원본 결과를, `source-before.sha256`에는 실행 당시 소스·입력 해시를 보존해요. `run1-checksums.sha256`은 첫 실행의 결과 해시이고 `raw/`는 두 번째 실행 결과예요. 기존 소스·사전 프로토콜·출처 문서·결과는 수정하지 않아요. 문서의 “미커밋”, “선호 미정”은 당시 실행 시점의 상태예요.

## 이후 구현과 구분해요

사용자는 E009의 ②, 즉 클라이언트 300m 원 이동 후 서버 300m 원 이동을 선택했어요. 실제 위치는 기기에 남기고 근사 좌표만 전송하는 방향이에요. 두 원의 이동을 합하므로 합성 평면 모델의 최종 실제 위치 기준 최대 거리는 600m예요.

E009의 ③ “기존 D”는 이전에 비교한 가우시안 후보이지 서비스의 사각형 sampler가 아니에요. 이번 보존 작업은 서비스 코드·ADR·OpenAPI·DB를 바꾸지 않아요. 제품 코드 교체, 좌표계 변환·멱등성 검증, 클라이언트 변경과 구형 앱 전환, PR 전 실제 앱 화면 확인은 후속 작업이에요. E008의 24시간 수명과 색상도 제품 정책으로 확정하지 않아요.

## 보존 검증 — 2026-09-07

새 실험을 생성한 것이 아니라 기존 산출물과 테스트를 다시 확인했어요.

- E006~E009 단위 테스트 20개가 통과했어요. 실패·오류·건너뜀은 0개이고 Gradle 결과는 `BUILD SUCCESSFUL`이에요.
- 네 소스 manifest의 총 32항목과 결과 manifest의 총 69파일 해시가 모두 일치했어요. 공유 파일은 소스 manifest마다 중복 포함돼요.
- 보존한 첫 실행 manifest와 `raw/` manifest가 모두 일치했어요. 로컬에 남아 있는 실제 run1·run2 파일도 해시를 확인해 보존본과 같음을 확인했어요.
- 기존 독립 검증 스크립트로 E007의 시각 통계 72행·반복 관측 통계 42행, E008의 통계 120행, E009의 통계 12행을 CSV에서 재계산해 일치를 확인했어요. E008·E009의 E007 입력 좌표 보존도 확인했어요.

BE 디렉터리에서 다음 명령으로 가까운 테스트를 실행해요. Java 21을 사용하며 이 실험 단위 테스트에는 DB 컨테이너가 필요하지 않아요.

```bash
./gradlew test \
  --tests 'com.pheeeew.sigh.experiment.e001.E006VirtualCenterExperimentTest' \
  --tests 'com.pheeeew.sigh.experiment.e001.E007ClientLocationExperimentTest' \
  --tests 'com.pheeeew.sigh.experiment.e001.E008AgeStyleExperimentTest' \
  --tests 'com.pheeeew.sigh.experiment.e001.E009LocationPipelineExperimentTest' \
  --no-daemon
```

아래 명령은 보존 파일을 변경하지 않고 소스·결과 해시와 두 실행 manifest를 확인해요. 각 파일이 `OK`이고 전체 종료 코드가 0이어야 해요.

```bash
for experiment in e006-virtual-center e007-client-location e008-age-style e009-location-pipeline; do
  result="docs/experiments/$experiment/results/2026-09-07"
  shasum -a 256 -c "$result/source-before.sha256" || exit 1
  (cd "$result/raw" && shasum -a 256 -c checksums.sha256) || exit 1
  cmp "$result/run1-checksums.sha256" "$result/raw/checksums.sha256" || exit 1
done
```

전체 재생성 명령과 환경은 각 실험의 README·PROVENANCE에 있어요. 출력 경로는 아직 존재하지 않는 새 디렉터리를 사용해요. 기존 원본을 덮어쓰거나 소스 manifest를 새 값으로 갱신하지 않아요. PNG 바이트 일치는 기존 JDK·OS·폰트 환경을 전제로 해요.

독립 CSV 검증 도구는 [E007](e007-client-location/verify-results.rb), [E008](e008-age-style/verify-results.rb), [E009](e009-location-pipeline/verify-results.rb)에 있어요. Ruby 표준 라이브러리를 사용해요. 이 도구들은 `raw/`가 아니라 `run1/`·`run2/`가 있는 실행 출력 디렉터리를 받아요. 이번에는 로컬에 남은 `build/reports/experiments/`의 기존 실행 출력에 적용했어요. 보존한 단일 `raw/`를 두 실행인 것처럼 복제해 검증하지 않아요.

원본 그림을 다시 렌더링하거나 실제 앱에서 확인한 검증은 아니에요. 개인정보 보호의 형식적 보장, 실제 기기 성능, 제품 조회 만료도 이번 검증 범위에 포함하지 않아요.
