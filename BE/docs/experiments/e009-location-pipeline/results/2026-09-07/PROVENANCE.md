# E009 실행 출처

기반 HEAD는 f3cdd00d89e803994f404c11b58bca5e9e490b2a예요. E009은 미커밋 실험이고 기존 E006/E007/E008은 수정하지 않아요. Temurin Java 21.0.5+11-LTS, macOS 26.6.2 (25G83), arm64에서 Java2D headless로 실행해요.

사용자 정정에 따라 2번을 클라이언트 300m + 서버 300m로 바꾼 뒤 테스트 5개가 통과했어요. 그 소스·사전 프로토콜·sprite·E007 원본 6개 hash를 source-before.sha256에 실행 전에 기록했어요. 테스트 명령이 이 소스를 컴파일했어요.

```bash
./gradlew test --tests 'com.pheeeew.sigh.experiment.e001.E009LocationPipelineExperimentTest' --no-daemon
shasum -a 256 -c docs/experiments/e009-location-pipeline/results/2026-09-07/source-before.sha256
java -Xmx1g -Djava.awt.headless=true -cp build/classes/java/test:build/resources/test com.pheeeew.sigh.experiment.e001.E009LocationPipelineExperiment docs/experiments/e007-client-location/results/2026-09-07/raw/coordinates.csv.gz build/reports/experiments/e009-location-pipeline
shasum -a 256 -c docs/experiments/e009-location-pipeline/results/2026-09-07/source-before.sha256
```

새 출력 폴더에서만 실행해요. run2 원본은 raw/에, run1 hash는 run1-checksums.sha256에 보존해요. 플랫폼·폰트·Java2D 버전이 바뀐 환경의 PNG 바이트 일치까지 보장하지 않아요. 합성 좌표만 사용하며 실제 위치를 네트워크로 전송하지 않아요.
