// 한 발판에서 다른 발판으로 가는 점프를 찾는다: node tools/route.mjs <id> <출발높이> <도착높이> <방향 -1|1> [출발 칸 최소] [출발 칸 최대]
import { TILE, DT, params, newPlayer, step, hits, hitsSolid } from '../public/physics.js';
import { level, yOf, hOf } from '../public/level.js';
const [id, h0, h1, dir, cMin = 1, cMax = 30] = process.argv.slice(2).map((v, i) => (i ? Number(v) : v));
const P = params(id);
const y = yOf(h0) - P.h;
for (let x = cMin * TILE; x <= cMax * TILE; x += 20) {
  if (hits(level, x, y, P) || !hitsSolid(level, x, y + 1, P)) continue;
  const ok = [];
  for (let n = 1; n <= Math.round(P.chargeMax / DT); n++) {
    const p = newPlayer(x, y);
    const hold = { left: dir < 0, right: dir > 0, jump: true };
    for (let i = 0; i < n && p.ground; i++) step(p, hold, level, P);
    if (p.ground) step(p, { left: dir < 0, right: dir > 0 }, level, P);
    for (let i = 0; i < 3000 && !p.ground; i++) step(p, {}, level, P);
    if (Math.round(hOf(p.y + P.h)) === h1) ok.push(n);
  }
  if (ok.length) console.log(`칸 ${(x / TILE).toFixed(1)}: 차지 ${Math.round((ok[0] * DT / P.chargeMax) * 100)}~${Math.round((ok.at(-1) * DT / P.chargeMax) * 100)}% (${ok.length}틱)`);
}
