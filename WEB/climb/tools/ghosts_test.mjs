// public/ghosts.js 시험. 화면 없이 다른 사람 목록과 보간만 확인한다.
// 사용: node tools/ghosts_test.mjs
import { createGhosts, DELAY, STALE, FADE } from '../public/ghosts.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};
const near = (a, b, what) => { if (Math.abs(a - b) > 1e-9) throw new Error(`${what}: ${a} !== ${b}`); };

const PIYU = { n: 9, nickname: '히유', tag: '4f2a', character: 'angry' };
// sts 항목 [n, x, h, vx, vy, flags, charge]
const st = (n, x, h, extra = [0, 0, 1, 0]) => [n, x, h, ...extra];

test('받은 상태를 150ms 늦춰 두 상태 사이를 잇는다', () => {
  const g = createGhosts();
  g.enter([PIYU]);
  g.states([st(9, 100, 10, [100, -40, 1, 0])], 1000);
  g.states([st(9, 200, 12, [300, 40, 3, 50])], 1100);
  const [p] = g.sample(1100 + DELAY - 50); // 보이는 시각 = 1050, 두 상태의 가운데
  eq([p.n, p.nickname, p.tag, p.character], [9, '히유', '4f2a', 'angry'], '이름표');
  near(p.x, 150, 'x');
  near(p.h, 11, 'h');
  near(p.vx, 200, 'vx');
  near(p.charge, 25, '모은 정도');
  eq(p.flags, 1, '자세 표시는 앞 상태 것');
  eq(p.alpha, 1, '진하기');
});

test('첫 상태 전에는 첫 상태에, 마지막 상태 뒤에는 마지막 상태에 멈춰 있다(앞질러 가지 않는다)', () => {
  const g = createGhosts();
  g.enter([PIYU]);
  g.states([st(9, 100, 10)], 1000);
  near(g.sample(1000)[0].x, 100, '첫 상태 전');
  g.states([st(9, 200, 12)], 1100);
  near(g.sample(2000)[0].x, 200, '마지막 상태 뒤');
});

test('1초 넘게 새 소식이 없으면 서서히 지우고, 다 지워지면 목록에서 뺀다', () => {
  const g = createGhosts();
  g.enter([PIYU]);
  g.states([st(9, 100, 10)], 1000);
  const last = 1000 + DELAY;
  eq(g.sample(last + STALE)[0].alpha, 1, '1초까지는 그대로');
  near(g.sample(last + STALE + FADE / 2)[0].alpha, 0.5, '반쯤');
  eq(g.sample(last + STALE + FADE).length, 0, '다 지워짐');
  g.states([st(9, 120, 10)], 9000);
  eq(g.sample(9000).length, 0, '지워진 사람은 in 을 다시 받아야 보인다');
});

test('새 소식이 오면 다시 진해진다', () => {
  const g = createGhosts();
  g.enter([PIYU]);
  g.states([st(9, 100, 10)], 1000);
  g.sample(1000 + DELAY + STALE + FADE / 2);
  g.states([st(9, 110, 10)], 2700);
  eq(g.sample(2700 + DELAY)[0].alpha, 1, '진하기');
});

test('out 이면 바로, clear 면 모두 없앤다', () => {
  const g = createGhosts();
  g.enter([PIYU, { ...PIYU, n: 10, nickname: '둘째' }]);
  g.states([st(9, 1, 1), st(10, 2, 2)], 0);
  g.leave([9]);
  eq(g.sample(0).map((p) => p.n), [10], 'out');
  g.clear();
  eq(g.sample(0), [], 'clear');
});

test('in 없이 온 상태는 그리지 않고, 상태가 없는 사람도 그리지 않는다', () => {
  const g = createGhosts();
  g.states([st(5, 1, 1)], 0);
  g.enter([PIYU]);
  eq(g.sample(0), [], '아무도 안 보임');
});

test('동시에 최대 20명까지만 둔다', () => {
  const g = createGhosts();
  g.enter(Array.from({ length: 25 }, (_, i) => ({ ...PIYU, n: i + 1 })));
  g.states(Array.from({ length: 25 }, (_, i) => st(i + 1, i, i)), 0);
  eq(g.sample(0).length, 20, '20명');
});

test('그리지 않는 동안 받은 상태는 최근 것만 남긴다', () => {
  const g = createGhosts();
  g.enter([PIYU]);
  for (let i = 0; i < 1000; i++) g.states([st(9, i, 10)], i * 100);
  near(g.sample(50000 + DELAY)[0].x, 970, '오래된 상태는 버려 최근 3초(30개)의 처음에 멈춘다');
  near(g.sample(999 * 100 - 2000 + DELAY)[0].x, 979, '최근 2초는 남아 있음');
  near(g.sample(999 * 100 + DELAY)[0].x, 999, '마지막 상태');
});

let failed = 0;
for (const { name, fn } of tests) {
  try { await fn(); console.log(`통과  ${name}`); } catch (e) { failed++; console.log(`실패  ${name}\n      ${e.message}`); }
}
console.log(failed ? `\n${failed}개 실패 / ${tests.length}개` : `\n모두 통과 (${tests.length}개)`);
process.exitCode = failed ? 1 : 0;
