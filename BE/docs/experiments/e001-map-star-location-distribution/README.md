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
3. 확인 단계의 좌표와 원본 panel은 최종 후보를 고정한 뒤 분포 runner가 한 번에 생성하고, 블라인드 nonce와 좌우 key는 수치 판정이 끝난 뒤 `e001BlindReview`가 별도로 생성해요.
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

시나리오 ID는 아래 값만 사용해요. ID의 `n`은 다섯 seed를 합친 개수예요.

| phase | `scenarioId` | 개수 계약 |
| --- | --- | --- |
| tuning | `tuning-single-n500` | 단일 중심 총 500개 |
| tuning | `tuning-grid-imbalanced-n4500` | 불균형 3×3 전체 총 4,500개 |
| confirmation | `confirmation-single-n500` | 단일 중심 총 500개 |
| confirmation | `confirmation-single-n5000` | 단일 중심 총 5,000개 |
| confirmation | `confirmation-grid-equal-n500-per-center` | 9개 중심마다 총 500개 |
| confirmation | `confirmation-grid-equal-n5000-per-center` | 9개 중심마다 총 5,000개 |
| spectral | `spectral-grid-equal-n500-per-center` | 121개 중심마다 총 500개 |
| spectral | `spectral-grid-imbalanced-n60500` | 불균형 11×11 전체 총 60,500개 |
| calibration | `field-calibration-cal-41x41` | 좌표 생성 없이 1,681개 고정 위치에서 field 측정 |
| conformance | `conformance-cal-n320-per-model` | D*·E*마다 다섯 seed를 합쳐 320개, 실험 표본 수에서 제외 |

단일 중심의 `centerId`는 `single`이고 `centerI=centerJ=0`이에요. 격자에서는 `centerI`가 서쪽부터 `-k,...,k`, `centerJ`가 남쪽부터 `-k,...,k`예요. `centerId`는 북쪽에서 남쪽, 같은 행에서는 서쪽에서 동쪽 순서로 `r00c00`부터 부여해요. `pointIndex`는 `(scenarioId, centerId, sampleSeed)` 안에서 0부터 시작하는 지역 인덱스예요.

균등 조건의 중심별 개수는 다섯 seed로 똑같이 나눠요. 따라서 `n=500`은 seed마다 중심별 100개이고 `n=5,000`은 1,000개예요. 모델과 파라미터 조합은 같은 point seed를 사용해요.

불균형 3×3은 중심별 총개수를 먼저 largest-remainder 방식으로 배분한 뒤 각 값을 다섯 seed로 똑같이 나눠요. 불균형 11×11은 seed마다 정확히 12,100개를 largest-remainder 방식으로 따로 배분해 총 60,500개를 만들어요.

largest-remainder는 `centerId` 오름차순으로 `weightSum += weight`를 단순 합산하고, 같은 순서로 `expected=(totalForAllocation×weight)/weightSum`, `base=(long)StrictMath.floor(expected)`, `remainder=expected-base`를 계산해요. 먼저 모든 `base`를 배정한 뒤 남은 개수만큼 `remainder` 내림차순으로 한 개씩 더하고, 나머지가 같으면 `centerId` 오름차순을 사용해요. 병렬 합산과 `Math.fma`는 사용하지 않아요.

point seed는 아래 UTF-8 문자열의 SHA-256 앞 64bit를 big-endian long으로 읽어 만들어요. 모델 ID와 파라미터 ID는 넣지 않아 공통 난수를 사용하고, 중심과 point index를 넣어 격자마다 같은 상대 모양이 복제되지 않게 해요.

```text
E001-v1|scenarioId|originId|centerI|centerJ|sampleSeed|pointIndex
```

seed 문자열의 정수 필드는 base-10 ASCII로 직렬화해요. 양수에 `+`를 붙이지 않고 선행 0을 쓰지 않으며, 0은 `0`, 음수는 `-` 뒤에 절댓값을 써요. `scenarioId`와 `originId`는 이 문서에 적힌 ASCII 문자열을 그대로 사용해요.

각 point seed에서 시작하는 PRNG는 Unit 3에서 golden test와 함께 제공할 `SplitMix64-v1`을 사용해요. 필드 seed는 표본 seed와 분리하여 다음 값으로 고정해요.

```text
fieldSeed = 0x5048454545455701
```

`SplitMix64-v1`은 `state=pointSeed`로 시작하고 부호 있는 Java `long`의 overflow를 의도적인 modulo `2^64` 연산으로 사용해요.

```text
state += 0x9E3779B97F4A7C15
z = state
z = (z xor (z >>> 30)) × 0xBF58476D1CE4E5B9
z = (z xor (z >>> 27)) × 0x94D049BB133111EB
nextLong = z xor (z >>> 31)
nextDouble = (nextLong >>> 11) × 0x1.0p-53
```

`Math.addExact`, `Math.multiplyExact`, 병렬 stream과 순서가 정해지지 않은 reduction은 사용하지 않아요.

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
v = 1 - u
T(u) = (v × v × v) × (1 + 3u + 6u²)  (0 <= u < 1)
T(u) = 0                              (u >= 1)
```

구현은 `u2=u×u`, `v2=v×v`, `v3=v2×v`, `shape=((6×u2)+(3×u))+1`, `T=v3×shape` 순서로 계산해요. 전개식 `1-10u³+15u⁴-6u⁵`은 `u≈1`에서 cancellation으로 음수가 될 수 있어 사용하지 않아요.

### 거리 커널 sampler

- 각도 상수는 `TWO_PI=0x1.921fb54442d18p2`로 고정해요.
- A는 point seed의 첫 두 `nextDouble`로 `dx=(300.0×U)-150.0`, `dy=(300.0×V)-150.0` 순서로 만들어요.
- B는 후보마다 `U_radius`, `U_angle` 두 값을 소비하고 `r=300.0×StrictMath.sqrt(U_radius)`, `theta=TWO_PI×U_angle` 순서로 만들어요.
- C는 후보마다 `U_radius`, `U_angle` 두 값을 소비하고 아래 순서로 절단 Rayleigh 역 CDF와 `theta=TWO_PI×U_angle`을 계산해요.
- D는 B와 같은 면적 균등 원 후보마다 `U_radius`, `U_angle`, `U_accept` 세 값을 먼저 소비하고 아래 순서로 acceptance를 계산해요.
- B~D는 `dx=r×cosθ`, `dy=r×sinθ`, `rOut=hypot(dx,dy)`를 순서대로 계산하고 `dx`, `dy`, `rOut`이 finite이며 `rOut < 300`인 후보만 채택해요.
- B~D는 한 점당 최대 4,096개 후보를 검사하며 모두 거절되면 다른 좌표로 대체하지 않아요.

```text
# C
sigma2 = sigma × sigma
denominator = 2.0 × sigma2
truncationExponent = -(90000.0 / denominator)
truncationDelta = StrictMath.expm1(truncationExponent)
scaledDelta = U_radius × truncationDelta
logTerm = StrictMath.log1p(scaledDelta)
radicand = -2.0 × logTerm
r = sigma × StrictMath.sqrt(radicand)
theta = TWO_PI × U_angle

# D
r = 300.0 × StrictMath.sqrt(U_radius)
theta = TWO_PI × U_angle
r2 = r × r
sigma2 = sigma × sigma
denominator = 2.0 × sigma2
gaussianExponent = -(r2 / denominator)
gaussian = StrictMath.exp(gaussianExponent)
u = r / 300.0
acceptance = gaussian × T(u)
accept iff coordinates are valid and U_accept < acceptance
```

B~D는 `cosTheta=StrictMath.cos(theta)`, `sinTheta=StrictMath.sin(theta)`, `dx=r×cosTheta`, `dy=r×sinTheta`, `rOut=StrictMath.hypot(dx,dy)` 순서로 계산해요. B와 C는 좌표가 유효하면 바로 채택하고, D는 좌표 유효성과 acceptance 조건을 모두 만족할 때 채택해요. E가 요구한 D 후보 하나를 4,096회 안에 만들지 못하면 해당 E 점도 즉시 실패하며 이후 후보나 선택 난수를 소비하지 않아요.

좌표·삼각·지수·로그·제곱근 계산에는 Java 21의 `StrictMath`를 사용하고 `Math.fma`는 사용하지 않아요. 비교는 위에 적힌 strict inequality와 식의 괄호 순서를 그대로 따라요.

### 강도장과 E sampler

noise lattice hash는 아래 순서로 `SplitMix64-v1`의 finalizer인 `mix64`를 적용해요. `mix64(input)`은 위 PRNG 식에서 `z=input`으로 시작해 세 xor·곱셈 단계만 수행하며 state increment를 하지 않아요. `octave`는 `Integer.toUnsignedLong`으로 바꿔요.

```text
h = mix64(fieldSeed xor domain)
h = mix64(h xor unsigned(octave))
h = mix64(h xor ix)
h = mix64(h xor iy)
```

`gradient-quintic-v1`의 domain은 `0xEECF68A53C368278`, `value-quintic-v1`의 domain은 `0xCD7893E850B50B86`이에요. 음수 lattice 좌표는 `(long)StrictMath.floor(q)`로 구해요. fade와 선형 보간은 아래 중간연산 순서를 그대로 사용해요.

```text
t2 = t × t
t3 = t2 × t
fade = t3 × (t × ((t × 6) - 15) + 10)
lerp(a,b,t) = a + t × (b - a)
```

gradient noise는 hash 하위 3bit로 다음 8개 단위 벡터를 순서대로 골라요.

```text
(1,0), (-1,0), (0,1), (0,-1),
(s,s), (-s,s), (s,-s), (-s,-s)
s = 0x1.6a09e667f3bccp-1
gradientScale = 0x1.6a09e667f3bcdp0
```

`ix=floor(qx)`, `iy=floor(qy)`, `tx=qx-ix`, `ty=qy-iy`로 두고 각 corner의 gradient와 아래 순서로 dot product를 계산해요.

```text
d00 = gx00 × tx       + gy00 × ty
d10 = gx10 × (tx - 1) + gy10 × ty
d01 = gx01 × tx       + gy01 × (ty - 1)
d11 = gx11 × (tx - 1) + gy11 × (ty - 1)
x0 = lerp(d00, d10, fade(tx))
x1 = lerp(d01, d11, fade(tx))
gradientRaw = lerp(x0, x1, fade(ty))
gradientScaled = gradientRaw × gradientScale
gradientNoise = max(-1, min(1, gradientScaled))
```

`gradientScale`은 `1/s`의 고정 double 값이며 `s×gradientScale=1.0`이에요. 마지막 clamp는 `lower=StrictMath.min(1.0,gradientScaled)`, `gradientNoise=StrictMath.max(-1.0,lower)` 순서로 호출해요.

value noise의 lattice 값은 `2 × ((hash >>> 11) × 0x1.0p-53) - 1`이에요. corner 값 `v00`, `v10`, `v01`, `v11`을 gradient noise의 `x0`, `x1`, 최종 y축과 같은 순서로 보간해요.

octave는 profile 표의 왼쪽부터 0, 1, 2예요. `radians=degrees×(StrictMath.PI/180)`, `cosθ=StrictMath.cos(radians)`, `sinθ=StrictMath.sin(radians)` 순서로 회전값을 구해요. 절대 `EPSG:5179` 좌표 `(x,y)`에 대해 `qx=(cosθ×x-sinθ×y)/L`, `qy=(sinθ×x+cosθ×y)/L`로 계산해요. profile 합산은 괄호를 고정해 `(w0×n0+w1×n1)+w2×n2` 순서로 수행하고 마지막 값만 `StrictMath.max(-1, StrictMath.min(1, value))`로 clamp해요.

`sir-16`과 `sir-32`는 D 후보를 각각 N개 만든 뒤 절대 후보 위치 `F(centerEasting+dx, centerNorthing+dy)`를 평가해요. index 순서로 `logWeight[i]=βF[i]`를 배열에 저장하고 같은 순서로 최댓값을 구해요. 이어 `weight[i]=StrictMath.exp(logWeight[i]-maxLogWeight)`를 저장하면서 `weightSum += weight[i]`의 단순 합산을 해요. 거리 커널 가중치를 다시 곱하지 않아요.

모든 후보를 만든 뒤 별도 난수 하나를 소비해 `target=U×weightSum`을 계산해요. index 0부터 N-2까지 `cumulative += weight[i]`를 수행하고 `target < cumulative`가 처음 참인 후보를 선택해요. 끝까지 참이 아니면 반올림 경계와 관계없이 마지막 후보 N-1을 선택해요.

`rejection-128`은 D 후보마다 절대 후보 위치의 F를 평가하고 별도 난수 `U`를 사용해 `U < StrictMath.exp(β×(F-1))`이면 채택해요. 128개를 모두 거절하면 다른 좌표로 대체하지 않아요. B~E sampler가 한도에 도달하면 해당 점을 `sampler-failures.csv`에 기록하고 그 parameter set은 hard gate에서 탈락시켜요.

Unit 3과 Unit 4는 이 알고리즘의 정수·부동소수점 raw bit golden test를 고정해요. 첫 실험 실행 뒤 golden 값이나 연산 순서를 바꾸면 프로토콜 변경으로 취급해요.

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

parameter set ID는 A `a-square-300`, B `b-disk-r300`, C `c-s{100|120}-r300`, D `d-s{100|120}-r300` 형식으로 고정해요. E는 `e-s{100|120}-{gradient|value}-{p1|p2}-b{040|070|100}-{sir16|sir32|rejection128}` 형식을 사용해요. 중괄호에는 선택한 값 하나만 넣고 소문자 ASCII와 hyphen만 사용해요.

octave 회전은 각각 `17°`, `71°`, `137°`로 고정해요. 각 noise octave는 문서화된 구현 경계로 `[-1,1]`에 정규화하고, profile 가중치의 합을 1로 유지하여 `F(x,y) ∈ [-1,1]`이 되게 해요. 같은 범위라도 noise와 profile의 분산은 다를 수 있으므로 같은 `β`를 같은 실제 강도로 해석하지 않아요.

각 E 후보는 `CAL+(30i,30j)`, `i,j∈{-20,...,20}`의 41×41 고정 위치에서 `βF`의 모집단 표준편차인 `logIntensityStd`를 계산해요. `j=-20..20` 바깥 loop, `i=-20..20` 안쪽 loop 순서로 1,681개 `value=β×F`를 배열에 저장해요. 같은 순서로 `sum += value`, `mean=sum/1681.0`을 계산하고, 다시 같은 순서로 `delta=value-mean`, `squareSum += delta×delta`, `variance=squareSum/1681.0`, `logIntensityStd=StrictMath.sqrt(StrictMath.max(0.0,variance))`를 계산해요. 병렬 reduction과 `Math.fma`는 사용하지 않아요.

이 값은 좌표를 생성하지 않는 `field-calibration-cal-41x41` metric이며 noise·profile 간 실제 강도를 비교하는 결정적 기준이에요.

Unit 3과 Unit 4는 이 문서의 거리 커널, 두 noise와 세 sampler를 구현해요. Unit 6의 runner 커밋 전에는 결과를 생성하지 않아요. 실행 중 파라미터를 자동 탐색하거나 연속 최적화하지 않아요.

## 실험 단계와 표본 수

### 1. 튜닝

- 기준점: `CAL`
- 조건: 단일 중심 500개와 고정 불균형 3×3 전체 4,500개
- 반복: 다섯 sample seed에 균등 분할
- 조합: 기준 A 1개 + D 2개 + E 36개
- 정상 경로 총 요청: `39 × 5,000 = 195,000`개

불균형 3×3은 아래 비율을 사용하고 총 4,500개가 되도록 largest-remainder 방식으로 정수 배분해요. 행은 북쪽에서 남쪽, 열은 서쪽에서 동쪽 순서예요.

```text
0.55  0.85  0.40
0.75  2.40  1.10
0.30  0.95  1.70
```

따라서 중심별 총개수와 seed별 개수는 아래처럼 고정돼요.

```text
total per center       per seed
 275   425   200       55   85   40
 375  1200   550       75  240  110
 150   475   850       30   95  170
```

튜닝에서는 이미지를 만들지 않아요. hard gate를 통과한 설정을 수치 규칙으로 정렬해 `D*`와 `E*` 하나씩 고정해요.

### 2. 최종 확인

- 기준점: 튜닝에 사용하지 않은 `HOLDOUT`
- 모델: `A`, `B`, `C`, `D*`, `E*`
- 조건: 단일 중심과 균등 3×3
- 표본: 각 중심당 500개·5,000개, 다섯 seed에 균등 분할
- 정상 경로 총 요청: `5 × (1+9) × (500+5,000) = 275,000`개

`C`는 `D*`와 같은 `σ`를 사용해요. 확인 결과로 `D*`나 `E*`의 파라미터를 다시 조정하지 않아요.

### 3. 300m 격자 스트레스

- 기준점: `SPECTRAL`
- 모델: 문제 기준인 `A`와 주 판정 대상 `D*`, `E*`
- 생성 영역: 11×11 중심, 평가 영역: 중앙 9×9
- 표본: profile마다 총 60,500개, 즉 중심당 평균 500개
- 중심별 개수 profile: 균등 profile과 고정 불균형 profile
- 균등 profile: 중심마다 총 500개
- 정상 경로 총 요청: `3 × 121 × 500 × 2 = 363,000`개

불균형 profile은 각 중심 `(centerI,centerJ)`에 대해 아래 UTF-8 문자열의 SHA-256 앞 64bit를 big-endian long으로 읽고 `u=(bits >>> 11)×0x1.0p-53`, `weight=0.25+(1.75×u)` 순서로 계산해요. seed마다 12,100개를 위에서 정한 largest-remainder 방식으로 배분해요.

```text
profileSeed = 0xE001300000000001
E001-v1|spectral-profile|centerI|centerJ|E001300000000001
```

profile seed 문자열의 `centerI`, `centerJ`도 위의 base-10 ASCII 규칙을 따라요. 121개 가중치는 `centerId` 오름차순으로 계산하고 같은 순서의 largest-remainder 규칙을 적용해요.

`coordinates.csv.gz`에 기록하는 분포 비교 표본의 정상 경로는 튜닝 195,000개, 최종 확인 275,000개, 격자 스트레스 363,000개로 총 833,000개 좌표 생성을 요청해요. field calibration, conformance와 performance의 sampler 호출은 이 수에서 제외해요. D*·E*가 모두 정해지고 sampler failure가 없을 때 분포 비교 생성 좌표도 833,000개예요. failure가 생기면 좌표를 누락한 채 요청 개수·생성 개수·실패 개수를 manifest에 각각 기록하고 해당 parameter set을 탈락시켜요.

D*를 정하지 못하면 E 후보를 생성하지 않고 튜닝 단계에서 `distributionOutcome=inconclusive`, `outcomeReason=no-d`로 종료해요. D*는 있지만 E tuning gate를 통과한 후보가 없으면 최종 확인은 A·B·C·D*만 생성해 D*를 재검증하고, 통과하면 `distributionOutcome=selected-d`, `outcomeReason=no-e`, 실패하면 `inconclusive`와 `no-d`로 기록해요. 이 경로에서는 격자 스트레스와 블라인드 평가를 만들지 않아요. 두 경우 모두 정상 경로보다 적은 요청·생성 개수를 manifest에 기록해요.

## 수치 지표

분포 형태 지표인 `radius`, `edgeRatio30`, `a4`, `seam300`, `radialKs`는 seed별로 계산한 뒤 median과 범위를 함께 기록해요. 화면 밀도에 비선형인 `proximity8/16/24`, `neighbors10`, `hotspot50`, `grid300`은 실제 panel과 같은 밀도를 보도록 다섯 seed shard를 모두 pool해 한 번 계산하고 `sample_seed`를 빈 값으로 기록해요. 별 하나를 독립 반복으로 취급해 신뢰구간을 부풀리지 않아요.

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
| `samplerFailureCount` | sampler 한도 도달로 생성하지 못한 좌표 수 | 후보 무효화 |
| `logIntensityStd` | 41×41 고정 위치에서 `βF`의 모집단 표준편차 | field 강도 비교 |

`edgeRatio30`의 경계 거리는 A에서 `d=150.0-StrictMath.max(StrictMath.abs(dx),StrictMath.abs(dy))`, B~E에서 `d=300.0-r`로 계산해요. 바깥 띠는 `0 <= d < 30`, 안쪽 띠는 `30 <= d < 60`인 half-open 구간이에요. A의 `outerArea=32400.0`, `innerArea=25200.0`이고, B~E는 `outerArea=StrictMath.PI×17100.0`, `innerArea=StrictMath.PI×15300.0` 순서로 계산해요.

`outerDensity=(outerCount+0.5)/outerArea`, `innerDensity=(innerCount+0.5)/innerArea`, `edgeRatio30=outerDensity/innerDensity` 순서로 계산해 0개 조건을 처리해요. 서로 다른 support 사이의 값은 설명용으로만 사용하고 D와 E를 같은 support에서 비교해요.

`radius`와 `hotspot50`의 quantile은 값을 오름차순으로 정렬한 뒤 `sorted[ceil(pN)-1]`을 고르는 nearest-rank 방식이에요. median은 개수가 홀수면 가운데 값, 짝수면 `((sorted[N/2-1]+sorted[N/2])/2)`를 연산 순서 그대로 사용해요. 반경은 각 점과 그 점의 입력 중심 사이에서 계산해요. 띠와 cell은 왼쪽·아래쪽 경계를 포함하고 오른쪽·위쪽 경계를 제외하는 half-open 구간을 사용해요.

`a4`는 metric 입력을 `centerId`, `pointIndex` 오름차순으로 순회해요. `r==0.0`이면 signed zero와 관계없이 그 점의 `(cos4,sin4)`를 `(1.0,0.0)`으로 두고, 아니면 `theta=StrictMath.atan2(dy,dx)`, `angle4=4.0×theta`, `cos4=StrictMath.cos(angle4)`, `sin4=StrictMath.sin(angle4)`를 계산해요. 같은 순서로 `sumCos += cos4`, `sumSin += sin4`를 계산한 뒤 `meanCos=sumCos/N`, `meanSin=sumSin/N`, `magnitude2=(meanCos×meanCos)+(meanSin×meanSin)`, `corrected=StrictMath.max(0.0,magnitude2-(1.0/N))`, `a4=StrictMath.sqrt(corrected)` 순서를 사용해요. `N=0`이면 `undefined`예요.

`radialKs`는 같은 sample seed의 D와 E 반경을 각각 오름차순으로 정렬하고, 두 표본에 나타난 모든 고유 반경 `t`에서 `countD(r<=t)/ND`, `countE(r<=t)/NE`인 두 경험적 CDF의 절대 차이를 계산한 최댓값이에요. 동률 반경을 각 표본에서 모두 소비한 뒤 차이를 계산하고, 어느 표본이 비어 있으면 `undefined`예요.

`seam300`은 3×3의 내부 경계 `x∈{-150m,150m}`, `y∈{-150m,150m}` 네 개를 기준점 상대 좌표에서 평가해요. 수직 경계 `b`는 왼쪽 `[b-15,b)`, 오른쪽 `[b,b+15)` strip을, 수평 경계 `b`는 아래쪽 `[b-15,b)`, 위쪽 `[b,b+15)` strip을 사용해요. 접선 방향은 `[-450m,450m)`만 포함해요. `L=leftOrBelowCount+0.5`, `R=rightOrAboveCount+0.5`, `seam=2.0×StrictMath.abs(L-R)/(L+R)` 순서로 계산하고, 한 seed의 값은 수직 두 개 뒤 수평 두 개 순서로 단순 합산한 산술평균이에요. 수직·수평 strip이 교차하는 점은 각 방향 계산에 한 번씩 참여해요.

`grid300`은 같은 model·parameter set·scenario의 다섯 seed와 11×11 전체 생성점을 pool한 뒤, 절대 좌표가 중앙 9×9 평가 창에 들어오는 점만 15m cell로 rasterize해요. 기준점 상대 `[-1350m,1350m)²`를 서쪽에서 동쪽으로 `ix=0..179`, 남쪽에서 북쪽으로 `iy=0..179`인 `180×180` count raster로 만들어요. 경계는 half-open이고 raster count의 합과 이후 모든 loop는 `iy` 바깥, `ix` 안쪽 순서예요.

`hann(i)=0.5×(1.0-StrictMath.cos(TWO_PI×(i/179.0)))`, `mean=totalCount/32400.0`, `windowed=((count-mean)×hann(ix))×hann(iy)` 순서로 평균을 빼고 2차원 Hann window를 적용해요. 각 정수 bin `(kx,ky)`의 직접 DFT는 raster 순회마다 `phaseX=(kx×ix)/180.0`, `phaseY=(ky×iy)/180.0`, `phase=-TWO_PI×(phaseX+phaseY)`, `re += windowed×StrictMath.cos(phase)`, `im += windowed×StrictMath.sin(phase)` 순서로 계산하고 `power=(re×re)+(im×im)`로 정의해요.

분자는 `(9,0)`, `(-9,0)`, `(0,9)`, `(0,-9)` 순서로 power를 단순 합산해 4.0으로 나눠요. 분모 후보는 `kx,ky∈[-90,89]`에서 `radiusBin=StrictMath.hypot(kx,ky)`가 `7.2 <= radiusBin <= 10.8`을 만족하는 bin이에요. `axisRatio=StrictMath.min(StrictMath.abs(kx),StrictMath.abs(ky))/radiusBin`이 `0x1.0907dc1930690p-2` 이하인 축 15도 이내 bin과 DC를 제외해요. 남는 power는 정확히 144개이며 오름차순 정렬 후 `denominatorMedian=(sorted[71]+sorted[72])/2.0`으로 계산해요. `grid300=numerator/denominatorMedian`이고 분모가 0이거나 어느 값이 finite가 아니면 `undefined`로 기록해 후보를 탈락시켜요. FFT crop에 의한 오판을 막기 위해 `grid300` 하나만으로 모델을 채택하지 않아요.

균등 정사각형은 같은 개수로 3×3을 채우면 내부를 빈틈없이 타일링하고 정확한 `1/300m` 성분이 sinc 영점에 놓일 수 있어요. 따라서 균등 조건의 낮은 `grid300`을 곧바로 자연스러움으로 해석하지 않고 `a4`, 불균형 조건의 `seam300`, 11×11의 축 방향 대역을 함께 봐요.

최근접 거리는 반올림한 sprite pixel이 아니라 원래 `EPSG:5179` double 좌표 사이의 `StrictMath.hypot` 거리로 계산해요. 고정 렌더의 `1m/px` 조건에서 연속 거리 1m를 1px로 해석해요. spatial hash 또는 kd-tree 결과는 작은 고정 fixture에서 brute force 결과와 대조해요. `proximity`는 실제 앱의 줌별 아이콘 겹침률이 아니라 고정 렌더의 근접 proxy예요.

화면 밀도 지표의 target은 평가 창 안에 좌표가 들어온 다섯 seed의 점이고, 이웃 검색 pool은 같은 model·parameter set·scenario에서 생성한 다섯 seed의 모든 점이에요. target과 pool은 각각 `sampleSeed`, `centerId`, `pointIndex` 오름차순으로 순회해요. 따라서 spectral에서는 중앙 9×9 target의 이웃을 찾을 때 바깥 11×11 buffer 중심에서 생성된 점도 포함해요. 자기 자신은 `sampleSeed`, `centerId`, `pointIndex`가 모두 같은 점으로 식별해 제외해요.

`proximity8/16/24`는 가장 가까운 다른 점까지의 연속 거리가 threshold보다 **작을 때만** target에 포함하고 target 전체 개수로 나눠요. `neighbors10`은 각 target마다 자신을 제외한 거리가 10m보다 작은 pool의 점을 세어 위 target 순서로 단순 합산한 평균이에요. `hotspot50`은 target만 절대 `EPSG:5179` 원점에 맞춘 50m half-open cell에 넣고 평가 창 안의 빈 cell도 0으로 포함해요. 평가 창은 단일 중심 `[-300m,300m)²`, 3×3 `[-600m,600m)²`, spectral 중앙 9×9 `[-1350m,1350m)²`예요. cell은 남쪽에서 북쪽, 같은 행에서 서쪽에서 동쪽 순서로 순회하고, 이 순서의 2-pass 모집단 표준편차를 평균으로 나눠 변동계수를 계산해요. 평균이 0이면 `undefined`예요.

분포 형태 지표의 반복 단위는 개별 별이 아니라 `origin × sampleSeed`예요. pooled 화면 밀도 지표에서 seed는 반복이 아니라 하나의 결정적 표본을 구성하는 shard이며, 단일 pooled 값을 반복 측정처럼 해석하지 않아요. 수십만 개 좌표를 독립 표본으로 간주한 t-test나 신뢰구간은 만들지 않아요.

## 고정 렌더와 블라인드 평가

- canvas: `1200×1200px`, DPR 1
- viewport: 기준 중심에서 동서·남북 각각 600m, 즉 `1m/px`
- background: `#11131A`
- 별: 지름 8px, `#FFD36A`, opacity 0.70
- 그리기 순서: `centerId`, `sampleSeed`, `pointIndex` 오름차순
- 격자선·중심점·모델명·파라미터는 이미지에 표시하지 않아요.

`HOLDOUT`의 단일·3×3과 500·5,000개 조합 네 장을 D*와 E*에 대해 생성해요. 각 panel은 해당 scenario의 다섯 sample seed shard를 모두 그려요. 다섯 개발자는 모델명과 좌우 순서를 숨긴 화면에서 각 장마다 다음 항목을 독립적으로 답해요.

Unit 6은 고정된 8×8 RGBA 별 sprite를 test resource로 커밋하고 raw byte checksum을 golden test로 보호해요. 점 `(x,y)`의 sprite 중심은 `pixelX=StrictMath.round(x-(centerX-600))`, `pixelY=StrictMath.round((centerY+600)-y)`로 계산하며 top-left `(pixelX-4,pixelY-4)`에서 source-over로 그려요. panel 밖 픽셀은 잘라내고 anti-alias, 보간과 추가 blur를 적용하지 않아요.

분포 runner는 D*와 E*의 1200×1200 원본 panel을 `coordinator-only/model-panels/`에 결정적으로 생성하고 checksum에 포함해요. 평가 전 전달 단위에는 이 directory를 넣지 않고, 평가자는 workspace나 원본 panel의 파일명·내용에 접근하지 않아요.

별도 `e001BlindReview` task는 `SecureRandom`으로 raw 16 byte nonce를 한 번 만들어요. 이 raw nonce를 `HmacSHA256` key로 사용하고, 좌우 배치 digest의 message는 UTF-8 `assignment|scenarioId`, 공개 pair digest의 message는 UTF-8 `pair-id|scenarioId`로 domain separation해요. 네 scenario를 좌우 배치 digest의 unsigned byte 사전순으로 정렬하고 digest가 같으면 UTF-8 `scenarioId` byte 사전순으로 정렬해요. 앞의 두 장은 E*를 왼쪽, 뒤의 두 장은 D*를 왼쪽에 배치해 정확히 2:2로 counterbalance해요.

`pairId`는 공개 pair digest 앞 8 byte의 소문자 hexadecimal이에요. 네 `pairId` 중 충돌이 나면 nonce를 버리고 새 nonce부터 다시 생성해요. 공개 `pairId`와 파일명에는 좌우 배치 digest를 사용하지 않아요.

블라인드 비교 이미지는 원본 panel 두 개와 32px의 `#000000` gutter를 합친 `2432×1200` PNG예요. nonce와 좌우 key는 `blind/coordinator-only/blind-key.csv`에만 기록해요. task는 key·source와 분리된 `blind/reviewer-package/`를 만들고, 실행 담당자는 그 package의 복사본만 다섯 평가자에게 전달해요. 평가자는 workspace의 `e001/`, blind manifest와 coordinator-only 경로를 열지 않고 package에서 이미지와 ballot만 사용해요.

다섯 응답이 모두 제출되기 전에 nonce, key, 원본 model panel의 파일명·내용, model identity 또는 좌우 mapping 중 하나라도 평가자에게 노출되면 일부 응답만 제외하지 않고 해당 네 쌍의 batch 전체를 무효화해요. 기존 응답과 package를 보존한 뒤 새 nonce로 전체 네 쌍을 다시 만들어요.

1. 어느 쪽에서 사각형·원형·격자 모양이 덜 보여요? (`left/right/tie`)
2. 어느 쪽이 더 자연스러운 군집과 빈 공간으로 보여요? (`left/right/tie`)
3. 서비스에 부적절할 정도로 강한 hotspot이 있어요? (`left/right/both/neither`)

누락된 평가는 보충한 뒤 판정하며 5명 미만으로 결론을 내리지 않아요. 원본 응답과 모델 key를 결과에 함께 보존해요.

shape·natural 항목은 key에서 E*가 놓인 쪽과 응답이 같을 때만 E* 한 표이고 `tie`는 어느 모델 표도 아니에요. hotspot 항목은 E*가 왼쪽이면 `left` 또는 `both`, 오른쪽이면 `right` 또는 `both`를 E* hotspot 한 표로 세요. reviewer·pair 조합마다 정확히 한 행만 허용하고 누락·중복·허용값 밖의 행은 보충 또는 정정하기 전까지 판정하지 않아요.

이 평가는 팀 내부 선택 근거이며 실제 사용자 대상 UX 검증이나 통계적으로 일반화할 수 있는 사용자 연구로 해석하지 않아요.

## 합격선과 선택 규칙

### Hard gate

metric이 누락되거나 `undefined` 또는 non-finite이면 그 metric을 요구하는 gate는 실패해요. 분포 형태 metric은 별도 조건이 없으면 다섯 seed 값의 median을 비교하고, pooled 화면 밀도 metric은 `sample_seed`가 빈 단일 값을 비교해요.

#### 생성 무결성

- 실제로 생성한 모든 A~E 좌표의 `dx`, `dy`, `r`이 finite여야 해요.
- A는 `-150 <= dx < 150`, `-150 <= dy < 150`이어야 하고, B~E는 `0 <= r < 300m`이며 `r=300m`에 clamp된 좌표가 없어야 해요.
- 실제로 생성한 B~E parameter set은 모든 scenario에서 `samplerFailureCount=0`이고 생성 개수가 요청 개수와 같아야 해요.
- 튜닝 D·E 후보의 무결성 실패는 그 parameter set을 탈락시키고, 튜닝 A 또는 확인 단계 A~C의 무결성 실패는 `distributionOutcome=inconclusive`, `outcomeReason=integrity-failure`로 끝내요. 전체 checksum 불일치는 공식 결과를 승격하지 않고 staging 실행을 실패로 보존해요.
- 같은 프로토콜·runner·JDK로 두 번 실행한 결정적 산출물 checksum이 같아야 해요.

각 단계에서는 생성 무결성을 수치 gate보다 먼저 판정해요. 튜닝 뒤 D*로 고정된 parameter set이 확인 또는 spectral에서 무결성을 실패하거나, 해당 단계의 대조군 A~C가 무결성을 실패하면 즉시 `distributionOutcome=inconclusive`, `outcomeReason=integrity-failure`예요. 유효한 D*가 있고 E*만 무결성 또는 수치 gate를 실패할 때만 단계에 맞는 `selected-d` 결과를 사용할 수 있어요.

#### 튜닝 선택 gate

`radius`, `edgeRatio30`, `a4`, `radialKs`는 `tuning-single-n500`에서 seed별로 계산해요. `seam300`은 `tuning-grid-imbalanced-n4500`에서 같은 seed의 아홉 중심을 pool해 seed별로 계산해요. `proximity16`, `neighbors10`, `hotspot50`은 이 3×3 scenario의 다섯 seed를 모두 pool해 한 번 계산해요.

- D의 seed별 중심 거리 `p95` median은 210m 이하이고 `p99` median은 250m 이하이며, 어떤 seed도 `p99 > 270m`가 아니어야 해요.
- D의 `edgeRatio30`은 median 0.35 이하이고 어떤 seed도 0.50을 넘지 않아야 해요.
- E 후보의 `radialKs` median은 0.05 이하이고 `edgeRatio30_E` median은 `max(1.10×edgeRatio30_D, edgeRatio30_D+0.02)` 이하여야 해요.
- E 후보의 pooled `proximity16`은 D보다 3%p 넘게 높지 않고, pooled `neighbors10`은 D의 1.25배를 넘지 않으며, pooled `hotspot50.max`는 D의 pooled `hotspot50.p99` 4배를 넘지 않아야 해요.
- E 후보의 `a4` median은 A의 0.5배 이하이고, `seam300` median은 같은 `σ`인 D보다 20% 이상 낮으며 다섯 seed 중 네 개 이상에서 개선돼야 해요.

#### 최종 확인 재검증

튜닝으로 D*와 E*를 고정한 뒤 아래처럼 재검증하며 확인 결과로 파라미터를 바꾸지 않아요.

| scenario | 재검증 gate |
| --- | --- |
| `confirmation-single-n500` | D*의 radius·edgeRatio30, E*의 radialKs·edgeRatio30·A 대비 a4 |
| `confirmation-single-n5000` | D*의 radius·edgeRatio30, E*의 radialKs·edgeRatio30·A 대비 a4 |
| `confirmation-grid-equal-n500-per-center` | E*의 pooled proximity16·neighbors10·hotspot50 |
| `confirmation-grid-equal-n5000-per-center` | E*의 pooled proximity16·neighbors10·hotspot50 |

각 scenario가 생성 무결성과 튜닝 때 사용한 같은 합격선을 독립적으로 통과해야 해요. 균등 3×3의 `seam300`은 설명용으로 기록하지만 불균형 튜닝 gate를 대신해 재적용하지 않아요. D*가 확인 gate를 하나라도 실패하면 `distributionOutcome=inconclusive`, `outcomeReason=no-d`, D*는 통과하지만 E*가 하나라도 실패하면 `distributionOutcome=selected-d`, `outcomeReason=e-confirmation-failed`, 둘 다 통과하면 spectral 판정으로 진행해요.

### 후보 정렬

먼저 `σ=120m`인 D가 튜닝 선택 gate를 통과하면 D*로 고정해요. 실패하면 `σ=100m`을 검사하고, 둘 다 실패하면 `distributionOutcome=inconclusive`, `outcomeReason=no-d`로 끝내요. C와 E*는 D*와 같은 `σ`를 사용해요.

튜닝 선택 gate를 통과한 E 후보만 다음 순서로 정렬해요.

1. `logIntensityStd`가 낮은 후보
2. field 평가 횟수가 적은 sampler: `sir-16`, `sir-32`, `rejection-128` 순서
3. `seam300`이 낮은 후보
4. `radialKs`가 낮은 후보
5. 남은 동률은 `parameterSetId` 사전순

wall-clock 생성 시간은 E* 정렬에 사용하지 않아요. 같은 코드도 실행 환경과 JVM 상태에 따라 시간이 달라져 E*와 이후 좌표 checksum을 바꿀 수 있기 때문이에요.

### E 채택 조건

아래 조건을 모두 만족해야 E를 채택해요.

- 11×11의 균등·불균형 profile 모두에서 E*의 pooled `grid300`이 D*보다 20% 이상 낮아요.
- 최종 확인 네 화면 중 세 화면 이상에서 사각형·원형·격자 모양이 덜 보인다는 E* 표가 4명 이상이에요.
- 최종 확인 네 화면 중 세 화면 이상에서 더 자연스럽다는 E* 표가 4명 이상이에요.
- 어떤 화면에서도 E*에 대한 부적절한 hotspot 표가 2명 이상 나오지 않아요.
- E* pure sampler의 측정 batch 열 개에서 좌표당 시간 median이 100μs 이하이고 어떤 측정 batch도 250μs를 넘지 않아요.

분포 runner는 spectral 생성 무결성을 먼저 통과한 D*·E*가 첫 조건인 두 profile까지 통과하면 `distributionOutcome=e-review-ready`, `outcomeReason=e-review-pending`으로 기록해요. 유효한 D*가 있고 E*만 spectral 무결성 또는 수치 조건을 실패하면 `distributionOutcome=selected-d`, `outcomeReason=e-spectral-failed`로 기록해요. 나머지 네 조건은 performance와 블라인드 평가에서 판정해요.

E 채택 조건을 하나라도 실패하거나 의미 있는 차이가 없으면 더 단순한 D*를 선택해요. D*가 확인 gate를 통과하지 못하면 어떤 모델도 채택하지 않고 `inconclusive`로 종료해요.

pure sampler 시간은 E*를 결정한 뒤 D*와 E* 각각 warmup 5회와 측정 10회, 회당 100,000개 생성으로 측정해요. 각 phase의 batch index는 0부터 시작하고 짝수 batch는 D*→E*, 홀수 batch는 E*→D* 순서로 실행해 measurement 순서를 균형화해요. 각 loop 직전·직후 `System.nanoTime()` 차이를 `elapsed_ns`로 기록하고 `ns_per_point=elapsed_ns/100000.0`으로 계산해요. batch마다 `acc=0L`로 시작해 각 점에서 `acc=Long.rotateLeft(acc,1) xor Double.doubleToRawLongBits(dx)`, 이어 같은 식으로 `dy`를 누적하고 loop 뒤 volatile sink에 저장하여 결과가 사용되게 해요.

E*의 측정 `ns_per_point` 열 개를 오름차순으로 정렬하고 `(sorted[4]+sorted[5])/2.0`을 performance median으로 사용해요. 100μs와 250μs gate는 각각 100,000ns와 250,000ns로 비교해요. wall-clock 값은 환경 의존적이므로 E* 선택, checksum과 CI assertion에서 제외하고 운영 전환 gate로만 사용해요. 실제 저장 경로는 Unit 12에서 기존·신규 구현을 번갈아 측정하며 p95 증가가 10ms 이하이고 기존의 2.5배 이하인지를 별도로 확인해요. 넘으면 Java 좌표 변환 또는 DB 왕복 구조를 재검토하고 운영 전환하지 않아요.

## 실행과 기본 테스트 격리

Unit 6은 다음 opt-in 명령을 추가해요.

```bash
./gradlew e001LocationDistribution --rerun-tasks --no-daemon
./gradlew e001LocationPerformance --rerun-tasks --no-daemon
./gradlew e001BlindReview --rerun-tasks --no-daemon
```

- 세 runner에는 공통 JUnit tag `e001`과 각각 `e001-distribution`, `e001-performance`, `e001-blind`를 붙여요.
- 기본 `test` task는 `e001`을 제외해요.
- 각 opt-in task는 이름에 대응하는 세부 tag만 실행해요.
- 실험 task는 `maxParallelForks=1`, `java.awt.headless=true`로 실행해요.
- `./gradlew test`는 전체 시뮬레이션을 실행하거나 E001 산출물을 생성하지 않아요.
- 지표 계산과 결정성 검증을 위한 작은 단위 테스트는 기본 `test`에 포함해요.
- 실험 runner는 운영 DB와 네트워크에 접근하지 않아요.

Unit 6에서는 기본 테스트와 세 task의 `--test-dry-run`으로 태그 격리만 검증하고 위 opt-in 명령을 실제로 실행하지 않아요. runner가 커밋된 뒤 Unit 7에서 `e001LocationDistribution`을 먼저 실행해요. `distributionOutcome=e-review-ready`일 때만 performance와 blind task를 순서대로 실행하고, 그 밖의 outcome이면 두 후속 task를 실행하지 않아요.

`e001LocationDistribution`은 시작할 때 `e001-previous-<runId>/` 복구 흔적을 먼저 검사해요. 공식 `e001/`이 없고 previous가 정확히 하나면 이를 `e001/`로 복원하고, 공식 결과와 previous가 함께 있으면 previous를 invalidated 경로로 보존해요. previous가 둘 이상인 모호한 상태에서는 어떤 directory도 옮기지 않고 실패해요.

기존 `e001-run1-staging/` 또는 `e001-run2-staging/`은 존재하는 것만 각각 `e001-invalidated/distribution-staging-<runId>-{1|2}/`로 격리해요. 그다음 결정적 본 실행을 두 번 수행해 새 staging 두 개에 기록해요. 두 `checksums.sha256`가 byte-for-byte 같을 때만 첫 번째 checksum을 두 번째 staging의 `verification-run1-checksums.sha256`로 추가해 승격 후보를 만들어요. 다르면 기존 공식 출력을 건드리지 않고 두 staging을 보존한 채 실패해요.

승격 전 같은 parent filesystem에서 임시 directory의 `Files.move(..., ATOMIC_MOVE)` probe를 통과해야 해요. 기존 `e001/`이 없으면 두 번째 staging을 공식 경로로 atomic move해요. 기존 결과가 있으면 이를 `e001-previous-<runId>/`로 atomic move한 뒤 새 결과를 `e001/`로 atomic move해요. 두 번째 move가 실패하면 previous를 즉시 공식 경로로 복원하고 새 staging에 `failure.json`을 남겨요. 프로세스가 두 move 사이에 중단돼도 다음 실행의 첫 복구 단계가 previous를 복원해요. 새 승격이 성공한 뒤에만 previous 전체를 `e001-invalidated/distribution-<runId>/`로 옮겨 보존해요.

distribution `runId`는 path에만 쓰는 `SecureRandom` 16 byte 소문자 hexadecimal이고 결정적 파일 안에는 넣지 않아요. atomic move probe나 복원이 실패하면 기존·previous·staging을 삭제하지 않고 수동 복구가 필요한 실패로 보고해요.

checksum 비교가 성공하고 distribution outcome이 허용하면 `e001LocationPerformance`를 한 번 실행해요. 프로세스 중단이나 다른 작업으로 측정이 오염됐다고 판단하면 선택한 batch만 다시 재지 않고 전체 성능 실행을 `invalidated`로 보존한 뒤 한 번 새로 실행해요. 두 번째 실행도 유효하지 않으면 E를 채택하지 않아요. `e001BlindReview`는 유효한 성능 측정 뒤 한 번 실행해 비교 이미지와 빈 ballot을 만들고, 다섯 평가자는 그 뒤 `blind-responses.csv`를 별도로 작성해요.

## 산출물 계약

runner의 공식 출력은 Git에서 제외되는 `build/reports/experiments/e001/`이에요. 분포 runner는 앞 절의 두 staging에서 검증을 마친 두 번째 결과만 공식 경로로 이동해요. 실패하면 기존 공식 출력을 건드리지 않고 staging에 `failure.json`을 남겨요. 아래 `performance/`와 `blind/`는 `distributionOutcome=e-review-ready`일 때만 생기는 비결정적 sidecar예요.

```text
build/reports/experiments/e001/
├── manifest.json
├── coordinates.csv.gz
├── metrics.csv
├── sampler-failures.csv
├── conformance.csv
├── verification-run1-checksums.sha256
├── coordinator-only/
│   └── model-panels/
├── environment.json
├── checksums.sha256
├── performance/                         # 조건부 sidecar
│   ├── manifest.json
│   ├── environment.json
│   └── performance.csv
└── blind/                               # 조건부 sidecar
    ├── manifest.json
    ├── coordinator-only/
    │   └── blind-key.csv
    └── reviewer-package/                # 평가자에게 전달하는 유일한 경로
        ├── blind-pairs.csv
        ├── blind-ballot-template.csv
        ├── blind-responses.csv          # 평가 뒤 사람이 추가
        └── renders/
            └── blind-pairs/
```

root `manifest.json`의 `distributionOutcome`은 `inconclusive`, `selected-d`, `e-review-ready` 중 하나이고 `outcomeReason`은 `no-d`, `integrity-failure`, `no-e`, `e-confirmation-failed`, `e-spectral-failed`, `e-review-pending` 중 하나예요. 이 manifest는 분포 runner의 결정적 파일만 기술하고 checksum 승격 뒤 절대 수정하지 않아요.

performance와 blind task는 root의 manifest·좌표·지표·conformance·model panel·checksum을 수정하지 않고 자기 sidecar manifest에 run ID, 상태, 기계 생성 행·파일 개수와 무효화 이유를 기록해요. 사람이 나중에 추가하는 `reviewer-package/blind-responses.csv`는 sidecar manifest 개수에 포함하지 않아요. root와 performance의 `environment.json`은 각각 자기 task 환경만 기록해요.

각 평가자는 전달받은 package의 `blind-responses.csv`를 작성해 실행 담당자에게 반환하고, 담당자는 다섯 파일을 `blind/reviewer-package/blind-responses.csv` 하나로 합쳐요. 사람 응답을 runner가 만든 결정적 파일처럼 취급하지 않아요.

```text
blind-pairs.csv:
pair_id,scenario_id,image_path

blind-ballot-template.csv / blind-responses.csv:
reviewer_id,pair_id,shape_choice,natural_choice,hotspot_choice,status,invalidation_reason

blind-key.csv:
nonce_hex,pair_id,scenario_id,left_model_id,right_model_id
```

유효한 응답은 `status=valid`, 빈 `invalidation_reason`을 사용해요. 위에 정한 정보가 노출되면 기존 응답을 `status=invalidated`, `invalidation_reason=blind_exposed`로 바꾸고 해당 blind sidecar 전체를 아래 invalidated 경로로 옮긴 뒤 새 nonce로 네 쌍 전체를 다시 만들어요.

performance와 blind task는 각각 `e001-performance-staging-<runId>/`, `e001-blind-staging-<runId>/`에서 전체 sidecar를 완성해요. `runId`는 `SecureRandom` 16 byte의 소문자 hexadecimal이고 blind nonce와는 별개예요. 성공한 staging directory만 같은 filesystem의 `Files.move(..., ATOMIC_MOVE)`로 `e001/performance/` 또는 `e001/blind/`에 승격해요. atomic move를 지원하지 않으면 공식 sidecar를 바꾸지 않고 실패해요.

기존 sidecar, 중간 실패와 오염된 실행은 `build/reports/experiments/e001-invalidated/{performance|blind}-<runId>/`로 옮기고 이유를 그 sidecar manifest에 기록해요. 새 실행을 승격하기 전에 기존 공식 sidecar를 이 경로로 먼저 보존해요. 따라서 부분 생성 파일은 공식 `e001/` 아래에 나타나지 않아요.

`coordinates.csv.gz`는 다음 열을 고정 순서로 가져요.

```text
protocol_version,phase,model_id,parameter_set_id,scenario_id,
requested_scenario_count,requested_center_count,sample_seed,center_id,point_index,
center_easting_m,center_northing_m,
offset_easting_m,offset_northing_m,radius_m
```

`metrics.csv`는 long form으로 다음 열을 사용해요. 값이 정의되지 않으면 `value`를 비워 두고 `status=undefined`로 기록하며 해당 값을 요구하는 hard gate는 실패해요.

```text
protocol_version,phase,model_id,parameter_set_id,scenario_id,
sample_seed,metric_id,statistic,value,unit,status
```

`field-calibration-cal-41x41`의 `logIntensityStd`와 pooled 화면 밀도 metric처럼 sample seed가 없는 결정적 metric은 `sample_seed`를 빈 값으로 기록해요.

`sampler-failures.csv`는 헤더를 항상 만들고 failure가 없으면 데이터 행을 쓰지 않아요.

```text
protocol_version,phase,model_id,parameter_set_id,scenario_id,
sample_seed,center_id,point_index,sampler_id,attempt_limit
```

`performance/performance.csv`는 `model_id,parameter_set_id,phase,batch_index,points,elapsed_ns,ns_per_point`를 기록해요. D*와 E*를 모두 측정하고 `phase`는 `warmup` 또는 `measurement`예요. 각 batch의 point seed는 `E001-v1|performance|CAL|0|0|phase|batchIndex|pointIndex`의 SHA-256 앞 64bit를 사용해 고정해요. warmup도 원본 측정값으로 보존해요. 두 `environment.json`에는 해당 task의 JDK vendor·version, OS name·version·arch, CPU 수, Gradle version, locale·timezone, protocol·runner SHA와 Git dirty 여부만 기록해요. 사용자명, 기기명과 절대 경로는 넣지 않아요.

`conformance.csv`는 `conformance-cal-n320-per-model` scenario에서 다섯 sample seed마다 point index 0~63을 사용해 point seed, offset easting·northing의 IEEE-754 raw bit를 기록해요. `e-review-ready`이면 D*·E* 각 320개, `selected-d`이면 D* 320개만 기록하고 `inconclusive`이면 header만 가져요. 이 최대 640개 vector는 833,000개 분포 비교 표본에 포함하지 않아요. Unit 9~12의 운영 sampler는 채택 모델에 해당하는 이 vector를 그대로 통과해야 해요.

```text
model_id,parameter_set_id,sample_seed,point_index,point_seed_hex,
offset_easting_bits,offset_northing_bits
```

- 텍스트는 UTF-8·LF·`Locale.ROOT`을 사용하고 schema version과 숫자 표현 규칙을 manifest에 기록해요.
- 일반 decimal double 열은 `Double.toString`의 round-trip 표현을 사용하고 `-0.0`은 `0.0`으로 정규화해요. conformance의 raw bit 열은 signed zero를 포함한 실제 `Double.doubleToRawLongBits`를 보존해 소문자 16자리 hexadecimal로 써요.
- 좌표 행은 `phase`, `model_id`, `parameter_set_id`, `scenario_id`, `sample_seed`, `center_id`, `point_index` 순으로 정렬해요.
- metric과 failure 행도 schema의 식별자 열 순서로 정렬해요.
- conformance 행은 `model_id`, `parameter_set_id`, `sample_seed`, `point_index` 순으로 정렬해요.
- root `manifest.json`은 UTF-8·LF, 정렬된 key, 공백 없는 canonical JSON으로 기록하고 protocol·runner SHA, 전체 파라미터 전개, seed·중심·개수 배분, D*·E*, `distributionOutcome`, `outcomeReason`, checksum 대상의 row·file count만 포함해요.
- gzip mtime과 PNG metadata timestamp를 제거해요.
- 절대 경로와 실행 시각을 결정적 산출물에 넣지 않아요.
- `checksums.sha256`는 정렬된 상대 경로를 사용해요.
- `manifest.json`, 좌표·지표·failure·conformance CSV와 `coordinator-only/model-panels/`는 checksum 대상이에요.
- root와 sidecar의 `environment.json`, `performance/`, `blind/`, `e001-invalidated/`와 두 checksum 파일 자체는 checksum 대상에서 제외해요.
- Unit 7에서는 합성 좌표·수치·렌더·환경·성능·응답·checksum을 보존하고 결과 문서에서 프로토콜과 runner 커밋 SHA를 연결해요.

## 개인정보와 완료 조건

- 문서에 적힌 합성 중심 외의 좌표를 사용하지 않아요.
- 운영 DB, 운영 로그, 사용자 좌표, `requestId`, 한숨 ID와 기기 식별자를 읽거나 산출물에 기록하지 않아요.
- 정확한 사용자 위치나 클라이언트가 보낸 실제 격자 중심을 재구성하지 않아요.
- 결과는 모델과 파라미터를 제안할 뿐이며 팀 합의와 후속 ADR 없이 제품 계약을 바꾸지 않아요.
- 실험 실패와 기각 결과도 원인과 함께 보존해요.
