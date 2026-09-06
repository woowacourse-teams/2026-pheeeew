# E001 실행 안내

판정 기준은 [사전등록 프로토콜](README.md)을 따라요. 이 문서는 runner 사용법만 설명하며, 실행 성공을 모델 채택으로 해석하지 않아요.

## 실행 전 확인

1. BE 디렉터리에서 Java 21과 저장소 Gradle wrapper를 사용해요. 실험 runner는 DB·네트워크를 사용하지 않아요. 처음 의존성을 준비하는 Gradle 다운로드는 별개예요.
2. 실험 코드·resource·Gradle 설정·프로토콜을 먼저 커밋해요. runner는 관련 미커밋 변경을 거절하고, 해당 경로의 마지막 커밋을 protocol·runner SHA로 기록해요.
3. Unit 6에서는 아래 검증만 해요. 각 dry-run 명령은 따로 실행해요. Gradle의 task 옵션을 여러 task 뒤에 한 번만 붙이면 앞 task가 실제 실행될 수 있어요.

```bash
./gradlew test --tests 'com.pheeeew.sigh.experiment.e001.*Test' --no-daemon
./gradlew test --test-dry-run --no-daemon
./gradlew e001LocationDistribution --test-dry-run --no-daemon
./gradlew e001LocationPerformance --test-dry-run --no-daemon
./gradlew e001BlindReview --test-dry-run --no-daemon
```

기본 `test`에서는 `e001` tag를 제외해요. dry-run은 테스트를 선택하고 건너뛰므로, 실제 실험이나 테스트 본문 성공의 근거가 아니에요.

## Unit 7 실행 순서

1. `./gradlew e001LocationDistribution --rerun-tasks --no-daemon`을 실행해요. 두 번 생성한 결정적 checksum이 같아야 `build/reports/experiments/e001/`로 승격해요.
2. root `manifest.json`의 `distributionOutcome`을 확인해요. `e-review-ready`가 아니면 성능·블라인드 명령을 실행하지 않아요.
3. `e-review-ready`이면 `./gradlew e001LocationPerformance --rerun-tasks --no-daemon`을 실행해요. `performance/manifest.json`의 `status=valid`는 전체 실행이 완성됐다는 뜻이고, 시간 합격 여부는 별도 `performanceGatePassed`로 확인해요.
4. 유효한 성능 측정 뒤 `./gradlew e001BlindReview --rerun-tasks --no-daemon`을 실행해요. 평가자에게는 `blind/reviewer-package/`의 복사본만 전달해요. 각 평가자는 네 행의 빈 응답지에 같은 reviewer ID를 적고 응답해요. 담당자는 다섯 명의 응답을 `blind/reviewer-package/blind-responses.csv`로 합쳐요.

성능 입력 seed의 SHA-256 계산은 측정 구간 밖에서 해요. 측정 구간에는 점별 RNG 초기화, sampler 호출과 raw-bit 누적이 포함돼요. 성능 수치는 분포 checksum과 후보 정렬에 사용하지 않아요.

모든 응답이 모이기 전에는 모델명이 붙은 panel·좌우 key·nonce를 평가자에게 보여주지 않아요. 전후 산점도도 이 블라인드 조건을 지킨 뒤 공개해요. 다섯 명 미만의 응답으로 E 채택을 결정하지 않아요.

## 무효화와 복구

- 단순 재실행은 기존 유효 sidecar를 덮어쓰지 않아요. root의 결정적 파일은 sidecar task가 변경하지 않아요.
- 성능이 다른 작업으로 오염됐을 때만 `./gradlew e001LocationPerformance --rerun-tasks --no-daemon -Pe001InvalidationReason=measurement_contaminated`를 사용해요. 전체 실행을 보존·무효화한 뒤 한 번만 재측정해요. 동일 checksum 분포의 archive까지 실행 횟수를 세며, 두 번째 실행도 무효하면 세 번째 측정을 시작하지 않아요.
- 블라인드 정보가 노출되면 `./gradlew e001BlindReview --rerun-tasks --no-daemon -Pe001InvalidationReason=blind_exposed`를 사용해요. 이전 네 쌍과 응답 원본을 보존하고, 응답 전체에 무효 상태를 기록한 뒤 새 nonce로 네 쌍을 다시 만들어요.
- 중단·실패 결과는 `build/reports/experiments/e001-invalidated/`에 보존해요. 잠금 충돌, 모호한 이력이나 atomic move 실패를 무시하거나 staging·archive를 삭제해서 통과시키지 않아요. 오류를 확인한 뒤 보존된 경로를 기준으로 복구해요.
- 블라인드 평가가 시작된 뒤에는 성능 결과를 교체하지 않아요. 분포를 다시 승격해 이전 평가가 archive로 옮겨진 경우에도 기존 평가와 실행 이력을 먼저 확인해요.
