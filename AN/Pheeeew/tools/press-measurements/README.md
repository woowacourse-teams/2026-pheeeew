# 프레스 기준 측정 도구

이 브랜치는 #1114의 예비 실험에서 만든 계측 도구를 보관한다. 새로 합의한 **500회 × 3회 실험은 아직 실행하지 않았다.** 최신 실험 조건은 [#1114](https://github.com/woowacourse-teams/2026-pheeeew/issues/1114)에 있다.

## 남긴 내용

| 도구 | 확인하는 구간 | 한계 |
|---|---|---|
| `PressHttpInputMeasurements` | 입력 대기·HTTP 전송·응답·재시도 | 서버 저장 완료 시각과 다름 |
| `PressRaceMeasurements` | WS 수신·UI 적용·그리기, 화면 재진입·연결 복구 | 첫 응답이 알려진 변경을 포함하는지는 별도 확인 필요 |
| `EmotionTapDrawProbe` | 입력 처리 시작 → 반응 요소의 그리기 코드 실행 | 실제 패널 표시 완료가 아님. 인스턴스당 최대 128회 기록 |
| `DebugDisplayLinkCallbackDiagnostics` | iOS 화면 갱신 콜백 간격 | 실제 프레임 표시·누락 측정값이 아님 |
| `dev_connect_proxy.py` | 개발 서버 연결 터널 차단·복구 | 전체 인터넷 단절과 다름 |
| `measure_proxy_restore.py` | 호스트의 복구 신호 → 앱 로그 관측 | 로그 출력·파일 관측 지연 포함. 화면 복구 시간과 다름 |

공통 계측은 Debug에서 사용한다. iOS 프록시는 Debug·시뮬레이터·개발 서버에서 명시적으로 활성화한 경우에만 적용된다. 기존 UI 애니메이션과 입력 큐 정책을 최적화하는 브랜치가 아니다.

## 도구 실행

이 디렉터리에서 실행한다.

```sh
python3 dev_connect_proxy.py
python3 measure_proxy_restore.py --proxy-pid <프록시_PID> --log-file <앱_콘솔_로그>
```

- iOS 시뮬레이터 앱 실행 환경에 `PHEEEEW_DEBUG_CONNECT_PROXY=1`을 설정하면 개발 서버 HTTP/WS가 로컬 `127.0.0.1:18771` 프록시를 사용한다.
- 관측 도구에 `offline`을 입력하면 터널을 끊고, `restore`를 입력하면 연결을 허용한다.
- `quit`은 관측만 종료한다. 실험 후 앱 환경 변수를 해제하고 프록시도 종료한다.
- 화면 갱신 콜백 진단은 앱 환경 변수 `PHEEEW_DISPLAY_LINK_DIAGNOSTICS=1`로 켠다.
- 프록시는 TLS 내용을 복호화하지 않는다. 관측 시간은 한 호스트의 단조 시계로 계산한다.

## 이번 정리와 검사

- 삭제: 이전 #1114 예비 결과·스크린샷·로그, 기기와 좌표가 고정된 일회용 실행 스크립트, 오래된 실험 조건 초안.
- 보존: 앱 계측 코드·단위 테스트, 재사용 가능한 위 Python 도구와 테스트.
- #1052 별도 자료와 다른 작업의 파일은 삭제하지 않았다.
- Android `:shared:testAndroidHostTest`: 869개 통과, 실패 0개(2026-11-06 재실행).
- 복구 관측기 `--self-test`: 10개 통과.
- 프록시 로컬 테스트: 대상 제한·전달·단절·복구 검사 통과. 개발 서버 요청 없이 검사.
- Swift 진단 파일 구문 검사와 `git diff --check`: 통과.
- 이번 정리에서는 iOS 전체 빌드·실행 및 Release 검증을 다시 하지 않았다.

```sh
python3 measure_proxy_restore.py --self-test
PYTHONDONTWRITEBYTECODE=1 python3 test_dev_connect_proxy.py
# AN/Pheeeew 디렉터리에서:
./gradlew :shared:testAndroidHostTest
```

## 다음 실험 전 확인

- 탭 그리기 계측의 128회 한도와 각 도구의 기록 제한을 500회·예열·반복 조건에 맞게 조정한다. 이번 보관 커밋에는 새 조건 대응을 포함하지 않았다.
- 회차별 로그를 분리하고 입력 수·HTTP 요청 수·상태 수신 수·그리기 수를 따로 집계한다.
- Android와 iOS의 시작·종료 지점 및 계측 오버헤드를 확인한다.
- 기기 간 시계를 그대로 빼지 않는다. 방법과 오차가 확정되지 않은 지연은 미측정으로 기록한다.
- 새 실험은 같은 기기·조건에서 WS 기준부터 다시 측정한다. 과거 예비 결과를 새 조건의 결과로 사용하지 않는다.
