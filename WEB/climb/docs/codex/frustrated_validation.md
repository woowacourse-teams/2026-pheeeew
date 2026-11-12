# frustrated 점프 자세 검수

- 생성: built-in image_gen. 초기 프롬프트는 frustrated_prompts.json, 수정 프롬프트는 frustrated_revision_prompts.json, 최종 원본 경로는 frustrated_sources.json에 기록.
- 저장: public/assets/characters/frustrated/jump_*.png 7장.
- 7장 모두 1024×1024 RGBA, 네 모서리 알파 0, 알파 영역 최하단 y=900.
- 원본 정면의 손발을 제외한 가시 몸통 폭 794px(세로 구간 500~649). 허용 범위 754.3~833.7px.
- 각 자세도 손발을 제외한 가시 몸통 외곽선 구간으로 측정. 구간·배율·알파 경계는 frustrated_measurements.json에 기록. 가려진 몸통 윤곽은 추정하지 않음.
- 눌림 정도는 splat > charge > land. 큰 폭 확장과 부피 인상 유지가 충돌하는 구간은 명시된 몸통 폭 ±5%를 우선함.
- 좌우 비대칭 포즈는 주문서 최종 검사에 따라 전체 알파 영역의 가로 중심을 x=512에 정렬.
- 원본에 머리 기호가 없으므로 추가하지 않음. 고유의 찡그린 눈썹, 감은 눈, 지그재그 입, 살구색과 볼을 유지.
- 이미지 생성으로 자세를 수정했고, 이후 종횡비를 유지한 크기 조정과 정수 픽셀 이동으로 정렬. 생성본의 알파 1~7 잔여 픽셀은 0으로 정리.
- check_frustrated.png는 원본+7장을 같은 배율로 나란히 놓은 회색 배경 비교본. 직접 열어 선·색·표정·크기를 검토하고 apex/fall/wall 등의 비율 수정 후 재검수.
- 시작 시 존재한 PNG 69개의 SHA-256 해시 유지 확인. 이 작업은 public/*.js에 쓰지 않았음. 작업 도중 main.js, physics.js, level.js의 외부 변경이 관찰되어 그대로 보존함.

| 파일 | 최하단 y | 가로 중심 x | 가시 몸통 폭 px |
|---|---:|---:|---:|
| jump_charge.png | 900 | 511.5 | 825 |
| jump_rise.png | 900 | 512.0 | 775 |
| jump_apex.png | 900 | 512.0 | 795 |
| jump_fall.png | 900 | 512.0 | 774 |
| jump_land.png | 900 | 512.5 | 800 |
| jump_wall.png | 900 | 512.0 | 780 |
| jump_splat.png | 900 | 512.5 | 826 |
