// public/ui.js 의 순수 규칙 시험: 명예의 전당 정렬, 1·2·3등 메달.
// 사용: node tools/ui_test.mjs
import { medal, hallBoard } from '../public/ui.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};

test('1·2·3등은 금·은·동, 나머지는 메달 없음', () => {
  eq([1, 2, 3, 4, 50].map(medal), ['gold', 'silver', 'bronze', '', ''], '등수별');
  eq([0, null, undefined, '1'].map(medal), ['', '', '', 'gold'], '이상한 값');
});

const H = [
  { order: 2, tag: 'b', time: 300 },
  { order: 1, tag: 'a', time: 500 },
  { order: 3, tag: 'c', time: 300 },
  { order: 4, tag: 'd', time: 120 },
];

test('먼저 온 순서: 서버 order 대로, 등수는 order', () => {
  eq(hallBoard(H, 'order').map((it) => [it.tag, it.place]), [['a', 1], ['b', 2], ['c', 3], ['d', 4]], '순서');
});

test('빠른 기록 순: 시간 오름차순, 같으면 먼저 온 쪽, 등수는 1부터', () => {
  eq(hallBoard(H, 'time').map((it) => [it.tag, it.place]), [['d', 1], ['b', 2], ['c', 3], ['a', 4]], '순서');
});

test('원래 목록은 바꾸지 않고, 못 불러온 목록(null)은 그대로', () => {
  hallBoard(H, 'time');
  eq(H.map((it) => it.tag), ['b', 'a', 'c', 'd'], '원래 목록');
  eq(hallBoard(null, 'time'), null, 'null');
});

let failed = 0;
for (const t of tests) {
  try { t.fn(); console.log(`ok   ${t.name}`); } catch (e) { failed++; console.log(`FAIL ${t.name}\n     ${e.message}`); }
}
console.log(`${tests.length - failed}/${tests.length} 통과`);
process.exit(failed ? 1 : 0);
