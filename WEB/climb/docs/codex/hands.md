# 감정이 손 부위 그림 다시 만들기 주문서

대상 캐릭터 id 5개: `tired`, `frustrated`, `angry`, `defeated`, `annoyed`. 아래 `{ID}`를 각 id로 바꿔 5번 반복합니다.

## 문제
게임은 캐릭터를 부위 그림으로 나눠 겹쳐 그립니다. 지금 손 부위 그림이 원본 캐릭터 그림과 다릅니다.
- 원본(`refs/{ID}/side.png`): 손은 몸통 아래쪽에서 **몸에서 볼록 솟은 덩어리**입니다. 외곽선이 한 바퀴 닫혀 있지 않습니다. 앞쪽 위에서 시작해 아래를 돌아 뒤쪽 위에서 끝나고, **위쪽은 선 없이 몸통 색으로 이어집니다.** 손 아래쪽은 발 위에 얹혀 있습니다.
- 지금(`public/assets/characters/{ID}/side_hand.png`): 외곽선이 완전히 닫힌 동그란 공입니다. 몸통 위에 따로 붙인 단추처럼 보입니다.
- 원본(`refs/{ID}/front.png`): 손은 몸통 양옆 아래에 있는 둥근 공입니다. 바깥쪽에만 외곽선이 있고, 몸 쪽은 몸통 외곽선의 짧은 호가 손 위로 살짝 넘어옵니다.
- 지금(`front_hand_l.png`, `front_hand_r.png`): 원본보다 작고, 외곽선이 닫힌 기울어진 달걀 모양입니다.

## 할 일
캐릭터마다 아래 3장을 다시 만들어 **같은 파일 이름으로 덮어씁니다.**
- `public/assets/characters/{ID}/side_hand.png`
- `public/assets/characters/{ID}/front_hand_l.png` (화면 왼쪽 손)
- `public/assets/characters/{ID}/front_hand_r.png` (화면 오른쪽 손)

덮어쓰기 전에 기존 3장을 `docs/codex/hands_backup/{ID}/`에 복사해 둡니다.
다른 부위 그림(`*_body.png`, `*_foot_*.png`, `jump_*.png`, `front.png`)은 고치지 않습니다.

## 반드시 먼저 열어 볼 그림
- `refs/{ID}/side.png`, `refs/{ID}/front.png` — 맞춰야 할 원본. 1024×1024, 발 기준점 (512, 900).
- `public/assets/characters/{ID}/side_body.png`, `side_foot_near.png`, `side_foot_far.png`, `front_body.png`, `front_foot_l.png`, `front_foot_r.png` — 그대로 쓰는 부위.

## 규격
- 1024×1024 PNG, 배경은 완전 투명(알파 0). 그림자, 바닥선 없음.
- 손은 **원본 그림에서의 위치와 크기 그대로** 캔버스에 놓습니다(부위 그림들을 같은 좌표로 겹치면 원본이 됩니다).
- 게임이 겹치는 순서
  - 옆모습: `side_foot_far` → `side_body` → `side_foot_near` → `side_hand` (손이 맨 위)
  - 정면: `front_foot_l`, `front_foot_r` → `front_body` → `front_hand_l`, `front_hand_r` (손이 맨 위)
- 이 순서로 겹쳤을 때 `refs/{ID}/side.png`, `refs/{ID}/front.png`와 **손 부분이 똑같이 보여야** 합니다.
  - 옆모습 손: 외곽선은 원본처럼 열려 있습니다. 선이 없는 쪽은 채색이 몸통 색으로 부드럽게 이어져 경계가 보이지 않아야 합니다(가장자리 알파를 서서히 줄입니다).
  - 정면 손: 원본에서 몸통 외곽선이 손 위로 넘어온 짧은 호도 손 그림에 포함합니다(손이 맨 위에 그려지기 때문입니다).
- 선 굵기, 색, 수채화 질감, 하이라이트는 원본과 같습니다. 새로 디자인하지 않습니다. 원본에서 손 부분을 잘라 다듬는 방법을 먼저 시도하고, 가려진 부분만 같은 그림체로 채웁니다.
- 게임은 걸을 때 손을 앞뒤로 약 30px, 위로 약 100px까지 옮깁니다. 그래서 손 그림은 원본에서 보이는 부분보다 **몸 쪽으로 20px쯤 더** 채색되어 있어야 합니다(선 없이, 알파가 줄어드는 채색). 옮겼을 때 구멍이 보이면 안 됩니다.

## 끝내기 전에 직접 확인할 것
1. 캐릭터마다 확인 그림 `docs/codex/check_hands_{ID}.png`를 만듭니다. 회색 배경에 4칸: (가) `refs/{ID}/side.png` (나) 옆모습 부위를 위 순서로 겹친 것 (다) `refs/{ID}/front.png` (라) 정면 부위를 겹친 것.
2. 그 그림을 직접 열어 봅니다. (가)와 (나), (다)와 (라)에서 손의 위치·크기·외곽선 모양이 다르면 고칩니다. 닫힌 동그라미가 보이면 실패입니다.
3. 3장 모두 1024×1024, 네 모서리 알파 0인지 확인합니다.
4. 이 폴더(`pheeeew-climb`) 밖의 파일은 만들거나 고치지 않습니다. 예외: 이미지 생성 도구가 `$CODEX_HOME/generated_images/` 아래에 자동으로 저장하는 것은 괜찮습니다. `public/*.js`는 고치지 않습니다.
5. 중간에 질문하지 말고 끝까지 진행합니다. 판단이 필요하면 이 문서를 우선해 스스로 정하고, 마지막 답에 무엇을 정했는지 적습니다.
6. 마지막 답에 캐릭터별로 손 3장의 불투명 영역 좌표(left, top, right, bottom)를 표로 적습니다.
