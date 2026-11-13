#844 개인 프레스 책임 분리 검증

상태 소유권
- PressRepositoryImpl: 앱 세션의 상태·버퍼·숫자·Job·타이머·API 호출 관리.
- PressReconciler: 처리 불명 쓰기의 확인/정리/유지, 전체 집계 보정과 오래된 응답 적용 판단.
- PressFlushPolicy: 첫 입력 대기·임계값·기존 쓰기 재개·재시도 시각 계산.
- 두 계산 객체는 StateFlow, CoroutineScope, API 호출을 소유하지 않는다.
- PressWriteState는 기존 PendingWrite와 WritePhase를 명시적인 내부 모델로 분리했다.

화면 구조
feature/screens/press/{PressRoute,PressScreen,PressViewModel,PressUiState,PressUiMapper}.kt
feature/screens/press/component/PressSummaryCard.kt
공용 EmotionPad/버튼/진동/애니메이션은 기존 위치와 동작을 유지했다.

데이터 구조
기존 PressRepositoryImpl/PressBatchAccumulator/PressRetryPolicy와 대응 테스트를
각각 data/repository/press 아래로 이동했다. DI와 테스트 import를 함께 변경했다.
도메인 패키지는 유지하고 PressBatch와 PressSendResult 선언만 파일로 분리했다.

단계별 확인
1. 기준: 기존 프레스·입력 테스트 67개 통과.
2. 화면·도메인 선언 분리: 67개 통과.
3. 데이터 구현·테스트 경로 이동: 67개 통과.
4. 전송 상태·집계 판단 분리: 기존 67개 + 경계 6개 통과.
5. 전송 시점 판단 분리: 기존 67개 + 경계 10개 통과.
모든 단계에서 BATCH_WAIT_PROBE/BURST_PROBE/LATENCY_PROBE 14행이 기준과 일치했다.
새 테스트는 감정별 확인, 잘못된 총계만의 증가, 세 번째 미확인 조회, 날짜 변경,
숫자 상한, 조회 시작 시점의 보정 범위, 오래된 집계, 첫 입력 대기/재시도 구분을 검증한다.
최종 전체 실행 결과와 기준 커밋은 validation.json에 기록했다.

유지한 계약
500ms 첫 입력 대기 / 감정별 30개·전체 100개 / 직렬 POST / 재시도 backoff /
처리 불명 복구 / 전체 집계 1초 예약 / 5초 조회 TTL / 날짜별 입력 분리 /
화면 재생성 시 세션 상태 표시. API 요청 DTO·설정·Gradle·의존성 버전은 변경하지 않았다.
오류 안내 UI, 프로세스 종료 후 영구 보관, 서버 중복 방지 계약은 추가하지 않았다.

재실행 (AN/Pheeeew에서)
  ./gradlew :shared:testAndroidHostTest ktlintCheck :androidApp:lintDebug \
    :androidApp:assembleDebug :shared:compileKotlinIosArm64
  python3 measurements/refactoring/844/verify.py \
    --current-results shared/build/test-results/testAndroidHostTest
저장된 기록만 비교:
  python3 measurements/refactoring/844/verify.py
raw/before와 raw/after는 Gradle 원본 XML이며 검증 스크립트는 원본을 수정하지 않는다.
기준 버전은 validation.json의 커밋에서 같은 *Press*Test / *Tap*Test 선택으로 재실행할 수 있다.
패키지 이동을 허용하기 위해 비교 시 테스트 클래스의 단순 이름과 테스트 이름을 사용한다.

범위와 한계
가상 시간·가짜 응답을 사용하는 기존 로직 테스트와 컴파일/lint 검증이다.
프로브 동일성은 해당 시나리오의 요청 수·묶음 크기·확인 지연·대기 등의 동일성이다.
모든 가능한 스케줄 또는 실기기 프레임 성능의 동등성을 보장하는 수치가 아니다.
요약 카드의 렌더링 본문은 선언 가시성과 공백을 제외하고 기준과 동일함을 별도로 비교했다.
기기 설치·화면·진동·접근성 직접 QA는 이번 실행에 포함하지 않았다. PNG는 추가하지 않았다.
전송·조회 워커의 추가 분리는 상태 동기화 복잡도를 늘릴 수 있어 이번에는 하지 않았다.
