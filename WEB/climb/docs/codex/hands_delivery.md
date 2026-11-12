**손 15장 교체·백업을 완료했습니다.** 아래 캐릭터 이름을 누르면 최종 비교 그림이 열립니다.

원본 픽셀을 추출하고, 몸 쪽 연결부에 약 20px의 투명도 전환을 적용했습니다. 비교 그림 5장을 직접 확인했고, 15장 모두 **1024×1024·모서리 알파 0·내부 투명 구멍 없음**을 검증했습니다.

좌표는 `(left, top, right, bottom)`이며, 반투명 가장자리를 포함한 **알파 > 0** 영역입니다. right·bottom은 제외 경계입니다.

| 캐릭터 / 비교 그림 | side_hand | front_hand_l | front_hand_r |
|---|---|---|---|
| [tired](docs/codex/check_hands_tired.png) | (390, 652, 576, 835) | (86, 648, 252, 819) | (772, 647, 940, 815) |
| [frustrated](docs/codex/check_hands_frustrated.png) | (382, 651, 571, 833) | (83, 645, 251, 807) | (771, 646, 942, 807) |
| [angry](docs/codex/check_hands_angry.png) | (438, 612, 634, 808) | (88, 643, 262, 813) | (760, 642, 935, 813) |
| [defeated](docs/codex/check_hands_defeated.png) | (368, 653, 562, 838) | (92, 645, 261, 816) | (758, 645, 933, 817) |
| [annoyed](docs/codex/check_hands_annoyed.png) | (376, 651, 563, 833) | (83, 645, 250, 807) | (772, 645, 941, 807) |

백업: [hands_backup](docs/codex/hands_backup) · 검사 기록: [validation.json](docs/codex/hands_work/validation.json)

다른 부위와 JS는 변경하지 않았습니다. 비교 그림에서 보이는 기존 몸통·발의 윤곽 및 질감 차이는 그대로 남아 있습니다.