[전체 확인 그림을 열어보세요.](docs/codex/check_sky_sequence.png) **4개 테마, PNG 112개를 완성했습니다.**

각 폴더에 `bg.png`, `rock.png`, `fabric.png`, `trim.png`, `arrow_sign.png`가 있습니다.

| 폴더 | `kit/` 파일 범위—all PNG | 추가 파일 | 합계 |
|---|---|---|---:|
| `castle/` | top_1~6, hang_1~5, side_1~4, in_1~6, far_1~3 | nod.png | 30 |
| `balloon/` | 위와 동일 | nod.png, flag.png | 31 |
| `space/` | 위와 동일 | nod.png, hot.png, boost.png | 32 |
| `moon/` | top_1~4, hang_1~2, side_1~2, in_1~4, far_1~2 | 없음 | 19 |

반복 타일과 테마별 합성 그림을 직접 확인했고, 해상도·투명도·기준선 검사도 통과했습니다.

스스로 정한 사항은 다음과 같습니다.

1. **배경 흐름:** 분홍·금빛 구름 → 맑은 파랑 → 짙은 남색 → 검은 하늘과 지구.
2. **달:** 허용된 축소 구성을 사용하고, 깃발은 무늬 없는 연보라색으로 제작.
3. **장치:** 새 주문의 512×160·윗면 y=20을 우선. 열기는 주황·빨강, 분사는 위쪽 화살표와 아래쪽 불꽃으로 표현.

[전체 파일 목록·프롬프트·검수 기록](docs/codex/sky_assets.md)에 정리했습니다. 기존 PNG와 `public/*.js`에는 이 작업에서 쓰지 않았습니다.