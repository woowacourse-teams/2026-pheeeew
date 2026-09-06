# E001-v2 결과: 팀 합의와 모델 공개

> 현재 상태: 사용자 승인으로 독립 평가는 미완료로 종료·보존하고 모델을 공개했어요. 팀의 네 그림 선택은 모두 D였어요. [모델 공개·정답표 검증·종료 근거](MODEL-DISCLOSURE.md)를 먼저 읽어요. 아래 평가 대기·공개 금지 설명은 공개 전 이력이에요. 종료 시 프로토콜 추천 `pending-review`를 합격으로 바꾸지 않았으며 같은 batch는 블라인드 평가에 재사용하지 않아요.

> 이 문서는 실행 담당자용 수치 기록이에요. 평가자에게는 [reviewer-package](reviewer-package/README.md)만 전달해요. 원본·모델 정보·결과 해석은 평가 전에 공유하지 않아요.

2026-09-07 커밋된 v2 runner로 분포·성능·블라인드 package 생성을 실제 실행했어요. 분포 결과는 **`d-review-ready / no-e`**, 최종 추천은 **`pending-review`**예요. D의 성능은 통과했지만 사람의 평가가 남아 있어 모델을 채택하거나 제품 코드를 변경하지 않아요.

## 실행과 보존

| 명령 | 실제 결과 |
| --- | --- |
| `./gradlew e001LocationDistribution --rerun-tasks --no-daemon` | `BUILD SUCCESSFUL`, 46초, 내부에서 분포 두 번 생성·checksum 일치예요. |
| `./gradlew e001LocationPerformance --rerun-tasks --no-daemon` | `BUILD SUCCESSFUL`, 8초, 첫 전체 측정이 유효하고 D 시간 기준을 통과했어요. |
| `./gradlew e001BlindReview --rerun-tasks --no-daemon` | `BUILD SUCCESSFUL`, 8초, 네 쌍의 공개 package를 만들었어요. 사람의 평가를 실행했다는 뜻은 아니에요. |

- protocol SHA: `7f377d867f0eef92facb25230258f07e0cbd45cf`예요.
- runner SHA: `78949eb663c3e3f374d9435cd9a4294cadd080e6`예요.
- 동결한 v1 README blob: `bc679aa3afc90de3f282ca7be449d18978156ad1`예요.
- 분포 checksum 목록 자체의 SHA-256: `476ae49ac4416ab8a98bb22da099a18a327e5a02c9f7808c9c37fa69b735e53e`예요.
- JDK Eclipse Adoptium 21.0.5, Gradle 9.5.1, macOS 26.6.2 aarch64, CPU 수 10, `Asia/Seoul`, 분포·성능 실행 당시 `gitDirty=false`예요. 실험 runner는 실제 사용자 좌표·운영 DB·네트워크를 사용하지 않았어요.

전체 원본은 [coordinator-only/raw](coordinator-only/raw/manifest.json)에 보존했어요. 여섯 필수 데이터 파일과 원본 panel 여덟 장의 checksum 14개, 첫 실행 checksum 증거, 환경, 성능·블라인드 sidecar를 포함해요. `build/reports/experiments/e001-v2/`에서 복사했고 원본 파일을 수정하지 않았어요. 비공개 key와 모델별 원본 panel은 이 경로에만 두고 공개 package에는 넣지 않았어요.

## 좌표 생성 결과

공식 좌표 파일은 두 번째 실행의 **767,000개**예요. 첫 번째 실행도 같은 개수와 결정적 결과를 만들었어요. 두 번 합친 생성 수는 1,534,000개이며 성능·conformance 호출은 별도예요. 요청 누락·sampler 실패는 모두 0개예요.

| 단계 | trial 수 | 실행 한 번의 요청·생성 수 |
| --- | ---: | ---: |
| 튜닝 | 78 | 195,000 |
| 확인 | 16 | 220,000 |
| 격자 스트레스 | 4 | 242,000 |
| 별도 시각 평가 표본 | 8 | 110,000 |
| 합계 | 106 | 767,000 |

sample seed는 `2026090601`~`2026090605`예요. 입력은 합성 300m 격자 중심이고 B~E는 입력 중심에서 `r<300m`를 유지해요. 이 반경은 실제 사용자 위치와의 거리가 아니에요. D conformance는 320행, metrics는 7,112행, 경계 관측은 106 trial×6행=636행, 실패 CSV는 header만 있어요.

## D의 거리 조건과 경계 관측

튜닝으로 고정된 D는 `d-s120-r300`이에요. 아래 세 값은 각 scenario의 다섯 seed에서 계산한 p95·p99의 요약이며, pooled quantile과 구분해요.

| 단일 중심 조건 | p95 median ≤210m | p99 median ≤250m | p99 max ≤270m |
| --- | ---: | ---: | ---: |
| 튜닝 500개 | 193.3261 | 226.5665 | 234.3930 |
| 확인 500개 | 176.8885 | 202.8084 | 232.4206 |
| 확인 5,000개 | 185.8076 | 216.2829 | 228.9530 |
| 시각 평가용 500개 | 188.1084 | 218.1009 | 243.3668 |
| 시각 평가용 5,000개 | 186.7757 | 219.8186 | 222.1262 |

모두 거리 조건을 통과했어요. 경계 관측은 [boundary-observations.csv](coordinator-only/raw/boundary-observations.csv)에 관측 그대로 남겼어요. 예를 들어 확인 500개에서는 두 띠가 모두 비어 `empty-bands`와 빈 밀도비를 기록해요. 확인 5,000개에서는 O=0, I=12, `inner-observed`, raw ratio=0이에요. 이 값으로 합격·탈락시키거나 실제 꼬리 확률이 0이라고 주장하지 않아요.

## E가 다음 평가에 진입하지 못한 이유

E 36개 후보를 모두 튜닝했지만 전체 조건을 만족한 후보는 0개예요. 생성 실패 때문은 아니에요. 아래는 [metrics.csv](coordinator-only/raw/metrics.csv)의 등록된 지표를 다시 비교한 결과이며, 개별 행은 서로 중복되는 조건이에요.

| E 튜닝 조건 | 통과 후보 수 / 36 |
| --- | ---: |
| `radialKs` median ≤0.05 | 0 |
| `a4` median ≤0.5×A | 35 |
| pooled proximity·neighbors·hotspot 세 조건 모두 | 36 |
| `seam300` median ≤0.8×D | 2 |
| 다섯 seed 중 seam 개선 ≥4개 | 3 |
| seam의 두 조건 동시 만족 | 0 |

`radialKs` median은 약 0.06~0.15예요. 이는 D와 E의 경험적 반경 누적분포 차이를 비교한 지표이며 평균 이동 거리나 미터 단위 오차가 아니에요. seam의 두 조건도 동시에 통과한 후보가 없어요. E가 시각적으로 나쁘다는 뜻으로 확대 해석하지 않아요. E의 확인·spectral·성능·블라인드는 실행하지 않았고, 결과를 본 뒤 기준을 낮추거나 다른 파라미터로 재도전하지 않았어요.

D의 `grid300`은 균등 profile에서 1117.4730, 불균형 profile에서 248.3860으로 모두 정의돼요. A는 각각 3.1324, 0.2182예요. 이 값으로 D가 더 자연스럽다고 주장하지 않아요. A의 균등 사각형이 격자를 빈틈없이 채울 수 있어 A/D 비율 합격선은 v2에서 정하지 않았어요. 반복 무늬의 시각 평가가 별도로 필요해요.

## 성능 결과

[performance.csv](coordinator-only/raw/performance/performance.csv)는 D만 warmup 5회·measurement 10회, batch당 100,000점을 측정한 15행이에요. 첫 전체 실행이 유효해 재측정하지 않았어요.

| 측정값 | 결과 | 기준 |
| --- | ---: | ---: |
| measurement ns/점 median | 417.3525ns ≈0.4174μs | ≤100,000ns |
| measurement ns/점 max | 447.97166ns ≈0.4480μs | ≤250,000ns |

이는 점별 RNG 초기화·pure sampler·raw-bit 누적의 시간이에요. point seed SHA-256 준비·DB·좌표계 변환·API 왕복·앱 렌더링은 측정하지 않아요. 실제 서비스 저장 경로의 성능 보장으로 해석하지 않아요.

## 공개 전 그림 제공과 평가 수집 이력

실제 생성한 좌표를 사전등록한 별 sprite로 그린 [평가자 안내와 네 쌍의 그림](reviewer-package/README.md)을 제공해요. 원본 2432×1200px, 각 화면은 1m/px로 고정했어요. 단일 중심 500·5,000개, 3×3 중심당 500·5,000개예요. 이미지와 공개 CSV를 재작성하지 않고 복사했으며 설명용 README만 추가했어요.

지금은 모델명이 붙은 전후 산점도나 우열 해석을 공개하지 않아요. 블라인드 조건 때문에 다섯 명의 완전한 20행을 받기 전에는 어느 쪽이 어떤 모델인지 밝히지 않아요. 현재 수집한 응답은 **4/20행**이며 가상의 평가를 만들지 않았어요. 확정 응답은 [담당자용 수집 CSV](coordinator-only/collected-responses.csv), 관찰과 확인 과정은 [응답 기록](coordinator-only/response-notes.md)에 남겨요. 다른 평가자가 보는 공개 package에는 응답을 넣지 않아요.

원본 sidecar의 `reviewStatus=awaiting-five-reviewers`를 유지해요. 응답이 모이면 원본 `blind/reviewer-package/blind-responses.csv`에 집계하고 검증된 key와 판정 함수를 사용해 결과를 갱신해요. `pending-review`는 별도 결과 기록이며 root manifest를 수정하지 않아요. 현재 기록은 R4 중간 산출물이고 최종 추천·R4 완료·제품 적용으로 간주하지 않아요.

## 팀 합의에 따른 선택 — 독립 평가와 구분해요

2026-09-07 사용자가 다른 네 명도 참여한 다수결 합의라고 설명하고 ‘팀 합의에 따른 선택’으로 기록하도록 요청했어요. 합의된 shape/natural/hotspot 답은 공개 그림 순서대로 **왼쪽/왼쪽/없음**, **오른쪽/오른쪽/없음**, **왼쪽/왼쪽/오른쪽**, **오른쪽/오른쪽/왼쪽**이에요.

팀 합의 기록은 완료했어요. 다만 개인별 득표수·상의 전 응답·전원 일치 여부는 확인하지 않았어요. 기존 CSV 네 행을 보존하되 다섯 명분으로 복제하거나 독립성이 검증된 응답으로 취급하지 않아요. 프로토콜 추천은 `pending-review`로 유지하고, 블라인드 통과·모델 채택·R4 전체 완료를 선언하지 않아요. [수집 이력과 후속 확인](coordinator-only/response-notes.md)을 함께 읽어요. 원본 manifest·key·공개 평가 package는 변경하지 않았어요.

## 재검증

보존한 분포 원본에서 아래 명령을 실행하면 14개 파일이 `OK`, 두 checksum 파일의 비교는 출력 없이 종료 코드 0이어야 해요.

```bash
cd docs/experiments/e001-map-star-location-distribution/results/2026-09-07-e001-v2/coordinator-only/raw
shasum -a 256 -c checksums.sha256
cmp checksums.sha256 verification-run1-checksums.sha256
```

성능·블라인드 sidecar는 각 `manifest.json`의 `dataChecksums`에 있는 모든 파일을 SHA-256으로 재검증했어요(각각 2개·7개). 응답지의 빈 행은 실제 응답으로 세지 않아요. v1 결과의 원본 다섯 checksum도 그대로 유지해요. 사람 평가 이후의 실제 앱 여러 격자·줌 화면 확인과 `be/dev` PR은 아직 하지 않았어요.
