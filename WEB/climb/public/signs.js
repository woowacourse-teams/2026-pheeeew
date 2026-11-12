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
