// public/flags.js 시험과 실제 맵(level.js)의 바람 깃발 자리 검사.
// 사용: node tools/flags_test.mjs
import { flagSpots } from '../public/flags.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};
const ok = (v, what) => { if (!v) throw new Error(what); };

// 합성 지형. rows 는 위에서 아래로, 맨 아래 줄이 높이 0 칸. '#' 꽉 찬 지형, '/' '\' 경사, 'n' 꾸벅 발판, '.' 빈 곳
// 줄 밖 좌우와 아래는 막혀 있고 위는 뚫려 있다(게임 맵과 같다)
const map = (rows) => {
  const K = { '.': 0, '#': 1, '/': 2, '\\': 3, n: 4 };
  return (c, h) => {
    if (h < 0 || c < 0 || c >= rows[0].length) return 1;
    if (h >= rows.length) return 0;
    return K[rows[rows.length - 1 - h][c]];
  };
};
const W = (c0, c1, h0, h1, ax = 100) => ({ c0, c1, h0, h1, ax });

test('구역 안에 발판이 없으면 깃발도 없다', () => {
  const cell = map([
    '..........',
    '..........',
    '..........',
    '..........',
  ]);
  eq(flagSpots(W(0, 9, 1, 4), cell), [], '허공');
});

test('넓은 발판이 있으면 그 윗면 가운데 쪽에 꽂는다', () => {
  const cell = map([
    '..........',
    '..........',
    '..#####...', // 칸 2~6, 윗면 높이 2
    '..........',
  ]);
  eq(flagSpots(W(3, 5, 1, 4), cell), [{ c: 4.5, h: 2 }], '좁은 구역(3칸)이면 1개, 가운데 칸');
});

test('경사·꾸벅 발판·폭 2칸 이하 발판에는 꽂지 않는다', () => {
  const cell = map([
    '..................',
    '..................',
    '../##\\..nnnn..##..', // 경사 끝 사이 2칸, 꾸벅 발판 4칸, 2칸 발판
    '..###############.',
  ]);
  eq(flagSpots(W(0, 17, 2, 4), cell), [], '윗면 높이 2 줄에 꽂을 자리가 없다');
});

test('넓은 구역이면 좌우로 고르게 최대 3개, 한 발판에 하나만', () => {
  const cell = map([
    '..............................',
    '..............................',
    '.#####....#####.....######....', // 칸 1~5, 10~14, 20~25
    '..............................',
    '..............................',
    '.###########################..', // 칸 1~27 긴 갑판(윗면 2)
  ]);
  const got = flagSpots(W(0, 29, 0, 6), cell);
  eq(got.length, 3, '3개');
  ok(got[0].c < 8 && got[1].c > 9 && got[1].c < 16 && got[2].c > 19, `좌·가운데·우로 퍼진다: ${JSON.stringify(got)}`);
  const pads = got.map((f) => (f.h === 2 ? 'deck' : `${f.h}:${Math.floor(f.c / 5)}`));
  eq(new Set(pads).size, 3, '서로 다른 발판');
});

test('좁은 구역은 1개', () => {
  const cell = map([
    '..........',
    '..........',
    '.########.',
  ]);
  eq(flagSpots(W(2, 5, 1, 3), cell).length, 1, '폭 4칸');
});

test('안내 표지판·방향 표지에서 1.5칸 안에는 꽂지 않는다', () => {
  const cell = map([
    '.......',
    '.......',
    '.#####.', // 칸 1~5, 꽂을 수 있는 칸 2~4
  ]);
  eq(flagSpots(W(1, 5, 1, 3), cell, [{ c: 3.5, h: 1 }]), [], '표지판이 가운데(3.5): 2.5·3.5·4.5 모두 1.5칸 안');
  eq(flagSpots(W(1, 5, 1, 3), cell, [{ c: 4.9, h: 1 }]), [{ c: 2.5, h: 1 }], '표지판이 오른쪽(4.9): 3.5·4.5 는 피하고 2.5');
  eq(flagSpots(W(1, 5, 1, 3), cell, [{ c: 3.5, h: 2 }]).length, 1, '다른 높이의 표지판은 상관없다');
});

test('깃대 위나 천이 나부끼는 쪽이 막혀 있으면 꽂지 않는다', () => {
  const cell = map([
    '..#..',
    '.....',
    '.###.',
  ]);
  eq(flagSpots(W(1, 3, 1, 3, 100), cell), [], '깃대 바로 위가 막혔다');
  const side = map([
    '...#.',
    '.....',
    '.###.',
  ]);
  eq(flagSpots(W(1, 3, 1, 3, 100), side), [], '오른쪽으로 나부끼는데 오른쪽 위가 막혔다');
  eq(flagSpots(W(1, 3, 1, 3, -100), side), [{ c: 2.5, h: 1 }], '왼쪽으로 나부끼면 괜찮다');
});

// ── 실제 맵 ──
const { level, winds, HEIGHT, SIGNS, DECOR, ZONES } = await import('../public/level.js');
const cellOf = (c, h) => level.raw(c, HEIGHT - 2 - h);
const avoid = [...SIGNS, ...DECOR.filter((d) => !d.hang)];
const zoneOf = (h) => ZONES.findIndex((z) => h < z.to);

test('실제 맵: 모든 바람 깃발이 꽉 찬 지형 위에 서 있다(공중에 뜬 깃발 0개)', () => {
  const air = [];
  for (const w of winds) {
    for (const f of flagSpots(w, cellOf, avoid)) {
      const c = Math.floor(f.c);
      // 깃발 자리 칸(윗면 바로 위)은 비고, 그 아래 칸은 꽉 찬 지형. level.tile 의 줄 = yOf(h) / TILE
      if (level.tile(c, HEIGHT - 1 - f.h) !== 1 || level.tile(c, HEIGHT - 2 - f.h) !== 0) air.push({ ...f, zone: zoneOf(w.h0 + 1) + 1 });
      if (f.c < w.c0 || f.c > w.c1 + 1 || f.h < w.h0 || f.h > w.h1) air.push({ ...f, out: true });
    }
  }
  eq(air, [], '공중에 뜨거나 구역 밖');
});

test('실제 맵: 구간 4의 바람 구역 중 깃발이 하나라도 있는 구역이 있다', () => {
  const z4 = winds.filter((w) => zoneOf(w.h0 + 1) === 3);
  ok(z4.some((w) => flagSpots(w, cellOf, avoid).length > 0), '구간 4에 깃발이 하나도 없다');
});

for (const { name, fn } of tests) {
  try { fn(); console.log(`ok  ${name}`); } catch (e) { console.log(`FAIL ${name}\n     ${e.message}`); process.exitCode = 1; }
}
