# annoyed 점프 자세 검수

- built-in image_gen으로 생성했다. 전체 초기 프롬프트와 수정 지시는 annoyed_prompts.json에 기록했다.
- 정면 기준 몸통 폭: 794px. 허용 범위: 754.3~833.7px.
- 몸통 폭은 손발을 제외하고 보이는 몸통 외곽선 구간으로 측정했다. 가려진 외곽선은 추정하지 않았다. 구간과 배율은 annoyed_normalization.json 및 annoyed_measurements.json에 기록했다.
- 폭 ±5%를 우선해 눌림의 옆 퍼짐을 제한했다. 눌림 정도는 splat > charge > land이며, rise/fall은 세로로 조금 늘렸다.
- 비대칭 자세의 가로 정렬은 주문서 최종 검사 기준인 전체 알파 영역의 중심을 사용했다.
- 생성본의 알파 1~7 잔여값만 0으로 정리한 뒤 종횡비를 유지해 크기를 맞추고 정수 픽셀 이동으로 정렬했다. 최종 위치 측정은 알파가 0보다 큰 모든 픽셀을 포함한다.
- 7장 모두 1024×1024 RGBA, 네 모서리 알파 0, 최하단 y=900.
- refs 4장을 먼저 열었고, 회색 배경의 check_annoyed.png를 직접 열어 비교했다. 비율이 달랐던 rise, charge, apex, fall, wall을 재생성했다.
- 작업 시작 시 존재하던 PNG는 모두 해시가 유지됐다. 이 작업은 public/*.js에 쓰기 작업을 하지 않았다. 작업 중 main.js와 physics.js에서 외부 변경을 감지했으며 되돌리지 않았다.

| 파일 | 최하단 y | 가로 중심 x | 가시 몸통 폭(px) |
|---|---:|---:|---:|
| jump_charge.png | 900 | 511.5 | 824 |
| jump_rise.png | 900 | 511.5 | 780 |
| jump_apex.png | 900 | 512.0 | 794 |
| jump_fall.png | 900 | 512.0 | 770 |
| jump_land.png | 900 | 512.0 | 810 |
| jump_wall.png | 900 | 511.5 | 784 |
| jump_splat.png | 900 | 512.0 | 820 |
