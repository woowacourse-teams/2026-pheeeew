# E006 실행 출처

- 실행 기준 커밋은 `f3cdd00d89e803994f404c11b58bca5e9e490b2a`예요. E006 실행 소스·테스트·프로토콜은 미커밋이므로 이 커밋만으로 E006을 재현할 수는 없어요.
- `source-before.sha256`은 아래 재검증 실행 **전에** 기록한 소스 manifest예요. 실행 후 같은 manifest를 검사해 불변을 확인해요.
- 환경은 macOS 26.6.2 (25G83), arm64, Temurin OpenJDK 21.0.5+11-LTS, Java2D headless예요. 폰트·JDK·OS가 다른 환경의 PNG 바이트 일치까지 보장하지 않아요.
- 첫 탐색 출력 `build/reports/experiments/e006-virtual-center/`는 provenance 선기록 전에 실행했어요. 삭제·덮어쓰지 않고 남기며 아래 출처 고정 재검증 결과를 정식 기록으로 보존해요. 모델·seed·축척·매개변수·실행 코드는 바꾸지 않았어요.
- 재검증 출력은 `build/reports/experiments/e006-virtual-center-provenance/`예요. `raw/`에는 재검증 run2를 보존하고 run1 manifest도 별도 보존해요.

```bash
shasum -a 256 -c docs/experiments/e006-virtual-center/results/2026-09-07/source-before.sha256
java -Djava.awt.headless=true -cp build/classes/java/test:build/resources/test com.pheeeew.sigh.experiment.e001.E006VirtualCenterExperiment build/reports/experiments/e006-virtual-center-provenance
shasum -a 256 -c docs/experiments/e006-virtual-center/results/2026-09-07/source-before.sha256
```
