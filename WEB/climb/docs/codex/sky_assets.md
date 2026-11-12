# 구간 3~5와 달 아트 결과

castle → balloon → space → moon 순서로 제작·검수했다. 게임용 PNG는 총 112개다. [전체 비교](check_sky_sequence.png)

## 테마별 파일 목록

모든 경로는 `public/assets/` 기준이며, 아래 범위의 파일은 모두 `.png`다.

| 폴더 | 공통 기본 5장 | kit 파일 | 추가 장치 | 합계 | 확인 그림 |
|---|---|---|---|---:|---|
| castle/ | bg, rock, fabric, trim, arrow_sign | top_1~6, hang_1~5, side_1~4, in_1~6, far_1~3 | nod | 30 | [check_castle.png](check_castle.png) |
| balloon/ | bg, rock, fabric, trim, arrow_sign | top_1~6, hang_1~5, side_1~4, in_1~6, far_1~3 | nod, flag | 31 | [check_balloon.png](check_balloon.png) |
| space/ | bg, rock, fabric, trim, arrow_sign | top_1~6, hang_1~5, side_1~4, in_1~6, far_1~3 | nod, hot, boost | 32 | [check_space.png](check_space.png) |
| moon/ | bg, rock, fabric, trim, arrow_sign | top_1~4, hang_1~2, side_1~2, in_1~4, far_1~2 | 없음 | 19 | [check_moon.png](check_moon.png) |

정확한 112개 상대 경로·크기·용량·SHA-256은 [final_manifest.json](sky_work/final_manifest.json)에 있다.

## 스스로 정한 사항

1. **높이와 색의 흐름:** 성은 크림 돌과 분홍·금빛 구름, 열기구는 라탄과 맑은 하늘, 우주는 밝은 금속과 짙은 남색, 달은 회색 표토와 검은 하늘로 구분했다. 구름·멀리 보이는 물체는 낮은 대비를 유지했다.
2. **경사 재질:** 성은 파란 유약 기와, 열기구는 산호·노랑·청록 천 줄무늬, 우주는 파란 태양 전지판, 달은 표토보다 어두운 매끈한 회색 암석이다. 얇은 사선 광택으로 미끄러운 면을 표현했다.
3. **달 축소 구성:** 허용된 top 4 / hang 2 / side 2 / in 4 / far 2를 선택했다. 깃발은 글자·문양 없는 연보라색이며 짧은 깃대와 가로 지지대를 사용했다. 원경은 낮은 산등성이와 구덩이 지형이다.
4. **장치:** 모든 nod/hot/boost는 새 주문의 512×160, y=20 윗면을 우선했다. hot은 빨강·주황 금속과 아주 옅은 아지랑이, boost는 위쪽 화살표와 아래쪽 분사 불꽃이다. balloon/flag는 오른쪽으로 뻗는 산호색 삼각 깃발이며 깃대 밑은 (40,240)이다.
5. **배치와 혼합:** 장식의 지형 접점은 해당 rock 평균색으로 맞추고 투명하게 흐리게 했다. 바닥 캐릭터는 refs/lineup.png의 파란 캐릭터를 원본 비율로 75px 폭에 배치했다. 확인 그림에서 달 원경은 지형 뒤에 낮게 배치했고, 배경은 세로 반복 전용이라 지구가 가로로 중복되지 않게 했다.

## 검수

각 테마에서 rock/fabric 2×2, trim 3×1, bg 1×2를 실제 이미지로 열어 확인했다. 파일은 `THEME_tile_rock.png`, `THEME_tile_fabric.png`, `THEME_tile_trim.png`, `THEME_tile_bg.png`다. `THEME_contact_sheet.png`는 개별 자산 모음이다.

확인 그림은 900×300 지형, 절반 크기 장식, 원경, 75px 캐릭터, 오른쪽 경사 삼각형을 포함한다. 생성 과정에서 생긴 새 장식의 사각 배경, 장식 비율, 기준선, 반복 무늬의 어긋남을 수정한 뒤 다시 확인했다.

| 테마 | 규격 검사 | 결과 |
|---|---:|---|
| castle | 17,503 | 통과 |
| balloon | 18,018 | 통과 |
| space | 17,511 | 통과 |
| moon | 11,322 | 통과 |

검사 대상은 파일 개수·해상도·알파·투명 여백·부착 기준선·장식 높이/폭·발판 윗면·표지 밑점·반복 경계다. rock/fabric 상하좌우, trim 좌우, bg 상하의 경계 픽셀 차이는 0이다. 시각 검수는 별도로 수행했다. 실행 중인 게임의 동작 검증을 뜻하지는 않는다.

## 생성 및 재현 기록

이미지 원화는 built-in image_gen으로 자산별 생성했다. Sharp로 크기, 알파, 색조, 반복 경계, 좌표를 정규화하고 검수 합성 이미지를 만들었다.

- 최종 생성 프롬프트: [castle](sky_work/castle_prompts.json), [balloon](sky_work/balloon_prompts.json), [space](sky_work/space_prompts.json), [moon](sky_work/moon_prompts.json).
- 선택한 원화 경로: `sky_work/THEME_sources.json`. 재생성 프롬프트와 교체 내역은 같은 폴더의 `*_revision.json`에 기록했다.
- 크롭·비율·기준선 조정: `sky_work/THEME_overrides.json`.
- 파일별 규격과 배치: `sky_work/THEME_validation.json`, `THEME_placements.json`, `THEME_checks.json`.
- 준비·합성 도구: `tools/prepare_sky.cjs`, 반복 미리보기: `tools/preview_sky_tiles.cjs`, 독립 규격 검사: `tools/verify_sky.cjs`.

이 작업의 쓰기 대상은 새 테마 네 폴더, 새 검수 그림, sky_work 기록과 위 보조 도구다. `public/*.js`와 기존 PNG에는 이 작업에서 쓰지 않았다. 시작 시점 285개 기존 파일의 해시 비교에서 NYC 배경·원경 4장과 public의 JS 5개에 동시 작업 중 변경이 감지됐다. 해당 파일은 되돌리거나 덮어쓰지 않았으며 감지 목록은 moon_validation.json의 originalsChanged에 남겼다.
