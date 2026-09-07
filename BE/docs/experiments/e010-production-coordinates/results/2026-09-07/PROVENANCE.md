# E010 실행 출처

- 제품 코드 기준 커밋: `325f656` (`feat: 지도 별 위치 반경 300m 균등 원 생성 적용`)이에요.
- 실험 실행기·검증기·프로토콜과 Gradle opt-in task는 실행 당시 미커밋이에요. `source-before.sha256` 10항목을 실행기 내부에서 좌표 생성 전에 기록하고 종료 시 다시 검사했어요.
- 입력은 보존된 E007·E009 합성 CSV이며 각각 해시로 식별해요. 표본 선택·원점·난수·그림 규칙은 상위 README에 고정했고 결과를 보고 바꾸지 않았어요.
- 새 두 경로는 실제 `PostgisSighLocationGenerator.generate()`와 실제 `SighRepository.findGeneratedLocation()`을 호출해요. 이전 사각형은 `325f656` 이전의 축별 `[-150,150)` 식을 테스트에서 재현한 비교군이에요.
- 재현 난수는 테스트 생성자를 통해 주입하고 제품의 공개 생성자·SecureRandom은 바꾸지 않았어요. 클라이언트와 지도 렌더링은 각각 수학식과 Java2D로 모의했어요.
- 환경: Java 21.0.5+11-LTS, macOS 26.6.2 aarch64, Testcontainers `postgis/postgis:17-3.5`예요. 실제 DB 버전·PostGIS·PROJ 상세는 environment.txt에 있어요.
- 두 실행은 새 디렉터리 `build/reports/experiments/e010-production-coordinates/run1/`, `run2/`에 생성했어요. run2를 raw로 복사하고 모든 파일 해시를 대조했어요. 기존 결과를 삭제하거나 덮어쓰지 않았어요.
- 검증기 실행: `ruby docs/experiments/e010-production-coordinates/verify-results.rb build/reports/experiments/e010-production-coordinates` — PASS예요. 입력·출력·통계 재계산은 쓰기 없는 Ruby 표준 라이브러리로 실행했어요.

환경·계산 일치 증거는 실제 사용자 위치의 정확도·프라이버시 또는 실제 앱 출시 검증을 대체하지 않아요.
