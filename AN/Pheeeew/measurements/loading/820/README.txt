#820 랭킹·그룹 로딩 고정 대기 제거

기준: an/dev 1d4de90f. 작업 브랜치: feat/820-ranking-group-loading.
환경: macOS 호스트, 기존 Gradle 설정의 :shared:testAndroidHostTest,
StandardTestDispatcher/TestCoroutineScheduler, 실제 제품 ViewModel + 가짜 Source.
네트워크·기기·실제 HTTP·픽셀 표시 단계 측정은 포함하지 않는다.
캐시는 새 ViewModel 상태에서 시작한다. 응답 지연 0/50/200/1500ms, 조건별 3회.
랭킹과 그룹 목록은 빈 성공 응답, 상세는 MEMBER 상세 + 빈 감정 목록 성공 응답이다.

문제와 변경
- 스탬프 랭킹 및 프레스 최초/필터/새로고침과 내 그룹 목록:
  withMinimumLoadingTime이 응답을 받아도 최소 1000ms까지 상태 반영을 보류했다.
  이 화면들의 래퍼만 제거했다. 공통 유틸리티와 다른 호출자는 변경하지 않았다.
- 그룹 상세: 150ms 후 보인 로딩 표시를 최소 300ms 유지하려고 상세 응답을 보류했다.
  상세 콘텐츠와 뒤이어 시작하는 감정 목록 조회도 같이 늦어졌다.
  결과를 즉시 적용하고 표시 타이머를 취소한다. 150ms 표시 지연은 유지한다.
- 프레스 날짜 이동의 150ms 요청 합치기, 요청 세대 검사, 중복 요청 방지,
  새로고침 중 기존 목록 유지, 그룹 가입 변경 후 무효화/재조회는 유지한다.
- 새 캐시나 TTL을 추가하지 않았다. 네트워크 요청 수 감소를 주장하지 않는다.

200ms 응답 기준 (3회 동일)
- 스탬프 최초/새로고침/이전 주, 프레스 최초/새로고침/감정 필터,
  그룹 최초/새로고침/가입 변경 후 재진입: 1000 -> 200ms.
- 그룹 상세와 감정 목록 요청 시작: 450 -> 200ms.
- 프레스 이전 주: 350 -> 350ms (150ms 합치기 유지).
- 변경 없는 그룹 홈 재진입: 0ms/0회 -> 0ms/0회.
- 각 읽기 Source 호출은 1회 -> 1회. 상세 감정 목록도 1회 -> 1회.
- 1500ms의 느린 응답은 빨라지지 않는다. 서버/네트워크 대기 자체는 그대로다.

측정 해석
comparison.csv의 ms는 행동 호출부터 테스트 스케줄러가 유휴 상태가 될 때까지의
가상 경과 시간이다. 해당 프로브에는 다른 장기 타이머가 없고 마지막에 Ready/Empty
상태를 확인한다. DETAIL_FEED_PROBE는 감정 목록 Source가 호출된 가상 시각이다.
API 시간은 가짜 Source에 넣은 delay이고 요청 수는 Source 호출 수이며 HTTP 수가 아니다.
렌더링 시간·실기기 첫 콘텐츠 프레임·메인 스레드 작업량을 측정하지 않았다.
3회 반복은 결정적 재현 확인이며 실기기 성능 분포/p95의 근거가 아니다.

원본과 재실행
raw/before-*.xml: 기준 코드에 측정용 테스트만 추가해 실행한 Gradle 원본 XML.
raw/after-*.xml: 같은 입력/지연 조건에서 고정 대기를 제거한 코드의 Gradle 원본 XML.
analyze.py는 원본을 수정하지 않고 156쌍의 조건과 Source 호출 수 동일 여부를 확인한 뒤
comparison.csv를 생성한다. 별도 해시 자동 덮어쓰기를 사용하지 않는다.

저장된 결과 재분석 (AN/Pheeeew에서):
  python3 measurements/loading/820/analyze.py
변경 후 재측정:
  ./gradlew :shared:testAndroidHostTest --tests '*ScreenLoadingProbeTest' \
    --tests '*GroupDetailViewModelTest.detailLoadingProbe' --rerun-tasks
결과는 shared/build/test-results/testAndroidHostTest/TEST-*.xml에 출력된다.
기준 재측정은 별도 깨끗한 1d4de90f 체크아웃에서:
  git apply <이 폴더의 절대 경로>/baseline-probes.patch
  cd AN/Pheeeew
  ./gradlew :shared:testAndroidHostTest --tests '*ScreenLoadingProbeTest' \
    --tests '*GroupDetailViewModelTest.detailLoadingProbe'
baseline-probes.patch는 기준 코드에 프로브만 추가하며 제품 로직을 변경하지 않는다.
현재 테스트는 응답 완료 시 즉시 상태 반영이라는 성능 회귀 기준도 검증한다.

검증과 관찰 한계
기존 빈 결과·실패 후 재시도·새로고침 콘텐츠 유지·중복 요청 방지·그룹 가입 변경·
프레스 연속 날짜 이동 테스트를 함께 실행한다. 최종 실행 개수는 validation.txt 참고.
새로고침/초기 로딩 표시가 빨리 사라지는 것이 의도한 변화다. 특히 상세의 150~450ms
응답에서는 표시된 스피너가 짧게 보일 수 있으므로 Android/iOS 시각 QA가 필요하다.
최초와 재진입은 ViewModel 상태 수명 기준이며 앱 내 실제 탭 전환/재생성 정책을
측정하지 않았다. 렌더링·실제 API 프로파일링은 이 자료로 대체하지 않는다.
PNG와 계측용 앱 진입점, 버전 변경은 없다.

추가 설치 확인
현재 수정본을 Xcode Debug로 빌드하여 연결된 iPhone 17 Pro에 설치·실행했다.
이 확인은 설치·실행 성공까지이며 화면별 로딩 시간 계측 또는 사용자 UI QA 결과가 아니다.
