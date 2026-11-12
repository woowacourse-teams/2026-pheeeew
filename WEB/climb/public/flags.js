// 바람 깃발 자리(그리지 않는다). 깃발은 바람 구역 안(가장자리 포함)의 넓은 발판 윗면에만 꽂는다.
export const FLAG_MAX = 3; // 구역마다 최대 개수
export const FLAG_CLEAR = 1.5; // 안내 표지판·방향 표지와 띄울 가로 거리(칸)
const FLAG_GAP = 3; // 깃발끼리 띄울 가로 거리(칸)

// w = 바람 구역 { c0, c1, h0, h1, ax }
// cell(c, h) = 칸 c, 높이 h 칸의 종류(0 빈 곳, 1 꽉 찬 지형, 2·3 경사, 4 꾸벅 발판)
// avoid = 비켜 설 것 [{ c, h }](c = 가로 중심 칸, h = 놓인 윗면 높이)
// 돌려주는 값 [{ c, h }]: c = 깃대 가로 위치(칸), h = 깃대가 꽂힌 윗면 높이
export function flagSpots(w, cell, avoid = []) {
  const top = (c, h) => cell(c, h - 1) === 1 && cell(c, h) === 0; // 꽉 찬 칸이고 윗면이 비었다(경사·꾸벅 발판 제외)
  const dir = w.ax < 0 ? -1 : 1; // 깃발이 나부끼는 쪽
  const spots = [];
  for (let h = w.h0; h <= w.h1; h++) {
    for (let c = w.c0; c <= w.c1; c++) {
      // 양옆 칸도 윗면이어야 한다: 폭 2칸 이하 발판(캐릭터가 내려앉는 좁은 자리)과 발판 끝 칸을 비운다
      if (!top(c - 1, h) || !top(c, h) || !top(c + 1, h)) continue;
      if (cell(c, h + 1) !== 0 || cell(c + dir, h + 1) !== 0) continue; // 깃대와 천이 들어갈 자리
      if (avoid.some((s) => s.h === h && Math.abs(c + 0.5 - s.c) < FLAG_CLEAR)) continue;
      let a = c;
      let b = c;
      while (top(a - 1, h)) a--;
      while (top(b + 1, h)) b++;
      spots.push({ c: c + 0.5, h, pad: `${a}:${h}`, wide: b - a + 1 });
    }
  }
  // 구역을 가로로 n 등분한 가운데마다 가장 가까운 자리(같으면 넓은 발판). 한 발판에 하나만
  const span = w.c1 - w.c0 + 1;
  const n = Math.min(FLAG_MAX, Math.max(1, Math.floor(span / 5)));
  const out = [];
  for (let i = 0; i < n; i++) {
    const x = w.c0 + (span * (i + 0.5)) / n;
    let best = null;
    for (const s of spots) {
      if (out.some((o) => o.pad === s.pad || Math.abs(o.c - s.c) < FLAG_GAP)) continue;
      const d = Math.abs(s.c - x);
      if (!best || d < best.d || (d === best.d && s.wide > best.wide)) best = { ...s, d };
    }
    if (best) out.push(best);
  }
  return out.map(({ c, h }) => ({ c, h }));
}
