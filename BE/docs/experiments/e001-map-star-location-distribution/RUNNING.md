# E001-v2 실행 안내

판정 기준은 [v2 사전등록 프로토콜](PROTOCOL-V2.md)과 거기서 유지한 [v1 기준](README.md)을 따라요. 이 문서는 runner 사용법만 설명하며, 실행 성공을 모델 채택으로 해석하지 않아요. 제품 코드·DB·API는 바꾸지 않아요.

## 실행 전 확인

1. BE 디렉터리에서 Java 21과 저장소 Gradle wrapper를 사용해요. 실험 runner는 DB·네트워크를 사용하지 않아요. 처음 의존성을 준비하는 Gradle 다운로드는 별개예요.
2. 실험 코드·resource·Gradle 설정·프로토콜을 먼저 커밋해요. runner는 관련 미커밋 변경을 거절하고, v2 문서의 마지막 커밋과 실행 소스의 마지막 커밋을 각각 protocol·runner SHA로 기록해요. 동결한 v1 README의 blob ID도 확인해요.
3. R3 커밋 전에는 아래 단위 테스트와 dry-run만 해요. 실제 표본·후보 산점도·성능·블라인드는 R3 커밋 뒤 R4에서 실행해요. 각 dry-run 명령은 따로 실행해요. 여러 task 뒤에 옵션을 한 번만 붙이면 앞 task가 실제 실행될 수 있어요.

```bash
./gradlew test --tests 'com.pheeeew.sigh.experiment.e001.*Test' --no-daemon
./gradlew e001LocationDistribution --test-dry-run --rerun-tasks --no-daemon
./gradlew e001LocationPerformance --test-dry-run --rerun-tasks --no-daemon
./gradlew e001BlindReview --test-dry-run --rerun-tasks --no-daemon
```

기본 `test`에서는 `e001` tag를 제외해요. dry-run은 테스트를 선택하고 건너뛰므로, 실제 실험이나 테스트 본문 성공의 근거가 아니에요.

## R4 실제 실행 순서

1. `./gradlew e001LocationDistribution --rerun-tasks --no-daemon`을 실행해요. 두 실행의 결정적 checksum이 같아야 `build/reports/experiments/e001-v2/`로 승격해요. 기존 `e001/`과 v1 staging·archive·lock은 건드리지 않아요.
2. root `manifest.json`의 `distributionOutcome`을 확인해요. `inconclusive`면 여기서 결과를 기록하고 멈춰요. `d-review-ready`는 D만, `e-review-ready`는 D와 E가 다음 평가를 받을 수 있다는 뜻이에요. 아직 추천은 아니에요.
3. 두 review-ready 상태에서는 `./gradlew e001LocationPerformance --rerun-tasks --no-daemon`을 실행해요. `performance/manifest.json`의 `status=valid`는 측정 완료이지 합격이 아니에요. `dPerformanceGatePassed`와, E가 있으면 `ePerformanceGatePassed`를 확인해요. D는 15행, D/E는 30행이며 모델별 warmup 5회·measurement 10회예요. D 실패면 `inconclusive`로 멈추고, E만 실패하면 A/D 평가만 진행해요.
4. 유효한 측정에서 D가 통과하면 `./gradlew e001BlindReview --rerun-tasks --no-daemon`을 실행해요. runner가 같은 분포의 checksum·버전·성능 원본을 다시 검증해요. 평가자에게는 `blind/reviewer-package/` 복사본만 전달해요. 같은 다섯 명이 각각 전체 4쌍(A/D) 또는 8쌍(A/D와 D/E)에 독립 응답해요. 각자 같은 reviewer ID를 사용하고, 담당자가 응답을 `blind/reviewer-package/blind-responses.csv`로 합쳐요.
5. 완전하고 유효한 전체 20행 또는 40행을 받은 뒤에만 key를 열어 family별 판정과 근거를 결과 문서에 기록해요. `E001Blind.evaluate(assignment, responses)`의 `Review`와 사전등록 추천 표를 적용해요. 응답 누락·중복·오류는 `pending-review`로 보류해요. D가 A 대비 평가를 실패하면 E의 결과와 관계없이 `inconclusive`예요. D만 자격을 갖추면 `recommend-d`, D와 E 모두 자격을 갖추면 `recommend-e`예요. root manifest는 수정하지 않아요.

성능 입력 seed의 SHA-256 계산은 측정 구간 밖에서 해요. 측정 구간에는 점별 RNG 초기화, sampler 호출과 raw-bit 누적이 포함돼요. 성능 수치는 분포 checksum과 후보 정렬에 사용하지 않아요.

판정 함수에는 검증된 공식 package의 assignment만 전달해요. 해당 sidecar manifest의 checksum과 비공개 key를 확인한 뒤, key에 저장된 nonce와 family 구성으로 assignment를 복원하고 모든 pair·scenario·좌우가 key와 같은지 대조해요. 새 무작위 `assign()`을 호출해 배치를 바꾸지 않아요. 응답 CSV는 header가 `BALLOT_HEADER`와 같은지 확인하고 header를 제외한 행을 `evaluate`에 넘겨요. `Review.recommendation()`은 이 package를 만들 때 확인한 수치·성능 자격을 전제로 하는 블라인드 판정이며, 임의의 assignment에서 얻은 값을 최종 추천으로 쓰지 않아요.

모든 응답이 모이기 전에는 모델명이 붙은 panel·좌우 key·nonce·family명을 평가자에게 보여주지 않아요. A/D는 `REVIEW_AD`, D/E는 `HOLDOUT`의 서로 다른 표본을 사용해요. 공개 목록에는 family를 드러내지 않는 조건 ID만 있어요. 전후 산점도도 이 블라인드 조건을 지킨 뒤 공개해요. 내부 다섯 명의 평가를 일반 사용자 선호나 E>A 직접 검증으로 확대 해석하지 않아요.

`boundary-observations.csv`는 trial마다 seed별 다섯 행과 pooled 한 행의 실제 관측을 기록해요. 비어 있는 띠의 밀도비는 빈 필드이며 합격·탈락 기준이 아니에요. 이 CSV를 포함한 여섯 필수 파일과 8장 또는 16장의 원본 panel이 분포 checksum에 포함돼요. 실제 앱의 여러 격자·줌 합성 화면 확인은 이후 PR 전 절차로 남아요.

## 무효화와 복구

- 단순 재실행은 기존 유효 sidecar를 덮어쓰지 않아요. root의 결정적 파일은 sidecar task가 변경하지 않아요.
- 성능이 다른 작업으로 오염됐을 때만 `./gradlew e001LocationPerformance --rerun-tasks --no-daemon -Pe001InvalidationReason=measurement_contaminated`를 사용해요. 전체 실행을 보존·무효화한 뒤 한 번만 재측정해요. 동일 checksum 분포의 archive까지 실행 횟수를 세며, 두 번째 실행도 무효하면 세 번째 측정 없이 `inconclusive`예요. 시간 조건 실패 자체를 오염으로 취급해 재도전하지 않아요.
- 블라인드 정보가 노출되면 `./gradlew e001BlindReview --rerun-tasks --no-daemon -Pe001InvalidationReason=blind_exposed`를 사용해요. 이전 4쌍 또는 8쌍과 응답 원본을 보존하고, 부분 family가 아닌 응답 전체에 무효 상태를 기록한 뒤 새 nonce로 다시 만들어요.
- 중단·실패 결과는 `build/reports/experiments/e001-v2-invalidated/`에 보존해요. 잠금 충돌, 모호한 이력이나 atomic move 실패를 무시하거나 staging·archive를 삭제해서 통과시키지 않아요. 오류를 확인한 뒤 보존된 경로를 기준으로 복구해요.
- 블라인드 평가가 시작된 뒤에는 성능 결과를 교체하지 않아요. 분포를 다시 승격해 이전 평가가 archive로 옮겨진 경우에도 기존 평가와 실행 이력을 먼저 확인해요.
