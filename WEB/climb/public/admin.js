// 관리자 모드(?admin)의 계산. 화면과 떨어진 순수 함수라 Node 에서 시험한다(tools/admin_test.mjs).
// 자리(box)는 늘 충돌 상자의 왼쪽 위 { x, y } (px) 이다.
import { TILE, COLS, hits } from './physics.js';
import { ZONES, START, SCREEN, HEIGHT, yOf, hOf } from './level.js';

const W = COLS * TILE;
const H = 720;

// 화면(브라우저) 좌표 -> 맵 좌표(px). view = 논리 화면이 놓인 자리와 배율. 게임 화면 밖이면 null.
export function toMap(sx, sy, view, camY) {
  const x = (sx - view.x) / view.s;
  const y = (sy - view.y) / view.s;
  if (x < 0 || x > W || y < 0 || y > H) return null;
  return { x, y: y + camY };
}

const clampX = (x, P) => Math.max(TILE, Math.min(W - TILE - P.w, x));

// 발이 (x, y) 에 오게 놓는다. 지형 속이면 그 칸부터 위로 칸 윗면을 하나씩 올려 보며 처음 빈 곳.
export function placeAt(level, P, x, y) {
  const bx = clampX(x - P.w / 2, P);
  if (!hits(level, bx, y - P.h, P)) return { x: bx, y: y - P.h };
  for (let top = Math.floor(y / TILE) * TILE; top > 0; top -= TILE) if (!hits(level, bx, top - P.h, P)) return { x: bx, y: top - P.h };
  return { x: bx, y: -P.h };
}

// 칸 윗면 높이 h 근처에서 (x, h) 와 가장 가까운, 서 있을 수 있는 자리. 위아래 range 칸 안에서 찾는다.
export function ledgeNear(level, P, x, h, range = SCREEN / 2) {
  let best = null;
  let bestD = Infinity;
  const h0 = Math.max(0, Math.round(h - range));
  const h1 = Math.min(HEIGHT - 2, Math.round(h + range));
  for (let hh = h0; hh <= h1; hh++) {
    const y = yOf(hh) - P.h;
    for (let c = 1; c < COLS - 1; c++) {
      const bx = clampX(c * TILE + TILE / 2 - P.w / 2, P);
      if (hits(level, bx, y, P) || !hits(level, bx, y + 1, P)) continue;
      const d = Math.hypot((bx + P.w / 2 - x) / TILE, hh - h);
      if (d < bestD) { bestD = d; best = { x: bx, y }; }
    }
  }
  return best;
}

// 한 화면 위(dir 1) 또는 아래(dir -1)의 발판
export function screenJump(level, P, p, dir) {
  const h = Math.max(0, Math.min(HEIGHT - 2, hOf(p.y + P.h) + dir * SCREEN));
  return ledgeNear(level, P, p.x + P.w / 2, h);
}

// 구간 n(1부터)의 시작 쉼터. 구간 1은 시작 지점. 검증기(validate.mjs ZONE=n)와 같은 [3, from].
export function zoneStart(P, n) {
  const z = ZONES[n - 1];
  if (!z) return null;
  return { x: START[0] * TILE, y: yOf(n === 1 ? START[1] : z.from) - P.h };
}

// 되돌리기 자리: 땅 위에 가만히 선 자리(still)를 계속 기억하고, 땅을 떠나는 순간의 still 을 mark 로 둔다.
// back = 되돌아갈 곳. 뛰었다가 떨어져도 뛴 자리로 돌아가 같은 점프를 다시 해 본다.
export const newTrack = () => ({ still: null, mark: null, ground: false, get back() { return this.mark ?? this.still; } });
export function track(t, p) {
  if (p.ground && !p.splat && !p.slide && p.vx === 0 && p.vy === 0) t.still = { x: p.x, y: p.y };
  if (t.ground && !p.ground && t.still) t.mark = t.still;
  t.ground = p.ground;
  return t;
}

// 전체 지도: 탑을 rows 칸씩 자른 줄을 왼쪽(아래)부터 오른쪽(위)으로 늘어놓는다. 한 칸 = cell px.
export const MINI = { cell: 4, rows: SCREEN * 6, gap: 12 };
MINI.strips = Math.ceil(HEIGHT / MINI.rows);
MINI.w = MINI.strips * (COLS * MINI.cell + MINI.gap) - MINI.gap;
MINI.h = MINI.rows * MINI.cell;
// 지도 위 점(px) -> 맵 좌표(px). 줄 사이 틈이면 null.
export function fromMini(mx, my) {
  const stride = COLS * MINI.cell + MINI.gap;
  const k = Math.floor(mx / stride);
  const lx = mx - k * stride;
  if (k < 0 || k >= MINI.strips || lx > COLS * MINI.cell || my < 0 || my > MINI.h) return null;
  return { x: (lx / MINI.cell) * TILE, y: yOf(k * MINI.rows + (MINI.h - my) / MINI.cell) };
}

// 전체 지도 그리기(canvas 2D). 지형 칸은 구간 색, 화면 경계마다 번호, 내 위치(몸 가운데 x, 발 y px)는 빨간 점.
const ZONE_COLORS = ['#7aa7d6', '#e48a8a', '#b9a3e3', '#efb95a', '#6fc1aa', '#c9c2b2'];
export function drawMini(ctx, level, zones, x, feetY) {
  const { cell, rows, gap } = MINI;
  const stride = COLS * cell + gap;
  ctx.clearRect(0, 0, MINI.w, MINI.h);
  ctx.font = '11px Jua, sans-serif';
  for (let k = 0; k < MINI.strips; k++) {
    const ox = k * stride;
    ctx.fillStyle = '#efe4cf';
    ctx.fillRect(ox, 0, COLS * cell, MINI.h);
    for (let i = 0; i < rows; i++) {
      const hh = k * rows + i; // 높이 hh~hh+1 의 칸 줄
      const r = HEIGHT - 2 - hh;
      if (r < 0) break;
      const z = Math.max(0, zones.findIndex((zz, j) => hh < zz.to || j === zones.length - 1));
      const y = MINI.h - (i + 1) * cell;
      for (let c = 0; c < COLS; c++) {
        const t = level.raw(c, r);
        if (!t) continue;
        ctx.fillStyle = t === 4 ? '#fff8ea' : ZONE_COLORS[z % ZONE_COLORS.length];
        ctx.fillRect(ox + c * cell, y, cell, cell);
      }
      if (hh % SCREEN === 0) { // 화면 경계와 번호(화면 1 = 높이 0~18)
        ctx.fillStyle = '#2b2433';
        ctx.fillRect(ox, MINI.h - i * cell - 1, COLS * cell, 1);
        ctx.fillText(String(hh / SCREEN + 1), ox + 2, MINI.h - i * cell - 3);
      }
    }
  }
  const h = hOf(feetY);
  const k = Math.max(0, Math.min(MINI.strips - 1, Math.floor(h / rows)));
  ctx.fillStyle = '#ff3b30';
  ctx.strokeStyle = '#2b2433';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(k * stride + (x / TILE) * cell, MINI.h - (h - k * rows) * cell, 6, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();
}
