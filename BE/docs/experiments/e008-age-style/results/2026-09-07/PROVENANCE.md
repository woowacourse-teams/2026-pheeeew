# E008 실행 출처

실행 전에 소스·입력 hash를 `source-before.sha256`에 기록했어요. 기반 HEAD는 `f3cdd00d89e803994f404c11b58bca5e9e490b2a`예요. E008 실행 코드·테스트·프로토콜은 미커밋이며 기존 E006/E007은 수정하지 않아요.

환경은 Temurin Java 21.0.5+11-LTS, macOS 26.6.2 (25G83), arm64, Java2D headless예요. 테스트 명령이 실행 소스를 컴파일해요. 실행 후 같은 6개 source/input hash를 대조해요. 두 실행의 PNG/GIF/CSV hash가 같아야 하며 다른 OS·폰트·GIF 인코더 버전의 바이트 일치까지 보장하지 않아요.

```bash
shasum -a 256 -c docs/experiments/e008-age-style/results/2026-09-07/source-before.sha256
java -Xmx1g -Djava.awt.headless=true -cp build/classes/java/test:build/resources/test com.pheeeew.sigh.experiment.e001.E008AgeStyleExperiment docs/experiments/e007-client-location/results/2026-09-07/raw/coordinates.csv.gz build/reports/experiments/e008-age-style
shasum -a 256 -c docs/experiments/e008-age-style/results/2026-09-07/source-before.sha256
```

결과의 `raw/`에는 run2 원본을, `run1-checksums.sha256`에는 run1 manifest를 보존해요. 원본 실험 좌표는 합성 위치이고 실제 사용자 데이터나 식별자를 사용하지 않았어요.
