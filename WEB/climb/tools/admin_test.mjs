// public/admin.js 시험. 화면 없이 관리자 모드의 좌표 계산과 자리 찾기만 확인한다(맵은 실제 level.js).
// 사용: node tools/admin_test.mjs
import { toMap, placeAt, ledgeNear, screenJump, zoneStart, track, newTrack, MINI, fromMini } from '../public/admin.js';
import { TILE, params, hits, newPlayer } from '../public/physics.js';
import { level, ZONES, START, SCREEN, HEIGHT, yOf, hOf } from '../public/level.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};
const ok = (v, what) => { if (!v) throw new Error(what); };
const P = params('defeated'); // 60×48
// 서 있을 수 있는 자리: 겹치지 않고, 1px 아래는 막혀 있다
const standable = (b) => !hits(level, b.x, b.y, P) && hits(level, b.x, b.y + 1, P);

test('화면 좌표를 배율과 여백, 카메라를 반영해 맵 좌표로 바꾼다', () => {
  const view = { x: 40, y: 10, s: 0.5 }; // 1280×720 논리 화면이 640×360 으로 줄어 (40,10) 에 놓였다
  eq(toMap(40, 10, view, 1000), { x: 0, y: 1000 }, '왼쪽 위 모서리');
  eq(toMap(40 + 320, 10 + 180, view, 1000), { x: 640, y: 1360 }, '가운데');
  eq(toMap(39, 100, view, 1000), null, '게임 화면 왼쪽 밖');
  eq(toMap(100, 10 + 361, view, 1000), null, '게임 화면 아래 밖');
});

test('공중을 누르면 발이 그 점에 오게(가로는 몸 가운데) 그대로 놓는다', () => {
  const y = yOf(140); // 구간 2 시작 쉼터(높이 135) 위 5칸: 비어 있다
  eq(placeAt(level, P, 200, y), { x: 200 - P.w / 2, y: y - P.h }, '자리');
});

test('지형 속을 누르면 그 칸 위로 가장 가까운 빈 곳(윗면)에 놓는다', () => {
  const top = yOf(ZONES[1].from); // 구간 2 시작 쉼터 바닥 윗면
  const b = placeAt(level, P, 5 * TILE, top + 20); // 바닥 칸 한가운데
  eq(b, { x: 5 * TILE - P.w / 2, y: top - P.h }, '윗면');
  ok(standable(b), '서 있을 수 있어야 한다');
});

test('벽 가까이를 눌러도 벽 안으로 들어가지 않는다', () => {
  const y = yOf(140);
  eq(placeAt(level, P, 5, y).x, TILE, '왼쪽 벽');
  eq(placeAt(level, P, 1279, y).x, 31 * TILE - P.w, '오른쪽 벽');
});

test('가장 가까운 발판: 정확히 그 높이에 쉼터가 있으면 거기', () => {
  const b = ledgeNear(level, P, 3 * TILE, ZONES[2].from);
  ok(standable(b), '서 있을 수 있어야 한다');
  eq(Math.round(hOf(b.y + P.h)), ZONES[2].from, '높이');
});

test('한 화면 위·아래: 18칸 위(아래) 근처의 설 수 있는 발판으로', () => {
  const p = newPlayer(START[0] * TILE, yOf(ZONES[2].from) - P.h);
  const up = screenJump(level, P, p, 1);
  const down = screenJump(level, P, p, -1);
  ok(standable(up) && standable(down), '서 있을 수 있어야 한다');
  const h = (b) => hOf(b.y + P.h);
  ok(Math.abs(h(up) - (ZONES[2].from + SCREEN)) <= SCREEN / 2, `위: ${h(up)}`);
  ok(Math.abs(h(down) - (ZONES[2].from - SCREEN)) <= SCREEN / 2, `아래: ${h(down)}`);
  ok(h(up) > ZONES[2].from && h(down) < ZONES[2].from, '방향');
});

test('맨 아래에서 한 화면 아래로 가면 맨 아래 바닥, 꼭대기 위로 가면 맵 안', () => {
  const p = newPlayer(START[0] * TILE, yOf(0) - P.h);
  const b = screenJump(level, P, p, -1);
  ok(standable(b), '서 있을 수 있어야 한다');
  eq(Math.round(hOf(b.y + P.h)), 0, '맨 아래');
  const top = screenJump(level, P, newPlayer(START[0] * TILE, yOf(HEIGHT - 3) - P.h), 1);
  ok(top && standable(top) && top.y >= 0, '꼭대기');
});

test('구간 번호 → 시작 쉼터. 구간 1은 시작 지점', () => {
  eq(zoneStart(P, 1), { x: START[0] * TILE, y: yOf(START[1]) - P.h }, '구간 1');
  for (let n = 2; n <= ZONES.length; n++) {
    const b = zoneStart(P, n);
    eq(b, { x: START[0] * TILE, y: yOf(ZONES[n - 1].from) - P.h }, `구간 ${n}`);
    ok(standable(b), `구간 ${n} 서 있을 수 있어야 한다`);
  }
  eq(zoneStart(P, 9), null, '없는 구간');
});

test('전체 지도: 줄(한 줄 = 6화면)마다 아래에서 위로, 왼쪽 줄부터 오른쪽 줄로 쌓는다', () => {
  const stripW = 32 * MINI.cell;
  const stripH = MINI.rows * MINI.cell;
  eq(fromMini(0, stripH), { x: 0, y: yOf(0) }, '첫 줄 왼쪽 아래 = 맨 아래 왼쪽');
  eq(fromMini(stripW + MINI.gap + 16 * MINI.cell, 0), { x: 16 * TILE, y: yOf(2 * MINI.rows) }, '둘째 줄 가운데 맨 위');
  eq(fromMini(stripW + 1, 10), null, '줄 사이 틈');
});

// 되돌리기: 가만히 선 자리를 기억하고, 땅을 떠나는 순간의 그 자리를 되돌릴 곳으로 삼는다
const still = (x, y) => ({ ...newPlayer(x, y), ground: true });
const air = (x, y) => ({ ...newPlayer(x, y), ground: false, vy: -500 });
test('되돌리기: 점프 직전에 서 있던 자리로(떨어져 착지한 자리가 아니라)', () => {
  const t = newTrack();
  track(t, still(100, 500));
  track(t, { ...still(100, 500), charging: true, charge: 0.3 }); // 모으는 중에도 가만히 서 있다
  track(t, air(110, 490)); // 뛰었다
  track(t, { ...still(300, 900) }); // 아래에 떨어져 가만히 섰다
  eq(t.back, { x: 100, y: 500 }, '뛴 자리');
});

test('되돌리기: 걷는 중(움직이는 중)인 자리는 기억하지 않는다', () => {
  const t = newTrack();
  track(t, still(100, 500));
  track(t, { ...still(140, 500), vx: 180 }); // 걷는 중
  track(t, air(150, 505)); // 걸어서 떨어졌다
  eq(t.back, { x: 100, y: 500 }, '마지막으로 가만히 서 있던 자리');
});

test('되돌리기: 아직 땅을 떠난 적 없으면 지금 가만히 선 자리, 아무것도 없으면 null', () => {
  const t = newTrack();
  eq(t.back, null, '처음');
  track(t, air(0, 0)); // 공중에서 시작(관리자 이동 직후)
  eq(t.back, null, '공중에서 시작');
  track(t, still(200, 600));
  eq(t.back, { x: 200, y: 600 }, '선 자리');
});

test('되돌리기: 엎어져 있거나 미끄러지는 자리는 기억하지 않는다', () => {
  const t = newTrack();
  track(t, still(100, 500));
  track(t, { ...still(300, 900), splat: true });
  track(t, { ...still(320, 900), slide: 1 });
  eq(t.back, { x: 100, y: 500 }, '엎어지기 전 자리');
});

let fail = 0;
for (const { name, fn } of tests) {
  try { fn(); console.log(`ok   ${name}`); } catch (e) { fail++; console.log(`FAIL ${name}\n     ${e.message}`); }
}
console.log(fail ? `${fail}개 실패` : `${tests.length}개 모두 통과`);
process.exit(fail ? 1 : 0);
