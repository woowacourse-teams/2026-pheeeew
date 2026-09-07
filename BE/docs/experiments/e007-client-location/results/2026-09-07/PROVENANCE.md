# E007 실행 출처

실험 실행 전 이 문서와 `source-before.sha256`을 기록했어요. 기반 HEAD는 `f3cdd00d89e803994f404c11b58bca5e9e490b2a`이고 E007 프로토콜·실행 코드·테스트는 미커밋이에요. 커밋만으로 실행 소스를 식별하지 않고 manifest 10항목을 함께 사용해요. 기존 미커밋 E006은 변경하지 않았어요.

환경은 macOS 26.6.2 (25G83) arm64, Temurin OpenJDK 21.0.5+11-LTS, Java2D headless예요. 가까운 테스트가 실행 소스를 컴파일했으며 5개 통과, 실패·오류·건너뜀 0개였어요. 다른 OS·폰트 환경의 PNG 바이트 동일성을 보장하지 않아요.

```bash
shasum -a 256 -c docs/experiments/e007-client-location/results/2026-09-07/source-before.sha256
java -Djava.awt.headless=true -cp build/classes/java/test:build/resources/test com.pheeeew.sigh.experiment.e001.E007ClientLocationExperiment build/reports/experiments/e007-client-location
shasum -a 256 -c docs/experiments/e007-client-location/results/2026-09-07/source-before.sha256
```

새 출력 폴더 안에서 run1과 run2를 만들어요. 두 실행이 같으면 run2 원본을 `raw/`로 보존하고 run1 checksum을 별도 보존해요. 실제 위치·사용자 ID·운영 데이터는 입력하지 않아요.
