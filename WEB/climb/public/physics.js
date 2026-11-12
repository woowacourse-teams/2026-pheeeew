// 피유 등반 물리. 브라우저와 Node(검증기)가 같은 코드를 쓴다.
// 고정 간격(1/120초)으로만 계산해서, 같은 입력이면 항상 같은 결과가 나온다.
export const TILE = 40;
export const COLS = 32;
export const DT = 1 / 120;

const BASE = {
  w: 60, // 충돌 상자
  h: 48,
  g: 1900,
  fallG: 1, // 내려올 때 중력 배율
  maxFall: 1200,
  walk: 180,
  jumpVx: 400, // 점프의 가로 속도는 고정. 차지는 높이만 바꾼다
  vMin: 300,
  vMax: 1103, // 최대 점프 높이 320px = 8칸
  chargeMax: 0.8,
  bounce: 0.5, // 벽에 부딪힌 뒤 남는 가로 속도
  splat: 1.2, // 엎어져 있는 시간
  splatDist: 720, // 이만큼 넘게 떨어지면 엎어진다
  flail: 0.12, // 공중에서 한 번 방향키로 살짝 움직이는 세기(가로 속도의 비율). 모두 할 수 있다
  scramble: 230, // 경사로를 걸어 오를 때의 첫 속도. 약 0.6칸 올라간다
};

// 고유 능력은 지금 꺼 두었다(2026-11-12 사용자 결정: 모두 기본으로 통일). 다시 켜려면 ABILITY 에 이 표를 넣는다.
// defeated { splat: 0.15 } 엎어져도 바로 일어남 / tired { fallG: 0.7, maxFall: 800 } 천천히 떨어짐
// frustrated { flail: 0.3 } 세게 허우적 / annoyed { bounce: 0.85 } 벽에 세게 튕김 / angry { chargeMax: 0.6, vMax: 1103 * Math.sqrt(1.1) } 빨리 모으고 높이 뜀
const ABILITY = {};

export const IDS = ['defeated', 'tired', 'frustrated', 'annoyed', 'angry'];
export const params = (id) => ({ ...BASE, ...ABILITY[id] });

export function newPlayer(x, y) {
  return { x, y, vx: 0, vy: 0, ground: true, charge: 0, charging: false, lock: 0, splat: false, peakY: y, takeoffY: y, flailed: false, facing: 1, ev: null, slide: 0, vs: 0 };
}

// 상자가 꽉 찬 칸과 겹치는지
export function hitsSolid(level, x, y, P) {
  const c0 = Math.floor(x / TILE);
  const c1 = Math.floor((x + P.w - 0.001) / TILE);
  const r0 = Math.floor(y / TILE);
  const r1 = Math.floor((y + P.h - 0.001) / TILE);
  for (let r = r0; r <= r1; r++) for (let c = c0; c <= c1; c++) if (level.tile(c, r) === 1) return true;
  return false;
}

// 상자 아래에 있는 경사면 중 가장 높은 면. { ys: 그 면의 y, dir: 미끄러지는 방향 } 또는 null.
// 칸 2 = '/' (오른쪽이 높음, 왼쪽으로 미끄러짐), 칸 3 = '\' (왼쪽이 높음, 오른쪽으로 미끄러짐)
export function slopeUnder(level, x, y, P) {
  const c0 = Math.floor(x / TILE);
  const c1 = Math.floor((x + P.w - 0.001) / TILE);
  const r0 = Math.floor(y / TILE);
  const r1 = Math.floor((y + P.h - 0.001) / TILE);
  let best = null;
  for (let r = r0; r <= r1; r++) for (let c = c0; c <= c1; c++) {
    const t = level.tile(c, r);
    if (t < 2) continue;
    const ys = t === 2 ? (r + 1) * TILE - (Math.min(x + P.w, (c + 1) * TILE) - c * TILE) : r * TILE + (Math.max(x, c * TILE) - c * TILE);
    if (!best || ys < best.ys) best = { ys, dir: t === 2 ? -1 : 1 };
  }
  return best;
}

// 경사면을 파고든 깊이(px). 닿지 않았으면 0.
function slopeDepth(level, x, y, P) {
  const s = slopeUnder(level, x, y, P);
  return s && y + P.h > s.ys ? y + P.h - s.ys : 0;
}

export const hits = (level, x, y, P) => hitsSolid(level, x, y, P) || slopeDepth(level, x, y, P) > 0;

// 경사면에 닿은 채, 경사 아래쪽으로 6px 안에서 꽉 찬 칸이나 마주 보는 경사에 막히면 끼여서 선다.
// 미끄러지는 한 틱은 6px 보다 짧아서, 미끄러지다 막힌 자리는 늘 여기에 든다.
function wedged(level, x, y, P) {
  const s = slopeUnder(level, x, y + 1, P);
  if (!s || y + 1 + P.h <= s.ys) return false;
  for (let k = 1; k <= 6; k++) if (hits(level, x + s.dir * k, y + k, P)) return true;
  return false;
}
// 서 있을 수 있는 자리: 발밑이 꽉 찬 칸이거나 경사에 끼였다
export const supported = (level, x, y, P) => hitsSolid(level, x, y + 1, P) || wedged(level, x, y, P);

function touchdown(p, P) {
  p.ground = true;
  p.slide = 0;
  p.vx = 0;
  p.vy = 0;
  p.ev = 'land';
  if (p.flailed && P.flail >= 0.3) p.lock = 0.3;
  if (p.y - p.peakY > P.splatDist && P.splat > 0) { p.lock = P.splat; p.splat = true; p.ev = 'splat'; }
}

function beginSlide(p, s, P) {
  p.y = s.ys - P.h;
  p.slide = s.dir;
  p.vs = Math.max(120, (p.vx * s.dir + p.vy) * 0.707); // 들어온 속도 중 경사 방향 성분
  p.vx = 0;
  p.vy = 0;
  p.facing = s.dir;
  p.ev = 'slide';
}

// 경사면에서는 서지 못하고 미끄러진다. 끝에서 그 속도로 날아간다.
function slideStep(p, level, P) {
  p.vs = Math.min(900, p.vs + P.g * 0.55 * DT);
  const d = p.vs * 0.707 * DT;
  const nx = p.x + p.slide * d;
  const ny = p.y + d;
  const fly = (vx) => { p.vx = vx; p.vy = p.vs * 0.707; p.slide = 0; };
  if (d < 0 && hitsSolid(level, nx, ny, P)) { p.vs = 0; return; } // 올라가다 막히면 거기서 멈췄다가 미끄러진다
  if (hitsSolid(level, nx, ny, P)) {
    if (!hitsSolid(level, nx, p.y, P)) { // 경사가 평평한 바닥으로 이어진다
      p.x = nx;
      p.y = Math.floor((ny + P.h) / TILE) * TILE - P.h;
      touchdown(p, P);
    } else touchdown(p, P); // 벽·덩어리에 막혔다: 그 자리에 끼여 선다(되튀면 다시 미끄러져 와서 끝없이 반복된다)
    return;
  }
  p.x = nx;
  p.y = ny;
  const s = slopeUnder(level, p.x, p.y + 3, P);
  if (!s || p.y + 3 + P.h <= s.ys) fly(p.slide * p.vs * 0.707);
  else if (s.dir === p.slide) p.y = s.ys - P.h;
  else touchdown(p, P); // 마주 보는 경사 사이에 끼었다
}

function launch(p, P, dir, level) {
  const c = Math.min(1, p.charge / P.chargeMax);
  p.vy = -(P.vMin + (P.vMax - P.vMin) * c);
  if (level?.padUnder?.(p.x, p.y, P)?.kind === 'boost') p.vy *= 1.4; // 분사 판: 더 높이 솟는다
  p.vx = dir * P.jumpVx;
  if (dir) p.facing = dir;
  p.ground = false;
  p.charging = false;
  p.charge = 0;
  p.flailed = false;
  p.peakY = p.takeoffY = p.y;
  p.ev = 'jump';
}

// input: { left, right, jump, flail } — flail 은 이번 스텝에 새로 눌린 방향(-1/0/1)
export function step(p, input, level, P) {
  p.ev = null;
  const dir = (input.right ? 1 : 0) - (input.left ? 1 : 0);
  if (p.slide) { slideStep(p, level, P); if (p.y < p.peakY) p.peakY = p.y; return; }

  if (p.ground) {
    if (!supported(level, p.x, p.y, P)) { // 딛고 있던 발판이 사라졌다
      p.ground = false;
      p.charging = false;
      p.charge = 0;
      p.vx = 0;
      p.vy = 0;
      p.flailed = false;
      p.peakY = p.takeoffY = p.y;
      return;
    }
    if (p.lock > 0) {
      p.lock -= DT;
      if (p.lock <= 0) p.splat = false;
      return;
    }
    if (input.jump) {
      p.charging = true;
      p.charge += DT;
      if (dir) p.facing = dir;
      if (p.charge >= P.chargeMax) launch(p, P, dir, level); // 끝까지 모으면 저절로 뛴다
      return;
    }
    if (p.charging) { launch(p, P, dir, level); return; }
    if (dir) {
      p.facing = dir;
      const nx = p.x + dir * P.walk * DT;
      if (!hits(level, nx, p.y, P)) p.x = nx;
      else if (!hitsSolid(level, nx, p.y, P)) {
        // 경사로를 걸어 오르려 하면 아주 조금 올라갔다가 도로 미끄러진다
        const s = slopeUnder(level, nx, p.y, P);
        if (s && s.dir === -dir && p.y + P.h - s.ys < 6) {
          p.x = nx;
          p.y = s.ys - P.h;
          p.ground = false;
          p.slide = s.dir;
          p.vs = -P.scramble;
          p.peakY = p.takeoffY = p.y;
          p.ev = 'slide';
          return;
        }
      }
      if (!supported(level, p.x, p.y, P)) { // 발판 끝에서 걸어 떨어진다
        p.ground = false;
        p.vx = dir * P.walk;
        p.vy = 0;
        p.flailed = false;
        p.peakY = p.takeoffY = p.y;
      }
    }
    return;
  }

  // 공중
  if (P.flail && !p.flailed && input.flail) { // 한 번 허우적(답답이는 세게)
    p.flailed = true;
    const moving = Math.sign(p.vx);
    p.vx = moving === 0 ? input.flail * P.jumpVx * P.flail : p.vx * (input.flail === moving ? 1 + P.flail : 1 - P.flail);
    p.ev = 'flail';
  }
  p.vy = Math.min(P.maxFall, p.vy + P.g * (p.vy > 0 ? P.fallG : 1) * DT);
  if (level.wind) p.vx += level.wind(p.x + P.w / 2, p.y + P.h / 2) * DT; // 바람 구역: 공중에서 옆으로 밀린다

  const nx = p.x + p.vx * DT;
  if (hitsSolid(level, nx, p.y, P)) {
    p.x = p.vx > 0 ? Math.floor((nx + P.w) / TILE) * TILE - P.w : Math.ceil(nx / TILE) * TILE;
    p.vx = -p.vx * P.bounce;
    p.ev = 'bounce';
  } else {
    const depth = slopeDepth(level, nx, p.y, P);
    if (depth > 24) { p.vx = -p.vx * P.bounce; p.ev = 'bounce'; } // 경사 칸의 세로 면은 벽이다
    else if (depth > 0) { p.x = nx; beginSlide(p, slopeUnder(level, nx, p.y, P), P); return; }
    else p.x = nx;
  }

  const ny = p.y + p.vy * DT;
  // 경사를 먼저 본다. 경사 칸 밑을 채운 덩어리의 윗면에 먼저 걸리면 경사 중간에 서 버린다.
  if (p.vy > 0 && slopeDepth(level, p.x, ny, P) > 0) { p.y = ny; beginSlide(p, slopeUnder(level, p.x, ny, P), P); return; }
  if (hitsSolid(level, p.x, ny, P)) {
    if (p.vy > 0) {
      p.y = Math.floor((ny + P.h) / TILE) * TILE - P.h;
      touchdown(p, P);
    } else {
      p.y = Math.ceil(ny / TILE) * TILE; // 머리를 박으면 위로 가던 힘이 사라진다
      p.vy = 0;
      p.ev = 'bump';
    }
  } else if (slopeDepth(level, p.x, ny, P) > 0) {
    if (p.vy > 0) { p.y = ny; beginSlide(p, slopeUnder(level, p.x, ny, P), P); return; }
    p.vy = 0; // 경사 칸의 밑면
    p.ev = 'bump';
  } else p.y = ny;
  if (p.y < p.peakY) p.peakY = p.y;
}
