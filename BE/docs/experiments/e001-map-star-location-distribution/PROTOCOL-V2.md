# E001-v2: 빈 경계 관측과 모델 추천을 분리해요

| 항목 | 값 |
| --- | --- |
| 상태 | 실행 전 사전등록안이에요. 현재 단위는 문서이며 runner는 아직 v1이에요. |
| 버전 | 프로토콜 `E001-v2`, 산출물 schema `2` |
| 승인 근거 | 2026-09-06 사용자가 평가 재설계 방향의 구체화와 진행을 요청했어요. |
| 기준 문서 | [E001-v1 사전등록](README.md), [재설계 이유](REDESIGN.md) |
| 보존할 증거 | [v1 결과](results/2026-09-06-e001-v1/README.md), 커밋 `50183e1` |

**빈 테두리에 가상의 별을 더해 탈락시키지 않아요. 대신 후보를 고르는 검사와 자연스러움을 확인하는 평가를 구분해요.** 이 문서는 v1의 변경 계약이에요. 명시적으로 대체한 항목 외에는 v1의 식·연산 순서·파라미터·검증 규칙을 그대로 적용해요. v1 문서와 결과를 소급 수정하지 않아요.

## 유지하는 것과 바꾸는 것

| 유지해요 | 바꿔요 |
| --- | --- |
| 클라이언트 300m 격자 중심, 후보 반경 `0 ≤ r < 300m` | 빈 띠에 `0.5`를 더한 `edgeRatio30`을 탈락 기준에서 빼요. |
| D의 σ 100m·120m, taper, E의 36개 조합과 sampler | 실제 개수·점유율·관측 상태를 별도 진단 자료로 남겨요. |
| 최초 저장 시 한 번 정하는 위치, 시간축 없는 field | E 실패 시 D 자동 선택을 없애고 D도 A 대비 시각 평가를 받아요. |
| 거리·생성 무결성·밀도·반복 지표의 나머지 기준 | 새 seed·합성 중심·버전·출력 경로로 다시 평가해요. |

제품 코드·DB·API·ADR은 바꾸지 않아요. 밝기 감쇠·조회 만료·줌별 크기·기존 별 재배치는 이번 실험 범위 밖이에요. 추천이 나와도 팀 합의와 후속 ADR 전에는 현재 제품 계약을 변경하지 않아요.

## 1. 경계는 관측한 그대로 기록해요

### 띠와 값의 정의

띠 위치와 면적은 v1 그대로예요. A는 `d=150-max(abs(dx),abs(dy))`, B~E는 `d=300-r`이에요. 여러 중심 시나리오에서도 각 점의 자기 입력 중심을 기준으로 계산해요.

| 항목 | 정의 |
| --- | --- |
| `N` | 해당 행에 포함된 실제 생성 좌표 수예요. 요청 수는 별도로 기록해요. |
| `O` | `0 ≤ d < 30`인 바깥 띠 개수예요. 원형 후보에서는 `270 < r < 300`이에요. |
| `I` | `30 ≤ d < 60`인 안쪽 띠 개수예요. 원형 후보에서는 `240 < r ≤ 270`이에요. |
| 면적 | A는 `outerArea=32400`, `innerArea=25200`; B~E는 각각 `StrictMath.PI×17100`, `StrictMath.PI×15300`이에요. |
| 값 | `outerShare=O/N`, `innerShare=I/N`, `rawDensityRatio=(O/outerArea)/(I/innerArea)`예요. |

개수는 정수로 세고 비율의 나눗셈은 double로 해요. `r=300`은 띠에 넣기 전에 생성 무결성 위반이에요. 반경·좌표가 유효하지 않으면 관측 통계로 덮어 정상 처리하지 않아요.

### 관측 상태

| `observation_status` | 조건 | 밀도비 |
| --- | --- | --- |
| `invalid-sample` | 포함된 좌표에 non-finite 또는 support 위반이 있어요. | 비워요. 개수 O·I와 점유율도 비워요. |
| `no-samples` | 생성 좌표가 0개예요. | 비워요. O·I는 0, 점유율은 비워요. |
| `empty-bands` | `N>0, O=0, I=0`이에요. | 비워요. 두 점유율은 0이에요. |
| `outer-only` | `N>0, O>0, I=0`이에요. | 비워요. 무한대나 임의의 큰 값으로 대체하지 않아요. |
| `inner-observed` | `N>0, I>0`이에요. | 보정 없이 계산해요. O가 0이면 관측 밀도비는 0이에요. |

상태는 위에서 아래 순서로 판정해요. `inner-observed`는 계산 가능하다는 뜻이지, 표본이 충분하다는 뜻이 아니에요. 모든 행의 `interpretation`은 `descriptive-only`로 기록해요. 관측 수에 따른 임의의 “충분” 합격선이나 신뢰구간을 도입하지 않아요. 한 점의 비율도 숫자는 남기되, 충분한 근거라고 표시하지 않아요.

**이 자료는 어느 상태든 단독 합격·탈락에 쓰지 않아요.** `outer-only`도 숨기지 않고 결과와 그림에서 확인하지만 새로운 hard gate로 만들지는 않아요. 반대로 거리 위반·생성 누락 같은 기존 무결성 실패는 그대로 탈락시켜요. 진단 값이 비어 있다는 이유로 필수 지표의 누락까지 허용하면 안 돼요.

별이 0개 관측된 띠의 실제 확률이 0이라고 주장하지 않아요. A와 원형 후보는 서로 다른 경계를 사용하므로 점유율·밀도비만으로 직접 순위를 매기지 않아요. B 균등 원도 높은 바깥 점유율을 갖지만 이 자료만으로 경계가 눈에 띄는지를 증명하지 않아요. 모든 실행 plan의 요청 수는 양수이므로 `no-samples`는 관측 상태와 별개로 생성 개수 불일치에 의한 무결성 실패예요.

### 기록 단위

새 `boundary-observations.csv`에 모든 실행된 trial의 seed별 다섯 행과 pooled 한 행을 기록해요. pooled 행의 `sample_seed`는 비워요. pooled 값은 합산 후 다시 계산하며 seed별 비율의 평균을 쓰지 않아요. 요청 수는 해당 plan의 seed별 또는 전체 요청 수이고, N은 같은 범위의 실제 생성 수예요. 실패로 일부만 생성돼도 요청 수와 N의 차이가 드러나야 해요.

```text
protocol_version,phase,model_id,parameter_set_id,scenario_id,sample_seed,
requested_count,generated_count,outer_count,inner_count,
outer_area_m2,inner_area_m2,outer_share,inner_share,raw_density_ratio,
observation_status,interpretation
```

식별자 열 순서로 정렬하고 UTF-8·LF·기존 double 표현을 사용해요. 비어 있는 값은 빈 CSV 필드예요. `metrics.csv`에서는 `edgeRatio30`을 제거하며 새 경계 자료를 `E001MetricSet`의 NaN 값으로 우회해서 넣지 않아요. 나머지 필수 metric은 계속 누락·undefined·non-finite일 때 해당 gate를 실패시켜요.

## 2. 수치 판정과 최종 추천을 분리해요

### D와 E의 진입 조건

D의 튜닝·두 단일 중심 확인 조건에서 `edgeRatio30` 조건만 제거해요. D의 p95 median ≤210m, p99 median ≤250m, 모든 seed의 p99 ≤270m와 생성 무결성은 유지해요. 튜닝에서 σ120이 통과하면 D*로 고정하고, 아니면 σ100을 검사해요. 둘 다 실패하면 `inconclusive / no-d`예요.

E의 튜닝·두 단일 중심 확인 조건에서도 `edgeRatio30_E` 대 D 비교만 제거해요. `radialKs` median ≤0.05, A 대비 `a4` ≤0.5배, 튜닝 `seam300` median 20% 이상 감소·5 seed 중 4개 개선, pooled 밀도 3종과 E 정렬 순서는 v1을 유지해요. E의 두 spectral profile에서 `grid300_E ≤ 0.8×grid300_D`도 유지해요.

E와 D의 거리 차이 제한이 D의 거리 quantile 자체를 보장하는 것은 아니에요. v2는 기존 D 거리 기준과 E 상대 거리 기준을 유지하는 실험이며, 두 모델 모두의 실제 거리 수치를 결과에 공개해요. 자동으로 더 강한 위치 보장을 주장하지 않아요.

### D만 남아도 반복 무늬를 확인해요

확인 단계를 통과한 D*가 있으면 E 유무와 관계없이 A·D의 두 spectral 시나리오를 실행해요. E가 확인 단계에서 탈락했다면 해당 E의 spectral은 생략해요. A 또는 D가 spectral에서 무결성을 실패하면 `inconclusive / integrity-failure`예요. D의 두 `grid300` 중 하나가 정의되지 않으면 `inconclusive / no-d`예요.

A 대비 D의 `grid300`에는 새로운 비율 합격선을 만들지 않아요. A는 균등 격자를 빈틈없이 채워 300m 성분이 작아질 수 있으므로, 이 값을 단독 자연스러움 순위로 쓰지 않아요. A·D의 두 profile 값을 진단 결과로 함께 보여줘요. A의 spectral 좌표 무결성은 필수지만 A의 `grid300`이 undefined인 것은 진단 값 누락으로 기록하며 별도 gate로 쓰지 않아요. E만 spectral gate를 실패하면 D 시각 평가로 진행해요.

spectral 뒤에는 아래 `REVIEW_AD`의 A/D 전용 네 시나리오를 생성해요. A와 D의 생성 무결성, D의 두 단일 중심 시나리오의 기존 거리 quantile gate를 다시 확인해요. 무결성 실패는 `inconclusive / integrity-failure`, D 거리 실패는 `inconclusive / no-d`로 종료해요. 이 확인으로 σ를 바꾸지는 않아요. 아래 review-ready 상태는 이 추가 확인까지 끝났을 때만 부여해요.

| 분포 종료 상태 | 이유 | 의미 |
| --- | --- | --- |
| `inconclusive` | `no-d`, `integrity-failure` | 다음 평가로 넘길 D가 없어요. 성능·블라인드를 만들지 않아요. |
| `d-review-ready` | `no-e`, `e-confirmation-failed`, `e-spectral-failed` | D만 다음 평가의 후보예요. D가 채택됐다는 뜻은 아니에요. |
| `e-review-ready` | `e-review-pending` | D와 E가 다음 평가의 후보예요. E가 채택됐다는 뜻은 아니에요. |

v2에서는 `selected-d`를 쓰지 않아요. 단계를 종료할 때 무결성 검사 우선순위는 v1을 유지해요. 예를 들어 A의 무결성 실패를 D 후보 존재만으로 우회하지 않아요.

### 성능과 블라인드

두 review-ready 상태 모두 D를 측정해요. `e-review-ready`이면 E도 함께 측정해요. warmup 5회·measurement 10회·각 100,000점, 순서 교대·시간식·sink·재시도 제한은 v1 그대로예요. D와 E 모두 measurement median ≤100,000ns/점, max ≤250,000ns/점을 요구해요. D도 실제 도입 대상이므로 같은 생성 시간 예산을 적용해요. D만 있으면 각 batch에서 D만 실행해요.

D가 성능 조건을 실패하거나 허용된 재시도 뒤에도 성능 실행 자체가 유효하지 않으면 최종 결과는 `inconclusive`이고 블라인드를 만들지 않아요. 유효한 측정에서 D는 통과하고 E만 조건을 실패하면 A/D 평가만 진행해요. E 성능 실패를 D의 시각 합격으로 취급하지 않아요.

블라인드에는 아래 비교 family를 사용해요. 각 family는 같은 네 조건(단일·3×3 × 500·5,000개)이지만 서로 다른 합성 중심과 point seed domain으로 생성해요. v1의 sprite·화면 크기·축척을 유지해요.

| family ID | 기준 모델 | 도전 모델 | 생성 조건 |
| --- | --- | --- | --- |
| `a-d` | A | D* | D가 수치와 성능 조건을 통과했어요. `REVIEW_AD`의 전용 표본을 써요. |
| `d-e` | D* | E* | 추가로 E도 수치와 성능 조건을 통과했어요. `HOLDOUT`의 confirmation 표본을 써요. |

v1의 shape·natural·hotspot 질문을 그대로 사용해요. 각 family에서 도전 모델의 shape 표 ≥4명이 네 화면 중 세 화면 이상이고, natural 표 ≥4명도 네 화면 중 세 화면 이상이어야 해요. 어떤 화면에서도 도전 모델 hotspot 표가 2명 이상이면 실패해요. tie는 득표로 세지 않아요. 이는 기존 내부 선택 기준을 D 대 A에도 적용한 운영 기준이며, v1 D 그림에 맞춰 새 숫자를 추정한 것이 아니에요.

한 번의 batch에 4쌍 또는 8쌍을 넣고 동일한 다섯 명이 서로 상의하지 않고 전부 답해요. `a-d`와 `d-e`를 사용한다는 이름, 모델명과 좌우 key는 평가자에게 숨겨요. package에는 공개 pair ID만 사용하고 family 열을 넣지 않아요. 두 family에 같은 D 좌표·패널을 재사용하지 않아요. 같은 그림을 찾아 좌우 mapping을 복원하는 문제를 막기 위한 조건이에요. 다만 분포 모양으로 모델을 추측할 가능성까지 없애는 완전한 맹검이라고 주장하지 않아요.

하나의 16byte nonce로 family별 `assignment|familyId|scenarioId`, `pair-id|familyId|scenarioId` HMAC domain을 사용해요. 여기서 scenarioId는 각 family가 사용하는 실제 생성 시나리오 ID예요. 각 family 안에서 기존 unsigned digest 정렬로 도전 모델을 왼쪽 두 장·오른쪽 두 장에 배치해요. 공개 pair ID는 batch 전체에서 중복이 없어야 하고 충돌 시 전체 nonce를 다시 만들어요. 공개 목록은 pair ID 오름차순이며 family별로 묶지 않아요.

공개 `blind-pairs.csv`의 `scenario_id`에는 family·origin을 드러내는 `review-ad-*`나 `confirmation-*`를 쓰지 않아요. 두 family 모두 대응하는 `single-n500`, `single-n5000`, `grid-equal-n500-per-center`, `grid-equal-n5000-per-center`라는 조건 ID만 써요. 공개 ballot·이미지 파일명은 기존처럼 pair ID만 참조해요. 비공개 key의 열은 `nonce_hex,pair_id,family_id,scenario_id,left_model_id,right_model_id`이며 여기에는 실제 생성 scenarioId를 기록해요.

모든 응답(4쌍이면 20행, 8쌍이면 40행)을 받기 전에는 어느 family의 key도 공개하지 않아요. 누락·중복·잘못된 응답은 판정을 보류해요. 노출되면 부분 family가 아니라 4쌍 또는 8쌍 전체 batch를 무효화하고 보존해요. 팀 내부 비교이며 일반 사용자 선호를 입증하지 않아요.

### 최종 추천 규칙

| 조건 | `recommendation` |
| --- | --- |
| D 수치·성능·A 대비 블라인드 중 하나라도 실패 | `inconclusive` |
| D가 모두 통과하고 E가 없거나 수치·성능·D 대비 블라인드 실패 | `recommend-d` |
| D가 모두 통과하고 E도 수치·성능·D 대비 블라인드 통과 | `recommend-e` |
| 필요한 사람 응답·유효한 측정이 아직 없어요. | `pending-review` |

확정 실패가 있으면 응답 부족보다 실패를 먼저 반영해요. 단, 블라인드의 확정 실패는 전체 batch의 완전하고 유효한 응답을 검증한 뒤에만 계산해요. 부분 응답으로 한 family의 실패를 먼저 공개하지 않아요. 성능 재시도 한도 소진은 앞 절대로 `inconclusive`이며 측정 대기와 구분해요. E가 D보다 낫다는 표만으로 추천되지 않도록 D의 A 대비 자격도 요구해요. 다만 서로 다른 표본에서의 D>A와 E>D가 E>A를 직접 검증하거나 선호의 추이성을 보장하지는 않아요. E>A 직접 비교는 이번 사전등록의 주장이 아니에요. 이 보수적인 규칙으로 E가 단독으로는 유망해도 이번 실험에서 추천되지 않을 수 있어요. 확인 결과를 본 뒤 다른 E나 다른 σ의 D로 바꿔 재도전하지 않아요.

추천은 사람이 근거 파일과 family별 판정 값을 확인해 결과 문서에 기록하며 root manifest는 수정하지 않아요. 판정 함수는 위 표를 그대로 반환하고, 사람이 표와 다른 추천을 하려면 이번 프로토콜 결과가 아닌 별도 결정으로 표시해요. D/E 어느 쪽도 자동으로 제품에 적용하지 않아요. 이후 실제 앱에서 여러 격자·줌의 합성 화면을 확인하는 기존 PR 전 절차도 유지해요.

## 3. 새 표본과 보존 계약

| 용도 | 합성 중심 `(easting, northing)` |
| --- | --- |
| `CAL` | `(971850, 1969950)` |
| `HOLDOUT` | `(980850, 1969950)` |
| `SPECTRAL` | `(971850, 1978950)` |
| `REVIEW_AD` | `(989850, 1969950)` |

모두 `coordinate mod 300 = 150`이에요. v1의 중심·산점도 표본을 재사용하지 않아요. sample seed는 `2026090601`, `2026090602`, `2026090603`, `2026090604`, `2026090605`로 고정해요. point seed, spectral-profile, performance의 canonical 문자열 접두사는 모두 `E001-v2`로 바꿔요. field seed `0x5048454545455701`과 profile seed `0xE001300000000001`은 유지하고, spectral-profile의 버전 domain만 바꿔 새 가중치를 만들어요.

기존 시나리오 ID·표본 수·CAL field calibration 위치 간격·개수 배분 방법은 v1 그대로예요. 추가 A/D 비교에는 `phase=review`, `originId=REVIEW_AD`를 사용하고 아래 네 시나리오를 추가해요. 각각 대응하는 confirmation plan과 같은 개수·배분·viewport를 사용하되 point seed 문자열의 scenarioId와 originId는 아래 새 값을 써요. 같은 family 안의 A/D는 공통 난수를 사용하고, D/E family의 D 표본과는 분리해요.

| 추가 `scenarioId` | 모델별 개수 |
| --- | --- |
| `review-ad-single-n500` | 500 |
| `review-ad-single-n5000` | 5,000 |
| `review-ad-grid-equal-n500-per-center` | 4,500 |
| `review-ad-grid-equal-n5000-per-center` | 45,000 |

A/D 추가 요청은 `2×(500+5,000+4,500+45,000)=110,000`개예요. E가 최종 spectral과 A/D 전용 확인까지 통과하는 정상 경로는 `833,000+110,000=943,000`개예요. E 튜닝 전 D가 없으면 15,000개에서 끝나고, E 튜닝은 끝났지만 통과 후보가 없을 때 나머지 단계가 모두 유효하면 `195,000+220,000+242,000+110,000=767,000`개예요. 도중 실패 경로는 실제 실행한 plan 요청 수를 합산하고 정상 경로 수를 강제하지 않아요. conformance와 performance 호출은 이 수에서 제외해요.

v2 공식 경로는 `build/reports/experiments/e001-v2/`예요. staging·previous·invalidated·lock도 각각 기존 `e001` 접두사를 `e001-v2`로 바꿔 분리해요. v2 실행이 기존 `e001/`을 이동·무효화·덮어쓰면 안 돼요. 기존 세 Gradle task 이름은 유지하되, v2 runner로 전환한 뒤에는 v2만 생성해요. v1 재현이 필요하면 v1 커밋의 별도 checkout에서 실행해요.

분포를 두 번 실행해 결정적 checksum이 같을 때만 승격하는 규칙, atomic move와 복구 규칙은 그대로예요. `boundary-observations.csv`를 필수 checksum 파일에 추가해요. `d-review-ready`에서는 REVIEW_AD의 A·D 패널 8개와 D conformance 320개를, `e-review-ready`에서는 그 8개에 HOLDOUT의 D·E 패널 8개를 더한 총 16개와 D·E conformance 640개를 기록해요. `inconclusive`이면 패널은 없고 conformance는 header만 기록해요. B·C는 수치 대조군으로 유지하며 블라인드 추천 대상이 아니에요.

`protocolSha`는 이 파일의 최종 커밋을, `runnerSha`는 v2 실행 소스의 최종 커밋을 가리켜요. root manifest에 `baseProtocolBlobId`로 동결된 v1 README의 Git blob ID도 기록해요. clean-source 검사에는 이 파일과 v1 README, 기존 runner source 목록을 포함해요. sidecar는 버전·schema·SHA·필수 파일·outcome을 확인하며 v1 source를 v2로 읽지 않아요.

프로토콜·runner가 각각 커밋되고 소스가 깨끗하기 전에는 실제 v2 실행이나 후보 산점도를 생성하지 않아요. 커밋 전에는 아래 고정 fixture와 단위 테스트만 사용해요. 실행 후 기준 변경이 필요하면 v3로 분리하고 v2의 원래 결과와 변경 이유를 보존해요. 이전 버전이 있다는 이유만으로 유효했던 실행을 잘못된 실행이라고 표시하지 않아요.

## 4. 구현 전에 고정하는 검증 예제

아래 개수 조합은 난수로 뽑지 않는 fixture예요. 원형 띠 fixture의 O에는 `(285,0)`, I에는 `(255,0)`, 나머지에는 `(0,0)`을 사용해요. 중복 좌표는 개수 검사 용도이며 자연스러운 분포의 예시가 아니에요.

| fixture | 기대 결과 |
| --- | --- |
| `N=100, O=0, I=0` | `empty-bands`, 두 share 0, ratio 빈 값; 이 사실만으로 D/E를 탈락·합격시키지 않아요. |
| `N=100, O=0, I=1` | `inner-observed`, ratio 0, inner share 0.01; 충분한 표본이라는 상태를 만들지 않아요. |
| `N=100, O=1, I=0` | `outer-only`, outer share 0.01, ratio 빈 값이에요. |
| `N=100, O=19, I=17` | share 0.19·0.17, raw ratio 약 1이에요. 이는 균등 원의 띠 면적 비율을 맞춘 산술 fixture이지 실제 B 실행 결과가 아니에요. |
| `N=100, O=80, I=1` | raw ratio 약 71.578947, outer share 0.8이에요. 경계 집중이 진단에서 사라지지 않아야 해요. |

추가 경계 검사는 `r=240` 제외, `r=270` I 포함, `r=299` O 포함, `r=300` 무결성 실패를 확인해요. A의 정사각형 면적·띠 판정은 `(dx,dy)=(135,0)` O, `(105,0)` I, `(90,0)` 양쪽 제외로 확인해요. N=0, 생성 누락, invalid-sample과 pooled 재계산도 검사해요.

선택 흐름 fixture에서는 경계 자료만 바꿔도 다른 필수 지표가 같으면 후보 판정이 같아야 해요. 반면 필수 radius·radialKs·밀도·spectral 값 누락은 기존처럼 실패해야 해요. no-e에서도 A/D spectral을 실행하고, D 확인 실패에서는 실행하지 않으며, 어느 경로에서도 `selected-d`를 만들지 않는지 검사해요.

산출물 검사는 v1 source 거부, v1 디렉터리 불변, 새 CSV checksum 누락 거부, 두 실행 일치, 8/16 패널과 320/640 conformance 분기, sidecar 복구·재시도 제한을 포함해요. 블라인드는 4/8쌍, family별 2:2, batch 전체 pair ID 유일성, 20/40행 완전성, 전체 노출 무효화와 D 자격 없는 E 추천 거부를 검사해요. 두 family의 D가 서로 다른 scenario·origin·point seed를 사용하고 같은 좌표 파일이나 panel을 참조하지 않는지도 확인해요. 성능 시간 판정은 fake clock으로 검사하고 CI에서 실제 wall-clock 합격을 요구하지 않아요.

## 5. 커밋 단위와 진행 경계

경로는 저장소의 `BE/` 기준이에요. 각 단위는 검증 후 커밋 전에 멈추며, 실제 실행은 마지막 실행기 단위의 커밋 뒤에만 해요. 아직 미래 단위 코드를 작성하지 않아요.

| 단위 | 완료할 범위 | 검증·제안 커밋 |
| --- | --- | --- |
| R1 — 지금 | `docs/experiments/e001-map-star-location-distribution/REDESIGN.md`와 이 사전등록안을 작성해요. 승인된 방향만 옵시디언 결정 기록에 남겨요. | 링크·식·개수·상태 전이와 v1 불변 검토. `docs: 지도 별 위치 E001 v2 평가 기준 사전등록` |
| R2 — 지표·분포 흐름 | `src/test/java/com/pheeeew/sigh/experiment/e001/`에 별도 경계 관측 값·집계를 만들고 `E001ShapeMetrics`, `E001Selection`, `E001Distribution`, 추가 REVIEW_AD plan을 담는 `E001Scenario`와 대응 테스트를 바꿔요. 새 상태를 소비하는 artifact·conformance 분기는 같은 단위에서 호환시켜 빌드가 깨지는 중간 단위를 만들지 않아요. v2 source 판별이 완성되기 전 실제 runner 실행은 명시적으로 차단해요. | 아래 targeted test가 모두 통과하고 실제 runner 미실행을 확인해요. `feat: 지도 별 경계 관측과 후보 판정 분리` |
| R3 — v2 실행 계약 | 같은 패키지의 seed·scenario·artifacts·checksums·context·store·performance·blind·runners 및 대응 테스트, `docs/experiments/e001-map-star-location-distribution/RUNNING.md`를 이 문서대로 전환해요. 필요하면 실행기 단위는 시작 전에 안전한 하위 단위로 다시 나눠요. | targeted test와 세 task의 개별 dry-run. `feat: 지도 별 위치 E001 v2 실행 및 평가 연결` |
| R4 — 실제 실행·결과 | 커밋된 v2 runner로 분포 두 실행을 검증해요. 상태가 허용할 때 성능·블라인드를 진행하고, 결과와 합성 좌표 그림을 보존해요. 응답 수집 전에는 후보명을 드러낸 holdout 그림을 평가자에게 공개하지 않아요. | 원본 checksum·개수·그림·응답을 검증해요. `docs: 지도 별 위치 E001 v2 실행 결과 기록` |

R2·R3의 기본 확인 명령은 다음과 같아요. 일반 단위 테스트만 실행되고 실험 산출물은 생기지 않아야 해요.

```bash
./gradlew test --tests 'com.pheeeew.sigh.experiment.e001.*Test' --no-daemon
```

R3의 태그 격리는 아래 명령을 **각각 따로** 실행해요. 각 명령은 대응 runner 하나를 선택해 dry-run으로 skip해야 해요. 세 task를 한 명령에 붙이고 마지막에만 `--test-dry-run`을 쓰지 않아요.

```bash
./gradlew e001LocationDistribution --test-dry-run --rerun-tasks --no-daemon
./gradlew e001LocationPerformance --test-dry-run --rerun-tasks --no-daemon
./gradlew e001BlindReview --test-dry-run --rerun-tasks --no-daemon
```

이번 문서 단위는 제품·실행 코드와 기존 증거를 변경하지 않아요. 구현 중 모순이 드러나면 실행하지 않고 사전등록안을 수정·검토·커밋해요. 이후 실제 표본을 생성한 뒤의 수정은 새 버전에서만 진행해요. 운영 반영·push·PR은 각 단계의 별도 승인을 따라요.
