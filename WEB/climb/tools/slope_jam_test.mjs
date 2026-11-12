// 경사 끼임 시험: 작은 합성 지형에 캐릭터를 떨어뜨려, 3000틱(25초) 안에 서는지 본다.
// 선 뒤에는 60틱 더 서 있는지, 좌우로 걸어 본 뒤에도 다시 서는지까지 본다.
// 사용: node tools/slope_jam_test.mjs   (모두 서면 "통과", 하나라도 못 서면 "실패"와 예를 출력하고 1로 끝난다)
//  (가) 경사 끝에서 벽까지 틈 0~3칸 (틈 바닥은 경사 끝과 같은 높이, 1칸 아래, 3칸 아래)
//  (나) 경사 위에 뜬 발판: 발판 밑면과 경사면 사이 0~3칸
//  (다) 마주 보는 경사 사이 (V자 골짜기)
// 경사는 '/'(칸 2)와 '\'(칸 3) 두 방향 모두 본다(좌우 대칭).
import { TILE, params, newPlayer, step } from '../public/physics.js';

const P = params('defeated');
const ROWS = 30;

// 행 0이 위. put(c, r, t) 로 칸을 놓고, mirror 면 좌우를 뒤집는다('/' <-> '\').
function makeLevel(build, mirror) {
  const grid = Array.from({ length: ROWS }, () => new Uint8Array(32));
  const put = (c, r, t) => {
    if (mirror) { c = 31 - c; if (t >= 2) t = 5 - t; }
    if (c > 0 && c < 31 && r >= 0 && r < ROWS) grid[r][c] = t;
  };
  build(put);
  return { rows: ROWS, tile: (c, r) => (c <= 0 || c >= 31 || r >= ROWS - 1 ? 1 : r < 0 ? 0 : grid[r][c]) };
}

// '\' 경사 4칸: 칸 8..11, 행 20..23. 밑은 바닥까지 채운다. 경사 끝 칸(11)의 아랫면은 행 24.
const slope = (put) => { for (let k = 0; k < 4; k++) { put(8 + k, 20 + k, 3); for (let r = 21 + k; r < ROWS; r++) put(8 + k, r, 1); } };

const cases = [];
for (const g of [0, 1, 2, 3]) for (const fg of [0, 1, 3]) {
  if (g === 0 && fg) continue;
  cases.push([`(가) 벽까지 틈 ${g}칸, 틈 바닥 ${fg}칸 아래`, (put) => {
    slope(put);
    for (let r = 0; r < ROWS; r++) put(12 + g, r, 1);
    for (let c = 12; c < 12 + g; c++) for (let r = 24 + fg; r < ROWS; r++) put(c, r, 1);
  }]);
}
// (나) 경사 칸 10의 왼쪽 위 모서리(행 22 윗면)에서 위로 gap 칸 떨어진 곳에 밑면이 오는 2칸 발판(칸 10~11)
for (const gap of [0, 1, 2, 3]) {
  cases.push([`(나) 발판 밑면이 경사면에서 ${gap}칸`, (put) => {
    slope(put);
    for (let c = 12; c < 20; c++) for (let r = 24; r < ROWS; r++) put(c, r, 1); // 경사 끝은 넓은 바닥
    for (const c of [10, 11]) put(c, 21 - gap, 1);
  }]);
}
cases.push(['(다) 마주 보는 경사 (V자)', (put) => {
  for (let k = 0; k < 4; k++) {
    put(8 + k, 20 + k, 3); put(15 - k, 20 + k, 2);
    for (let r = 21 + k; r < ROWS; r++) { put(8 + k, r, 1); put(15 - k, r, 1); }
  }
}]);

let fail = 0;
for (const [name, build] of cases) for (const mirror of [false, true]) {
  const level = makeLevel(build, mirror);
  let never = 0, tot = 0, ex = null;
  for (let x0 = 5 * TILE; x0 <= 13 * TILE; x0 += 8) for (const vx of [-400, -150, 0, 150, 400]) {
    const x = mirror ? 31 * TILE - P.w - x0 : x0;
    const p = newPlayer(x, 8 * TILE);
    p.ground = false; p.vx = mirror ? -vx : vx;
    tot++;
    let i = 0;
    for (; i < 3000 && !p.ground; i++) step(p, {}, level, P);
    // 섰으면 60틱 더 두어도 계속 서 있어야 한다(서자마자 떨어지면 끼임과 같다)
    for (let k = 0; k < 60 && p.ground; k++) step(p, {}, level, P);
    // 선 자리에서 좌우로 2초씩 걸어 봐도 다시 서야 한다(끼인 자리에서 걷기·경사 오르기)
    if (p.ground) for (const dir of [-1, 1]) {
      const q = { ...p };
      for (let k = 0; k < 240; k++) step(q, { left: dir < 0, right: dir > 0 }, level, P);
      for (let k = 0; k < 3000 && !q.ground; k++) step(q, {}, level, P);
      if (!q.ground) { p.ground = false; break; }
    }
    if (!p.ground) { never++; ex ??= `시작 칸 ${(x / TILE).toFixed(1)} vx ${p.vx.toFixed(0)} → 칸 ${(p.x / TILE).toFixed(2)} 발밑 행 ${((p.y + P.h) / TILE).toFixed(2)} slide ${p.slide}`; }
  }
  if (never) fail++;
  console.log(`${never ? '실패' : '통과'}  ${name}${mirror ? ' [좌우 반전]' : ''}: 못 선 경우 ${never}/${tot}${ex ? '  예: ' + ex : ''}`);
}
console.log(fail ? `\n실패: ${fail}개 지형에서 서지 못하는 캐릭터가 있다` : '\n통과: 모든 지형에서 3000틱 안에 선다');
process.exit(fail ? 1 : 0);
