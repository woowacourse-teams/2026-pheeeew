# E001: 지도 별 위치 분포 비교 사전등록

| 항목 | 값 |
| --- | --- |
| 상태 | 실행 전 |
| 프로토콜 버전 | `E001-v1` |
| 관련 이슈 | [#241](https://github.com/woowacourse-teams/2026-pheeeew/issues/241) |
| 현재 제품 결정 | [ADR-0002](../../adr/0002-use-grid-center-for-sigh-location.md) |
| 좌표계 | 거리 계산 `EPSG:5179`, 저장·표시 `EPSG:4326` |

이 문서는 결과를 본 뒤 유리한 seed, 파라미터 또는 판정 기준을 고르는 일을 막기 위한 사전등록 프로토콜이에요. 이 파일이 커밋되기 전에는 E001을 실행하지 않아요.

## 질문과 판정 대상

클라이언트의 300m 격자 중심 계약을 유지할 때, `tapered Gaussian + 전역 결정적 다중 스케일 강도장`이 단순 `tapered Gaussian`보다 사각형·원형 외곽과 300m 격자 반복을 의미 있게 줄이면서 위치 의미와 생성 비용을 지킬 수 있나요?

주 판정은 동일한 `R`, `σ`, 입력 중심별 개수와 화면 조건을 사용하는 다음 두 모델 사이에서만 수행해요.

- `D`: tapered Gaussian
- `E`: D의 거리 커널에 전역 결정적 다중 스케일 강도장을 결합한 모델

현재 균등 정사각형, 균등 원과 절단 Gaussian은 문제와 경계 효과를 설명하기 위한 대조군이에요. 대조군이 더 좋아 보여도 자동 채택하지 않고 실험을 `inconclusive`로 끝낸 뒤 별도 결정을 요청해요.

## 고정 계약

- 클라이언트는 실제 위치를 `EPSG:5179`의 `300m × 300m` 격자 중심으로 바꾸고 실제 위치를 폐기해요.
- 신규 후보 모델의 최대 표시 반경은 `R=300m`, 즉 지름 600m예요.
- 신규 후보 모델은 항상 `0 <= r < 300m`를 만족하며 반경 밖 좌표를 원둘레에 clamp하지 않아요.
- 한숨 1개가 별 1개를 만들며 최종 위치는 최초 저장 후 다시 추첨하지 않아요.
- 실제 위치에서 표시 위치까지의 보수적 상한은 `150√2m + 300m ≈ 512.1m`예요.
- 시간에 따른 밝기·조회 만료, 줌·밀도별 크기, clustering, 음량별 위치 변경과 기존 별 재배치는 범위에 포함하지 않아요.

E001 결과와 팀 합의로 후속 ADR이 채택되기 전까지 ADR-0002의 현재 균등 정사각형 분포가 제품 계약이에요.

## 결과 확인 전 고정 규칙

1. 이 프로토콜과 실험 runner가 각각 커밋되기 전에는 E001 산출물을 생성하지 않으며, 실행 결과에는 두 커밋의 SHA를 기록해요.
2. 튜닝 단계에서는 숫자 지표만 사용하고 렌더 이미지를 열지 않아요.
3. 확인 단계의 좌표·이미지·블라인드 키는 최종 후보를 고정한 뒤 한 번에 생성해요.
4. 첫 결과를 생성한 뒤 프로토콜이나 runner 코드를 변경하면 버전을 올리고 변경 이유를 기록한 뒤 전체 실험을 다시 실행해요. 이전 결과는 삭제하지 않고 `invalidated`로 보존해요.
5. 승리 모델이나 파라미터를 정하지 못하면 합격선을 낮추지 않고 `inconclusive`로 종료해요.

## 합성 좌표와 난수

운영 위치나 사용자 데이터를 사용하지 않아요. 모든 기준점은 300m 격자 중심 조건인 `coordinate mod 300 = 150`을 만족하는 합성 `EPSG:5179` 좌표예요.

| 용도 | ID | 중심 `(easting, northing)` |
| --- | --- | --- |
| 파라미터 튜닝 | `CAL` | `(953850, 1951950)` |
| 최종 확인 holdout | `HOLDOUT` | `(962850, 1951950)` |
| 11×11 격자 스트레스 holdout | `SPECTRAL` | `(953850, 1960950)` |

3×3 조건은 각 기준점에 `(300i, 300j)`, `i,j ∈ {-1,0,1}`을 더해 만들어요. 11×11 조건은 `i,j ∈ {-5,...,5}`를 사용하고 중앙 9×9 영역만 평가하여 바깥 경계 효과를 막아요.

표본 난수 seed는 다음 다섯 개로 고정해요.

```text
2026090301
2026090302
2026090303
2026090304
2026090305
```

`n=500`은 seed마다 중심별 100개, `n=5,000`은 seed마다 중심별 1,000개로 나눠요. 모델과 파라미터 조합은 같은 point seed를 사용해요.

point seed는 아래 UTF-8 문자열의 SHA-256 앞 64bit를 big-endian long으로 읽어 만들어요. 모델 ID와 파라미터 ID는 넣지 않아 공통 난수를 사용하고, 중심과 point index를 넣어 격자마다 같은 상대 모양이 복제되지 않게 해요.

```text
E001-v1|scenarioId|originId|centerI|centerJ|sampleSeed|pointIndex
```

각 point seed에서 시작하는 PRNG는 Unit 2에서 명세와 테스트를 함께 제공할 `SplitMix64-v1`을 사용해요. `[0,1)` double은 `nextLong`의 상위 53bit에 `2^-53`을 곱해 만들어요. 필드 seed는 표본 seed와 분리하여 다음 값으로 고정해요.

```text
fieldSeed = 0x5048454545455701
```

## 비교 모델

거리 `r`은 입력 격자 중심과 후보 위치 사이의 유클리드 거리예요. 모든 원형 모델은 면적 균등 후보를 만들 때 `r=R√U`, `θ=2πV`를 사용해요.

| ID | 모델 | 확률 또는 범위 |
| --- | --- | --- |
| `A` | 현재 균등 정사각형 | `dx,dy ~ Uniform[-150m,150m)` |
| `B` | 균등 원 | `r < 300m`에서 면적 균등 |
| `C` | 절단 Gaussian | `exp(-r²/2σ²) × 1[r<300m]` |
| `D` | tapered Gaussian | `exp(-r²/2σ²) × T(r/300m)` |
| `E` | 강도장 결합 | `D × exp(βF(x,y))` |

taper는 후보군으로 두지 않고 다음 식으로 고정해요.

```text
u = r / 300m
T(u) = 1 - 10u³ + 15u⁴ - 6u⁵  (0 <= u < 1)
T(u) = 0                       (u >= 1)
```

### 튜닝 후보군

먼저 `D`의 `σ ∈ {100m, 120m}` 두 개를 비교해 아래 선택 규칙으로 `D*`를 고정해요. 그다음 `E`는 `D*`의 `σ`를 그대로 사용하고 나머지 항목의 전체 Cartesian product 36개를 비교해요.

| 항목 | 후보 |
| --- | --- |
| `σ` | `D*`와 같은 값으로 고정 |
| noise | `gradient-quintic-v1`, `value-quintic-v1` |
| field profile `P1` | 길이 `(120m,360m,1080m)`, 가중치 `(0.40,0.35,0.25)` |
| field profile `P2` | 길이 `(180m,540m,1620m)`, 가중치 `(0.20,0.35,0.45)` |
| `β` | `0.4`, `0.7`, `1.0` |
| sampler | `sir-16`, `sir-32`, `rejection-128` |

octave 회전은 각각 `17°`, `71°`, `137°`로 고정해요. 각 noise octave는 문서화된 구현 경계로 `[-1,1]`에 정규화하고, profile 가중치의 합을 1로 유지하여 `F(x,y) ∈ [-1,1]`이 되게 해요. 이 규칙으로 noise 구현별 진폭 차이가 `β`의 숨은 튜닝 변수가 되지 않게 해요.

Unit 2는 두 noise와 세 sampler의 정확한 알고리즘 및 field 정규화 경계를 golden test로 고정해야 해요. 이 runner 커밋 전에는 결과를 생성하지 않아요. `rejection-128`은 `exp(β(F-1))`을 acceptance로 사용하며 128회 실패를 조용히 fallback하지 않고 실패 횟수와 함께 결과에 기록해요. 실행 중 파라미터를 자동 탐색하거나 연속 최적화하지 않아요.

## 실험 단계와 표본 수

### 1. 튜닝

- 기준점: `CAL`
- 조건: 단일 중심 500개와 고정 불균형 3×3 전체 4,500개
- 반복: 다섯 sample seed에 균등 분할
- 조합: D 2개 + E 36개
- 총 출력: `38 × 5,000 = 190,000`개

불균형 3×3은 아래 비율을 사용하고 총 4,500개가 되도록 largest-remainder 방식으로 정수 배분해요. 행은 북쪽에서 남쪽, 열은 서쪽에서 동쪽 순서예요.

```text
0.55  0.85  0.40
0.75  2.40  1.10
0.30  0.95  1.70
```

튜닝에서는 이미지를 만들지 않아요. hard gate를 통과한 설정을 수치 규칙으로 정렬해 `D*`와 `E*` 하나씩 고정해요.

### 2. 최종 확인

- 기준점: 튜닝에 사용하지 않은 `HOLDOUT`
- 모델: `A`, `B`, `C`, `D*`, `E*`
- 조건: 단일 중심과 균등 3×3
- 표본: 각 중심당 500개·5,000개, 다섯 seed에 균등 분할
- 총 출력: `5 × (1+9) × (500+5,000) = 275,000`개

`C`는 `D*`와 같은 `σ`를 사용해요. 확인 결과로 `D*`나 `E*`의 파라미터를 다시 조정하지 않아요.

### 3. 300m 격자 스트레스

- 기준점: `SPECTRAL`
- 모델: 문제 기준인 `A`와 주 판정 대상 `D*`, `E*`
- 생성 영역: 11×11 중심, 평가 영역: 중앙 9×9
- 표본: 중심당 총 500개
- 중심별 개수 profile: 균등 profile과 고정 불균형 profile
- 총 출력: `3 × 121 × 500 × 2 = 363,000`개

불균형 profile은 각 중심 `(i,j)`에 대해 고정 profile seed와 SHA-256으로 `u∈[0,1)`을 만들고 `0.25+1.75u`를 가중치로 사용해요. 전체 개수는 60,500개가 되도록 largest-remainder 방식으로 배분해요.

```text
profileSeed = 0xE001300000000001
```

전체 E001은 튜닝 190,000개, 최종 확인 275,000개, 격자 스트레스 363,000개로 총 828,000개의 합성 좌표를 생성해요.

## 수치 지표

모든 비율은 seed별로 계산한 뒤 median과 범위를 함께 기록해요. 별 하나를 독립 반복으로 취급해 신뢰구간을 부풀리지 않아요.

| ID | 정의 | 용도 |
| --- | --- | --- |
| `radius` | p50·p95·p99·max 중심 거리와 반경 위반 개수 | 위치 의미와 hard bound |
| `edgeRatio30` | 경계에서 0~30m 띠의 면적당 밀도 ÷ 30~60m 띠의 면적당 밀도 | 단단한 사각형·원형 외곽 |
| `a4` | `sqrt(max(0, \|mean(exp(4iθ))\|²-1/N))` | 단일 중심의 4방향·사각형 성분 |
| `seam300` | 내부 300m 격자 경계 양쪽 15m strip의 면적당 밀도 차이를 pooled density로 나눈 값 | 불균형 3×3의 경계 이음새 |
| `grid300` | 고정 raster의 300m 축 방향 spectral power ÷ 같은 주파수 대역의 비축 방향 median power | 반복되는 격자 성분 |
| `radialKs` | 같은 `σ`의 D와 E 사이 경험적 반경 CDF의 KS 거리 | 필드가 거리 분포를 바꿔 이기는 현상 방지 |
| `proximity8/16/24` | 최근접 별의 고정 렌더 거리가 각각 8·16·24px 미만인 별의 비율 | 화면 근접 proxy |
| `neighbors10` | 점 하나의 10m 이내 평균 이웃 수 | 국소 과밀 |
| `hotspot50` | 50m cell count의 p99·max·변동계수 | 인공 hotspot |
| `cost` | 고정 batch의 좌표 1개당 생성 시간 | 순수 sampler 비용 |

`edgeRatio30`에서 A의 support는 300m 정사각형, B~E는 반경 300m 원이에요. 각 띠의 점 개수에 0.5를 더한 뒤 띠 면적으로 나누어 0개 조건을 처리해요. 서로 다른 support 사이의 값은 설명용으로만 사용하고 D와 E를 같은 support에서 비교해요.

`grid300`은 11×11에서 생성하고 중앙 9×9만 15m cell로 rasterize해요. 평균을 빼고 2차원 Hann window를 적용한 뒤 `f0=1/300m`의 네 축 방향 power 평균을 구해요. 분모는 `0.8f0 <= |f| <= 1.2f0`에서 축으로부터 15도 이내인 bin과 DC를 제외한 power median이에요. FFT crop에 의한 오판을 막기 위해 `grid300` 하나만으로 모델을 채택하지 않아요.

균등 정사각형은 같은 개수로 3×3을 채우면 내부를 빈틈없이 타일링하고 정확한 `1/300m` 성분이 sinc 영점에 놓일 수 있어요. 따라서 균등 조건의 낮은 `grid300`을 곧바로 자연스러움으로 해석하지 않고 `a4`, 불균형 조건의 `seam300`, 11×11의 축 방향 대역을 함께 봐요.

최근접 거리는 spatial hash 또는 kd-tree로 계산하고 작은 고정 fixture에서만 brute force 결과와 대조해요. `proximity`는 실제 앱의 줌별 아이콘 겹침률이 아니라 아래 고정 렌더에서의 근접 proxy로만 해석해요.

통계적 반복 단위는 개별 별이 아니라 `origin × sampleSeed`예요. 수십만 개 좌표를 독립 표본으로 간주한 t-test나 신뢰구간은 만들지 않아요.

## 고정 렌더와 블라인드 평가

- canvas: `1200×1200px`, DPR 1
- viewport: 기준 중심에서 동서·남북 각각 600m, 즉 `1m/px`
- background: `#11131A`
- 별: 지름 8px, `#FFD36A`, opacity 0.70
- 그리기 순서: `centerId`, `sampleSeed`, `pointIndex` 오름차순
- 격자선·중심점·모델명·파라미터는 이미지에 표시하지 않아요.

`HOLDOUT`의 단일·3×3과 500·5,000개 조합 네 장을 D*와 E*에 대해 생성해요. 다섯 개발자는 모델명과 좌우 순서를 숨긴 화면에서 각 장마다 다음 항목을 독립적으로 답해요.

1. 어느 쪽에서 사각형·원형·격자 모양이 덜 보여요? (`left/right/tie`)
2. 어느 쪽이 더 자연스러운 군집과 빈 공간으로 보여요? (`left/right/tie`)
3. 서비스에 부적절할 정도로 강한 hotspot이 있어요? (`left/right/both/neither`)

누락된 평가는 보충한 뒤 판정하며 5명 미만으로 결론을 내리지 않아요. 원본 응답과 모델 key를 결과에 함께 보존해요.

이 평가는 팀 내부 선택 근거이며 실제 사용자 대상 UX 검증이나 통계적으로 일반화할 수 있는 사용자 연구로 해석하지 않아요.

## 합격선과 선택 규칙

### Hard gate

- B~E의 모든 좌표가 finite이고 `0 <= r < 300m`이며 `r=300m`에 clamp된 좌표가 0개예요.
- 같은 프로토콜·runner·JDK로 두 번 실행한 결정적 산출물 checksum이 같아요.
- D의 seed별 중심 거리 median이 `p95 <= 210m`, `p99 <= 250m`이고 어떤 seed도 `p99 > 270m`가 아니에요.
- D의 `edgeRatio30`은 median 0.35 이하이고 어떤 seed도 0.50을 넘지 않아요.
- E 후보의 `radialKs`는 0.05 이하예요.
- E 후보는 `edgeRatio30_E <= max(1.10 × edgeRatio30_D, edgeRatio30_D + 0.02)`를 만족해요.
- E 후보의 `proximity16`은 D보다 3%p 넘게 높지 않고, `neighbors10`은 D의 1.25배를 넘지 않아요.
- E 후보의 `hotspot50.max`는 D의 `hotspot50.p99` 4배를 넘지 않아요.
- E 후보의 `a4`는 A의 0.5배 이하이고, `seam300` median은 같은 `σ`인 D보다 20% 이상 낮으며 다섯 seed 중 네 개 이상에서 개선돼요.

### 후보 정렬

먼저 `σ=120m`인 D가 hard gate를 통과하면 D*로 고정해요. 실패하면 `σ=100m`을 검사하고, 둘 다 실패하면 실험을 `inconclusive`로 끝내요. C와 E*는 D*와 같은 `σ`를 사용해요.

hard gate를 통과한 E 후보만 다음 순서로 정렬해요.

1. `β`가 낮은 후보
2. field 평가 횟수가 적은 sampler: `sir-16`, `sir-32`, `rejection-128` 순서
3. 좌표당 생성 시간이 낮은 후보
4. `seam300`이 낮은 후보
5. 남은 동률은 `parameterSetId` 사전순

### E 채택 조건

아래 조건을 모두 만족해야 E를 채택해요.

- 11×11의 균등·불균형 profile 모두에서 E*의 `grid300` median이 D*보다 20% 이상 낮아요.
- 최종 확인 네 화면 중 세 화면 이상에서 사각형·원형·격자 모양이 덜 보인다는 E* 표가 4명 이상이에요.
- 최종 확인 네 화면 중 세 화면 이상에서 더 자연스럽다는 E* 표가 4명 이상이에요.
- 어떤 화면에서도 E*에 대한 부적절한 hotspot 표가 2명 이상 나오지 않아요.
- pure sampler screening에서 좌표당 median이 100μs 이하이고 어떤 측정 batch도 250μs를 넘지 않아요.

하나라도 실패하거나 의미 있는 차이가 없으면 더 단순한 D*를 선택해요. D*도 hard gate를 통과하지 못하면 어떤 모델도 채택하지 않고 `inconclusive`로 종료해요.

pure sampler 시간은 warmup 5회와 측정 10회, 회당 100,000개 생성으로 측정해요. wall-clock 값은 환경 의존적이므로 checksum과 CI assertion에서 제외해요. 실제 저장 경로는 Unit 8에서 기존·신규 구현을 번갈아 측정하며 p95 증가가 10ms 이하이고 기존의 2.5배 이하인지를 별도로 확인해요. 넘으면 Java 좌표 변환 또는 DB 왕복 구조를 재검토하고 운영 전환하지 않아요.

## 실행과 기본 테스트 격리

Unit 2는 다음 opt-in 명령을 추가해요.

```bash
./gradlew e001LocationDistribution --no-daemon
```

- 전체 runner에는 JUnit tag `e001`을 붙여요.
- 기본 `test` task는 `e001`을 제외해요.
- `e001LocationDistribution` task만 `e001`을 실행해요.
- `./gradlew test`는 전체 시뮬레이션을 실행하거나 E001 산출물을 생성하지 않아요.
- 지표 계산과 결정성 검증을 위한 작은 단위 테스트는 기본 `test`에 포함해요.
- 실험 runner는 운영 DB와 네트워크에 접근하지 않아요.

## 산출물 계약

runner의 기본 출력은 Git에서 제외되는 `build/reports/experiments/e001/`이에요.

```text
build/reports/experiments/e001/
├── manifest.json
├── coordinates.csv.gz
├── metrics.csv
├── blind-review.csv
├── renders/
├── environment.json
└── checksums.sha256
```

`coordinates.csv.gz`는 다음 열을 고정 순서로 가져요.

```text
protocol_version,phase,model_id,parameter_set_id,scenario_id,
sample_size,sample_seed,center_id,point_index,
center_easting_m,center_northing_m,
offset_easting_m,offset_northing_m,radius_m
```

- 텍스트는 UTF-8·LF·`Locale.ROOT`을 사용하고 숫자 scale과 반올림 규칙을 manifest에 기록해요.
- 행은 `phase`, `model_id`, `parameter_set_id`, `scenario_id`, `sample_size`, `sample_seed`, `center_id`, `point_index` 순으로 정렬해요.
- gzip mtime과 PNG metadata timestamp를 제거해요.
- 절대 경로와 실행 시각을 결정적 산출물에 넣지 않아요.
- `checksums.sha256`는 정렬된 상대 경로를 사용해요.
- `environment.json`, 성능 결과와 checksum 파일 자체는 checksum 대상에서 제외해요.
- Unit 3에서는 합성 좌표·수치·렌더·환경·checksum을 보존하고 결과 문서에서 프로토콜과 runner 커밋 SHA를 연결해요.

## 개인정보와 완료 조건

- 문서에 적힌 합성 중심 외의 좌표를 사용하지 않아요.
- 운영 DB, 운영 로그, 사용자 좌표, `requestId`, 한숨 ID와 기기 식별자를 읽거나 산출물에 기록하지 않아요.
- 정확한 사용자 위치나 클라이언트가 보낸 실제 격자 중심을 재구성하지 않아요.
- 결과는 모델과 파라미터를 제안할 뿐이며 팀 합의와 후속 ADR 없이 제품 계약을 바꾸지 않아요.
- 실험 실패와 기각 결과도 원인과 함께 보존해요.
