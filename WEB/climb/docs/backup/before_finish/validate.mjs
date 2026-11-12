// 맵 검증기: 감정이 5명 각각의 물리로 "시작 -> 목표"가 되는지, 얼마나 빡빡한지 계산한다.
// 사용: node tools/validate.mjs [--all]   (--all 은 모든 간선을 출력)
// 창(window) = 같은 자리·같은 방향에서 그 발판에 착지하는 차지 길이의 연속 구간. 단위는 1/120초.
import { TILE, DT, IDS, params, newPlayer, step, hits as hitsAll, hitsSolid } from '../public/physics.js';
import { level, START as START0, GOAL as GOAL0, ZONES, winds, yOf, hOf } from '../public/level.js';
// 구간 하나만 검사: ZONE=1 또는 ZONE=2 (구간 길이가 바뀌어도 경계를 알아서 찾는다)
if (process.env.ZONE) { const z = ZONES[Number(process.env.ZONE) - 1]; process.env.FROM = String(z.from); process.env.TO = String(z.to); console.log(`구간 ${process.env.ZONE}: 높이 ${z.from}~${z.to}`); }
// 구간 하나만 검사: FROM=117 (시작 높이, 칸 3) TO=117 (끝 높이). 예: TO=117 node tools/validate.mjs
const START = process.env.FROM ? [3, Number(process.env.FROM)] : START0;
const GOAL = process.env.TO ? Number(process.env.TO) : GOAL0;

const XSTEP = 8;
const MIN_WINDOW = 6; // 0.05초. 이보다 좁은 점프만으로 이어지는 길은 "못 깬다"로 본다

const hits = (x, y, P) => hitsAll(level, x, y, P);
const solidAt = (x, y, P) => hitsSolid(level, x, y, P);

function spotsFor(P) {
  const spots = [];
  for (let r = 1; r < level.rows; r++) {
    if (process.env.ZONE && (hOf(r * TILE) < START[1] || hOf(r * TILE) > GOAL)) continue; // 구간 하나만: 그 높이 범위만 계산해 빨리 끝낸다
    const y = r * TILE - P.h;
    let cur = null;
    for (let x = TILE; x <= 31 * TILE - P.w; x += XSTEP) {
      const ok = !hits(x, y, P) && solidAt(x, y + 1, P);
      if (ok) {
        if (!cur) { cur = { id: spots.length, y, x0: x, x1: x, h: hOf(r * TILE) }; spots.push(cur); }
        cur.x1 = x;
      } else cur = null;
    }
  }
  return spots;
}

const find = (spots, x, y) => spots.find((s) => s.y === y && x >= s.x0 - XSTEP && x <= s.x1 + XSTEP);

function land(p, input, P) {
  for (let i = 0; i < 3000 && !p.ground; i++) step(p, input, level, P);
  return p.ground ? p : null;
}

function analyse(id) {
  const P = params(id);
  const spots = spotsFor(P);
  const edges = new Map(); // "a>b" -> 가장 넓은 창
  const put = (a, b, w) => { const k = `${a}>${b}`; if (a !== b && w > (edges.get(k) ?? 0)) edges.set(k, w); };
  const maxN = Math.round(P.chargeMax / DT);

  for (const s of spots) {
    for (let x = s.x0; x <= s.x1; x += XSTEP) {
      // 바람 구역 근처에서는 제자리 점프(방향키 없이)도 옆으로 실려 가므로 같이 계산한다
      const windy = winds.some((w) => s.h >= w.h0 - 17 && s.h <= w.h1);
      for (const dir of windy ? [-1, 0, 1] : [-1, 1]) {
        let run = 0;
        let last = -1;
        for (let n = 1; n <= maxN; n++) {
          const p = newPlayer(x, s.y);
          const hold = { left: dir < 0, right: dir > 0, jump: true };
          for (let i = 0; i < n && p.ground; i++) step(p, hold, level, P);
          if (p.ground) step(p, { left: dir < 0, right: dir > 0 }, level, P);
          const q = land(p, {}, P);
          const t = q ? find(spots, q.x, q.y) : null;
          const tid = t ? t.id : -1;
          if (tid === last) run++; else { run = 1; last = tid; }
          if (tid >= 0) put(s.id, tid, run);
        }
      }
    }
    // 끝에서 걸어 떨어지기
    for (const dir of [-1, 1]) {
      const p = newPlayer(dir < 0 ? s.x0 : s.x1, s.y);
      const walk = { left: dir < 0, right: dir > 0 };
      for (let i = 0; i < 600 && p.ground; i++) step(p, walk, level, P);
      if (p.ground) continue;
      const q = land(p, {}, P);
      const t = q && find(spots, q.x, q.y);
      if (t) put(s.id, t.id, 999);
    }
  }

  const start = find(spots, START[0] * TILE, yOf(START[1]) - P.h);
  const goals = new Set(spots.filter((s) => s.h === GOAL).map((s) => s.id));
  // 가장 넓은 길: 지나는 창의 최솟값이 가장 큰 경로
  const best = new Map([[start.id, Infinity]]);
  const prev = new Map();
  const todo = new Set([start.id]);
  const out = new Map();
  for (const [k, w] of edges) { const [a, b] = k.split('>').map(Number); (out.get(a) ?? out.set(a, []).get(a)).push([b, w]); }
  while (todo.size) {
    let a = null;
    for (const t of todo) if (a === null || best.get(t) > best.get(a)) a = t;
    todo.delete(a);
    for (const [b, w] of out.get(a) ?? []) {
      const v = Math.min(best.get(a), w);
      if (v > (best.get(b) ?? 0)) { best.set(b, v); prev.set(b, a); todo.add(b); }
    }
  }
  const goal = [...goals].sort((a, b) => (best.get(b) ?? 0) - (best.get(a) ?? 0))[0];
  const path = [];
  for (let n = goal; n !== undefined && best.has(n); n = prev.get(n)) path.unshift(n);
  // 목표로 돌아갈 수 없는 곳(갇히는 곳)
  const canReach = new Set(goals);
  for (let grown = true; grown;) {
    grown = false;
    for (const [k, w] of edges) {
      const [a, b] = k.split('>').map(Number);
      if (w >= MIN_WINDOW && canReach.has(b) && !canReach.has(a)) { canReach.add(a); grown = true; }
    }
  }
  const stuck = spots.filter((s) => best.has(s.id) && !canReach.has(s.id));
  return { spots, edges, best, path, goal, stuck };
}

const name = (s) => `높이${s.h}(${(s.x0 / TILE).toFixed(1)}~${((s.x1 + 60) / TILE).toFixed(1)})`;
if (process.argv.includes('--map')) { // 맵을 글자로 출력
  for (let r = 0; r < level.rows; r++) {
    let line = '';
    for (let c = 0; c < 32; c++) line += '.#/\\'[level.tile(c, r)];
    console.log(String(level.rows - 1 - r).padStart(3), line);
  }
}
let fail = false;
// 능력이 같은 감정이는 한 번만 계산한다(지금은 5명 모두 같다)
const seen = new Set();
for (const id of IDS) {
  const sig = JSON.stringify(params(id));
  if (seen.has(sig)) continue;
  seen.add(sig);
  const { spots, edges, best, path, goal, stuck } = analyse(id);
  const width = best.get(goal) ?? 0;
  const ok = width >= MIN_WINDOW && !stuck.length;
  if (!ok) fail = true;
  console.log(`\n== ${id}: ${ok ? '통과' : '실패'} — 가장 좁은 점프 창 ${width} (${(width * DT).toFixed(3)}초), 갇히는 곳 ${stuck.length}`);
  for (const s of stuck) console.log(`   갇힘: ${name(spots[s.id])}`);
  if (id === IDS[0] || process.argv.includes('--all')) {
    for (let i = 1; i < path.length; i++) {
      const w = edges.get(`${path[i - 1]}>${path[i]}`);
      console.log(`   ${name(spots[path[i - 1]])} -> ${name(spots[path[i]])}  창 ${w}`);
    }
  }
  if (process.argv.includes('--skips')) {
    // 3칸 넘게 오르는 모든 간선: 의도하지 않은 지름길 찾기
    for (const [k, w] of edges) {
      const [a, b] = k.split('>').map(Number);
      if (spots[b].h - spots[a].h >= 3 && best.has(a)) console.log(`   ↑ ${name(spots[a])} -> ${name(spots[b])} 창 ${w}`);
    }
  }
}
process.exit(fail ? 1 : 0);
