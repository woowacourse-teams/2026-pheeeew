#799 단계별 실험 목차

1. batch-wait/README.md
   집계 조회 도중 입력의 첫 대기를 보존한 초기 후보 및 가상 시간 비교.
   초기 수치는 이후 POST 대기 보완·집계 합치기 적용 전 기록이다.
2. iphone/
   위 초기 후보의 iPhone 전후 원본 로그·CSV·실제 화면·분석 스크립트.
   최종 집계 합치기 구현의 실기기 결과로 사용하지 않는다.
3. post-wait/README.md
   POST 중 들어온 입력이 전체 대기를 다시 시작하던 문제의 후속 비교.
   XML/CSV와 device/, device-repeat/ 실기기 기록을 함께 보관한다.
4. aggregate-device/README.md
   POST 대기 보완이 공통 적용된 조건에서 집계 조회 합치기 전후를 비교한 자료.
   #799의 마지막 개선 단계에 해당하며, 현재 최신 코드 전체의 재측정은 아니다.

실행 기준은 AN/Pheeeew 디렉터리다. 원본 보존을 위해 출력은 별도 임시 폴더를 권장한다.
예: iPhone 저장 로그 재분석
  python3 measurements/personal-press/799/iphone/summarize_799_iphone.py \
    measurements/personal-press/799/iphone \
    measurements/personal-press/644-baseline/summarize_device.py /tmp/799-iphone-analysis
각 device 폴더의 analyze.py는 해당 comparison.csv만 다시 생성하고
원본 로그는 SHA256.json과 비교한다. README와 분석 코드 등 변경 가능한 항목은
원래 분석기의 검증 제외 정책을 유지하며 해시 목록을 덮어쓰지 않는다.
