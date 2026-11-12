# 구간 3~5와 달 그림 주문서 (테마 4개)

먼저 `docs/codex/_rules.md`(공통 규격)와 `docs/codex/nyc_art.md`, `docs/codex/xmas_art.md`(앞 구간 주문서), 그 결과물 `public/assets/nyc/`, `public/assets/xmas/`, `docs/codex/check_nyc.png`, `docs/codex/check_xmas.png`를 열어 봅니다. **같은 규격, 같은 그림체, 같은 완성도**로 테마 4개를 만듭니다. 캐릭터 그림체는 `refs/lineup.png`입니다.

게임은 아래에서 위로 오릅니다: 뉴욕 → 크리스마스 마을 → **구름 위 성 → 열기구 축제 하늘 → 로켓 발사대와 우주 → 달**. 올라갈수록 실제로 더 높은 곳이라 배경이 이어져 보여야 합니다(성 = 해 질 녘 구름 위, 열기구 = 맑고 높은 하늘, 우주 = 짙은 남색에 별, 달 = 검은 하늘과 지구).

## 테마별 내용
| 폴더 | 세계 | rock(지형 속) | fabric(미끄러운 경사) | trim(윗면 띠) | bg | 장식(kit) 소재 |
|---|---|---|---|---|---|---|
| `public/assets/castle/` | 구름 위 성 | 크림색·연회색 성벽 돌 | 파란 첨탑 지붕 기와 | 성벽 총안(낮게) + 살짝 얹힌 구름 | 분홍·금빛 구름 바다, 멀리 첨탑 | 깃발, 횃불, 담쟁이, 아치 창, 사슬, 구름 뭉치 |
| `public/assets/balloon/` | 열기구 축제 하늘 | 엮은 바구니(라탄) 결 | 알록달록 줄무늬 열기구 천 | 바구니 테두리 + 밧줄 | 파란 하늘, 흰 구름, 멀리 작은 열기구들 | 삼각 깃발 줄, 모래주머니, 밧줄 매듭, 연, 리본, 새 |
| `public/assets/space/` | 로켓 발사대와 우주 | 흰색·회색 금속 패널(리벳, 얇은 이음선) | 파란 태양 전지판 | 노란·검정 안전 줄무늬 난간(낮게) | 짙은 남색 우주, 별, 멀리 행성 | 안테나, 배관, 경고등, 케이블, 위성 접시, 작은 소행성 |
| `public/assets/moon/` | 달 | 밝은 회색 달 표토, 작은 구덩이 | 매끈한 회색 암석 | 달 먼지 가장자리 | 검은 하늘, 큰 지구, 별 | 깃발(글자·실제 국기 없음), 발자국, 구덩이, 바위 |

## 테마마다 만들 파일 (규격은 `_rules.md`)
- `bg.png`, `rock.png`, `fabric.png`, `trim.png`, `arrow_sign.png`
- `kit/top_1`~`top_6`, `kit/hang_1`~`hang_5`, `kit/side_1`~`side_4`, `kit/in_1`~`in_6`, `kit/far_1`~`far_3` (달은 top 4, hang 2, side 2, in 4, far 2로 줄여도 됩니다)
- 발판 장치 그림(각 512×160 PNG, 투명 배경, **y=20 가로줄이 캐릭터가 서는 윗면**, 좌우로 꽉 찬 덩어리, `public/assets/xmas/nod.png`와 같은 규격. 게임이 가로로 늘려 씁니다):
  - `castle/nod.png` 푹신한 구름 덩이, `balloon/nod.png` 작은 구름 덩이, `space/nod.png` 덜컹거리는 낡은 패널
  - `space/hot.png` 달궈진 엔진 판(주황·빨강으로 달아오른 금속, 윗면에 아지랑이 느낌. 한눈에 "뜨겁다")
  - `space/boost.png` 분사 판(아래로 불꽃을 뿜는 노즐이 달린 판, 윗면에 위쪽 화살표 무늬. 한눈에 "솟구친다")
  - `balloon/flag.png` 256×256 투명 배경: 바람에 **오른쪽으로** 나부끼는 삼각 깃발과 짧은 깃대(깃대 밑이 (40, 240)). 바람 방향 표시에 씁니다.

## 끝내기 전에
- `_rules.md`의 확인 절차를 테마마다 합니다. 확인 그림: `docs/codex/check_castle.png`, `check_balloon.png`, `check_space.png`, `check_moon.png`.
- 테마 순서대로(castle → balloon → space → moon) 하나를 끝내고 다음으로 갑니다. 한 테마의 bg, rock, fabric, trim 4장이 모두 있어야 게임이 그 폴더를 쓰기 시작합니다.
- 이 폴더(`pheeeew-climb`) 밖의 파일은 만들거나 고치지 않습니다(이미지 생성 도구의 `$CODEX_HOME/generated_images/` 자동 저장은 예외). `public/*.js`와 기존 PNG는 고치지 않습니다.
- 중간에 질문하지 말고 끝까지 진행합니다. 마지막 답에 테마별 파일 목록과 스스로 정한 것을 적습니다.
