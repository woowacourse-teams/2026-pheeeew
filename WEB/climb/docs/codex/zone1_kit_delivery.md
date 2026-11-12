# 구간 1 가장자리 장식 납품 기록

## 결과

- `public/assets/zone1/kit/`: top 6장, hang 5장, side 4장, far 3장, 총 18 PNG.
- top/hang/side는 256×256 RGBA, far는 768×768 RGBA.
- `check_zone1_kit.png`: bg 위에 원본 rock을 반복한 900×300 덩어리, 50% 크기 가장자리 장식, 뒤쪽 far 3장. side 4종은 양쪽에 모두 배치해 반전도 확인했다.
- `zone1_kit_contact_sheet.png`: 18장 전체와 실제 투명도 확인용 모음.

## 판단과 처리

- 기존 rock의 평균 RGB(118.84, 141.09, 165.32)를 접합부 기준색으로 사용했다. 휴지·테이프도 같은 회청색 계열을 유지하고 별도 포인트색은 생략했다.
- 내장 image_gen으로 18종을 각각 생성했다. hang_3와 far_2에 남은 밝은 배경막은 같은 도구로 제거했다.
- 생성물이 지시 좌표를 정확히 따르지 않아 실제 알파 윤곽을 측정하고, 최종 자산에서 top y=200, hang y=56, side x=56에 재배치했다. 노출부 질감은 보존하고 묻히는 56px 구간은 지형색에서 투명으로 전환했다.
- 실측 노출 길이: top 26~66px, hang 54~157px, side 37~102px. top 가로 폭은 요청한 120~240px 범위에 들어간다. 보이는 윤곽의 중심도 중앙으로 맞췄다.
- far는 대비와 불투명도를 낮추고 윤곽을 부드럽게 처리했다. 확인 그림에는 약 420~580px 크기로 배치했다.

## 확인

- 두 확인 PNG를 직접 열어 검수했다. 첫 합성에서 발견한 hang_5의 밝은 접합 틈을 고쳤으며, 최종 합성에서 뚜렷한 접합선이나 밝은 테두리가 보이지 않았다.
- 18장의 크기, 투명 픽셀, 반투명 픽셀, 캔버스 외곽 alpha=0, 노출 길이, 기준선 연결, 묻히는 방향으로 단조 감소하는 alpha를 검사했다.
- 접합부와 rock 평균색의 채널별 절대 차이 평균은 모든 자산에서 1.58/255 이하였다.
- 기존 PNG의 SHA-256은 모두 유지됐다. 이 작업은 `public/*.js`를 쓰지 않았다. 작업 도중 외부 변경으로 `public/level.js`, `public/main.js`, `public/physics.js`의 해시 차이가 관찰되어 그대로 두었다.
- 게임 코드를 수정하거나 게임 내 무작위 배치 동작을 검증하지는 않았다. 납품 검증 범위는 이미지와 지정된 합성 장면이다.

## 재현 자료

- 생성 프롬프트와 편집 프롬프트: `zone1_kit_work/prompts.json`
- 선택한 생성 원본 경로: `zone1_kit_work/sources.json`
- 좌표 및 실측: `zone1_kit_work/validation.json`, `zone1_kit_work/placements.json`
- 픽셀 검증 결과: `zone1_kit_work/acceptance.json`
- 정규화·합성: `node tools/prepare_zone1_kit.cjs`
- 규격 검사·모음 생성: `node tools/verify_zone1_kit.cjs`
- 이미지 처리 의존성은 프로젝트 내부 `tools/.image-work/node_modules`에 설치했다.
