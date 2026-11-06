# #1052 활동 표시등 실험 도구

이전 활동 표시등 비교 실험에서 재사용할 소스와 실행 도구를 보관한다. 기존 결과·로그·스크린샷은 삭제했다. #1114의 전체 프레스 수신 방식 비교 도구와 별개다.

## 구성

| 파일 | 용도 |
|---|---|
| `Activity1052Experiment.common.kt.txt` | 기준/후보 표시등과 재구성 횟수 계측 |
| `Activity1052Experiment.ios.kt.txt`, `ContentView.experiment.swift.txt` | 시뮬레이터 실험 진입점 예제 |
| `run.py` | 시뮬레이터 앱을 실행하고 프로세스 CPU·재구성 횟수 수집 |
| `visual-check.py`, `visual-check.swift` | 스크린샷 중앙 영역 변화 확인. CPU 측정과 별도로 실행 |
| `device/*.kt.txt`, `device/ContentView.swift.txt` | 실기기 계측·진입점·표시등 전환 예제 |
| `device/PressActivityLiveIndicator.product.kt.txt` | 실기기 실험 당시 비교 대상 소스. 현재 Git 파일과 달라 보존 |
| `device/run-device.py` | 설치된 실기기 실험 앱 실행·결과 수집 |
| `device/cause/analyze.py` | xctrace Time Profiler XML 표본 분석 |

`.txt` 소스는 앱 빌드에 자동 포함되지 않는다. 현재 코드와 호환성을 확인하고 별도 실험 작업 공간에서 연결해야 한다. 기존 앱 소스를 무조건 덮어쓰지 않는다.

## 실행 준비와 사용법

1. 시뮬레이터는 common/ios 실험 소스와 Swift 진입점 예제를 연결해 별도 Debug 앱을 빌드·설치한다.
2. 실기기는 `device/`의 계측 소스·Swift 진입점·표시등 전환 함수를 연결해 서명한 실험 앱을 설치한다.
3. 설치한 앱의 기기 ID와 bundle ID를 지정한다. 스크립트는 해당 앱을 종료·재실행한다.

```sh
# 이 디렉터리에서 실행
python3 run.py --device <시뮬레이터-UDID> --bundle-id <앱-bundle-ID>
python3 visual-check.py --device <시뮬레이터-UDID> --bundle-id <앱-bundle-ID>
python3 device/run-device.py --device <실기기-ID> --bundle-id <앱-bundle-ID> --phase isolated
python3 device/run-device.py --device <실기기-ID> --bundle-id <앱-bundle-ID> --phase full
python3 device/cause/analyze.py <Time-Profiler-XML> <출력-JSON>
```

- macOS·Xcode가 필요하다. `analyze.py`는 원래 실험에서 사용한 XML 열 구조를 전제로 하므로 다른 스키마에서는 조정해야 한다.
- CPU는 앱 프로세스 기준이며 100%는 한 코어다. 화면 전체·배터리 개선율로 해석하지 않는다.
- 표시등 비교·프로파일 수집용 도구이며 새 #1114의 500회 × 3회 시나리오를 실행하지 않는다.
- 수집한 로그·결과·스크린샷은 Git에서 제외한다.

## 정리 시 검증

- Python 스크립트 구문 검사 및 실행기 3개의 `--help` 검사.
- Swift 이미지 분석 도구 구문 검사.
- 앱 빌드·기기 실행·새 성능 측정은 이번 정리 범위에 포함하지 않는다.
- Git으로 복원 가능한 원본 백업 3개는 제거했다. 이전 Swift 원본은 커밋 `946946e69`, 표시등 기준 소스는 `64e9df0b2`에서 확인할 수 있다.
