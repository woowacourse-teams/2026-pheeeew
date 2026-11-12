// 안내 표지판 규칙(그리지 않는다). 표지판 자료는 구간 파일의 signs, 합친 목록은 level.js 의 SIGNS.
export const NEAR_X = 3; // 칸. 캐릭터가 이만큼 안에 들어오면 말풍선을 띄운다
export const NEAR_Y = 2;
export const READ_AFTER = 1.5; // 초. 말풍선 앞에 이만큼 계속 있으면 읽은 것으로 친다

// 캐릭터(c = 몸 가운데 가로 칸, h = 발 높이 칸)에서 말풍선을 띄울 표지판의 번호. 없으면 -1
export function nearestSign(signs, c, h) {
  let best = -1;
  let bestD = Infinity;
  signs.forEach((s, i) => {
    const dx = Math.abs(c - s.c);
    const dy = Math.abs(h - s.h);
    if (dx > NEAR_X || dy > NEAR_Y) return;
    const d = dx * dx + dy * dy;
    if (d < bestD) { best = i; bestD = d; }
  });
  return best;
}

// 표지판을 저장할 때 쓰는 이름(문구를 고쳐도 읽음 표시가 남게 자리로 정한다)
export const signKey = (s) => `${s.c}:${s.h}`;

// 읽음 처리. readKeys = 저장해 둔 이름 목록
export function signReader(readKeys = []) {
  const read = new Set(readKeys);
  let key = null;
  let t = 0;
  return {
    isRead: (k) => read.has(k),
    // k = 이번 프레임에 말풍선을 띄운 표지판 이름(없으면 null). 막 읽음이 된 순간에만 true(그때 저장한다)
    update(k, dt) {
      if (k !== key) { key = k; t = 0; }
      if (k === null || read.has(k)) return false;
      t += dt;
      if (t < READ_AFTER) return false;
      read.add(k);
      return true;
    },
    keys: () => [...read],
  };
}

// "이름: 설명" 은 두 줄(이름 줄, 설명 줄)로 나눈다
export function splitSign(text) {
  const i = text.indexOf(': ');
  return i < 0 ? [text] : [text.slice(0, i), text.slice(i + 2)];
}

// 말풍선 가운데 x. 폭 w 가 화면(폭 W) 가장자리 밖으로 나가지 않게 안쪽으로 당긴다
export const bubbleX = (x, w, W, margin = 12) => Math.min(W - w / 2 - margin, Math.max(w / 2 + margin, x));

// 표지판 하나의 모습. read = 읽었는지, near = 지금 카드를 띄운 표지판인지(nearestSign 이 고른 것)
// 안 읽었으면 빛 무리를 깔고, 멀 때는 머리 위 느낌표 꼬리표, 가까우면 꼬리표 대신 카드
export const signLook = (read, near) => ({ tag: !read && !near, glow: !read, card: near });

// 표지판 종류(구간 파일 signs 의 kind) → 카드에 넣을 장치 그림(assets/<테마>/<이름>.webp). 게임이 그 장치에 쓰는 그림 그대로
export const SIGN_ART = { slope: 'fabric', nod: 'nod', wind: 'flag', hot: 'hot', boost: 'boost' };

// 표지판이 장식에 묻히지 않게 비워 둘 너비(칸, 좌우 각각)
export const CLEAR_X = 1.5;
// 가로 x 칸, 윗면 높이 h 에 놓일 윗면 장식이 표지판 옆인지(그러면 그리지 않는다)
export const besideSign = (signs, x, h) => signs.some((s) => s.h === h && Math.abs(x - s.c) < CLEAR_X);

// 표지판 크기. info_sign 그림(512×512)은 바닥 기준 줄 y=450 에서 꼭대기까지 테마마다 다르다(SIGN_TOPS, 그림 px), 판 반 폭은 약 110px
export const SIGN_S = 0.38; // 기본 배율. 방향 표지(화살표, 0.22~0.36)보다 크고 캐릭터 키의 약 1.5배
export const SIGN_SINK = 5; // 지형에 살짝 묻힌다(px)
export const TAG_H = 40; // 판 꼭대기 위로 느낌표 꼬리표가 차지하는 높이(간격·통통 튀는 폭 포함, px)
export const SIGN_TOPS = { nyc: 387, xmas: 440, balloon: 429, space: 407 }; // 지팡이 손잡이·풍선처럼 판 위로 솟은 것까지
export const signScale = (s) => s.s ?? SIGN_S; // 위가 좁은 자리는 구간 파일에서 s 로 줄인다
export const signTop = (s, art) => (SIGN_TOPS[art] ?? 388) * signScale(s) - SIGN_SINK; // 바닥에서 표지판 꼭대기까지(px). art = 그 구간 그림 폴더
export const signTall = (s, art) => signTop(s, art) + TAG_H; // 꼬리표까지
export const signHalf = (s) => 110 * signScale(s); // 판 반 폭(px)
