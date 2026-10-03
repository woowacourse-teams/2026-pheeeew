# #606 Android 핀 이미지 성능 측정 패키지

실기기 지도 핀 측정의 원본 데이터와 이를 분석한 문서를 분리해 보관한다.

## 하위 패키지

- [raw-measurements](raw-measurements/README.md): logcat 원본과 A/B/C 표본별 CSV
- [reports](reports/README.md): 기존 기준 측정 분석, 실험 계획, A/B/C 결과

## 최신 비교 결과

R8 최적화 Android 빌드에서 일반 핀 시나리오를 각 20회 측정했다. 요약은 [실험 결과](reports/experiment-results.md), 표본별 데이터는 [trial-results.csv](raw-measurements/trial-results.csv)에 있다.
