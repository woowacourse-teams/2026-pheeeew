// 지형 윤곽을 손그림처럼 만든다. 충돌은 칸 그대로이고, 보이는 선만 모서리를 굴리고 살짝 흔든다(최대 약 3px).
import { TILE, COLS } from './physics.js';

// 칸 격자에서 지형의 경계선을 따라간다. 돌려주는 값: 닫힌 다각형들의 꼭짓점 배열(px).
// frame = 좌우 벽과 바닥이 이어진 큰 덩어리(화면 밖에서 닫힌다).
export function traceTerrain(level, rows) {
  const T = (c, r) => { const t = level.raw(c, r); return t === 4 ? 0 : t; }; // 꾸벅 발판은 따로 그린다
  const out = new Map(); // "x,y" -> 그 점에서 나가는 변의 끝점들. 지형이 항상 진행 방향 오른쪽에 있다.
  const add = (x1, y1, x2, y2) => { const k = `${x1},${y1}`; (out.get(k) ?? out.set(k, []).get(k)).push([x2, y2]); };
  for (let r = -2; r <= rows; r++) for (let c = -1; c <= COLS; c++) {
    const t = T(c, r);
    if (!t) continue;
    const up = T(c, r - 1);
    const rt = T(c + 1, r);
    const dn = T(c, r + 1);
    const lf = T(c - 1, r);
    if (t === 1) {
      if (up === 0) add(c, r, c + 1, r);
      if (rt === 0 || rt === 2) add(c + 1, r, c + 1, r + 1);
      if (dn !== 1) add(c + 1, r + 1, c, r + 1);
      if (lf === 0 || lf === 3) add(c, r + 1, c, r);
    } else if (t === 2) { // '/'
      add(c, r + 1, c + 1, r);
      if (rt !== 1 && rt !== 3) add(c + 1, r, c + 1, r + 1);
      if (dn !== 1) add(c + 1, r + 1, c, r + 1);
    } else { // '\'
      add(c, r, c + 1, r + 1);
      if (dn !== 1) add(c + 1, r + 1, c, r + 1);
      if (lf !== 1 && lf !== 2) add(c, r + 1, c, r);
    }
  }
  const incoming = new Set();
  for (const ends of out.values()) for (const [x, y] of ends) incoming.add(`${x},${y}`);
  const starts = [...out.keys()].sort((a, b) => incoming.has(a) - incoming.has(b)); // 열린 사슬의 시작점부터
  const loops = [];
  for (const k of starts) {
    while (out.get(k)?.length) {
      let [x, y] = k.split(',').map(Number);
      const pts = [[x, y]];
      let closed = false;
      for (;;) {
        const ends = out.get(`${x},${y}`);
        if (!ends?.length) break;
        [x, y] = ends.pop();
        if (x === pts[0][0] && y === pts[0][1]) { closed = true; break; }
        pts.push([x, y]);
      }
      if (!closed) pts.push([COLS + 3, pts.at(-1)[1]], [COLS + 3, rows + 3], [-3, rows + 3], [-3, pts[0][1]]); // 화면 밖으로 돌아 닫는다
      // 일직선 위의 중간 점을 지운다
      const slim = pts.filter((p, i) => {
        const a = pts[(i + pts.length - 1) % pts.length];
        const b = pts[(i + 1) % pts.length];
        return (p[0] - a[0]) * (b[1] - p[1]) !== (p[1] - a[1]) * (b[0] - p[0]);
      });
      loops.push({ frame: !closed, pts: slim.map(([px, py]) => [px * TILE, py * TILE]) });
    }
  }
  return loops;
}

const hash = (x, y) => { const s = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453; return (s - Math.floor(s)) * 2 - 1; };

// 다각형의 모서리를 굴리고 변을 살짝 흔들어, 촘촘한 점 목록(x,y,x,y,...)으로 돌려준다.
export function sketch(pts, radius = 13, wobble = 3.2) {
  const n = pts.length;
  const flat = [];
  const corner = pts.map((v, i) => {
    const a = pts[(i + n - 1) % n];
    const b = pts[(i + 1) % n];
    const d1 = Math.hypot(v[0] - a[0], v[1] - a[1]);
    const d2 = Math.hypot(b[0] - v[0], b[1] - v[1]);
    const r = Math.min(radius, d1 / 2, d2 / 2);
    return { v, p1: [v[0] + ((a[0] - v[0]) / d1) * r, v[1] + ((a[1] - v[1]) / d1) * r], p2: [v[0] + ((b[0] - v[0]) / d2) * r, v[1] + ((b[1] - v[1]) / d2) * r] };
  });
  for (let i = 0; i < n; i++) {
    const { v, p1, p2 } = corner[i];
    for (let k = 0; k <= 5; k++) { // 둥근 모서리
      const t = k / 5;
      flat.push((1 - t) * (1 - t) * p1[0] + 2 * t * (1 - t) * v[0] + t * t * p2[0], (1 - t) * (1 - t) * p1[1] + 2 * t * (1 - t) * v[1] + t * t * p2[1]);
    }
    const q = corner[(i + 1) % n].p1; // 다음 모서리까지의 변을 흔든다
    const len = Math.hypot(q[0] - p2[0], q[1] - p2[1]);
    const steps = Math.floor(len / 34);
    const nx = -(q[1] - p2[1]) / (len || 1);
    const ny = (q[0] - p2[0]) / (len || 1);
    for (let k = 1; k < steps; k++) {
      const t = k / steps;
      const x = p2[0] + (q[0] - p2[0]) * t;
      const y = p2[1] + (q[1] - p2[1]) * t;
      const w = hash(Math.round(x / 8), Math.round(y / 8)) * wobble * Math.sin(Math.PI * t);
      flat.push(x + nx * w, y + ny * w);
    }
  }
  return flat;
}

// 다각형 넓이의 부호. 지형 덩어리는 양수, 지형 안의 빈 구멍은 음수.
export const area = (pts) => pts.reduce((s, p, i) => { const q = pts[(i + 1) % pts.length]; return s + (p[0] * q[1] - q[0] * p[1]); }, 0) / 2;
