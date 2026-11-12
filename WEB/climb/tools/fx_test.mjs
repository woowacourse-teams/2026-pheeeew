// public/boost.js 시험. 화면 없이 분사 판 효과의 상태 계산(밝기, 솟는 조각의 주기와 개수)만 확인한다.
// 사용: node tools/fx_test.mjs
import { boostLook, RISE_LIFE } from '../public/boost.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const near = (a, b, what) => { if (Math.abs(a - b) > 1e-9) throw new Error(`${what}: ${a} !== ${b}`); };
const ok = (v, what) => { if (!v) throw new Error(what); };

test('아무도 없으면 2.4초마다 숨 쉬듯 0.25~0.45 사이로 밝아졌다 어두워진다', () => {
  near(boostLook(0, false, 0).glow, 0.35, '0초(가운데)');
  near(boostLook(0.6, false, 0).glow, 0.45, '0.6초(가장 밝음)');
  near(boostLook(1.8, false, 0).glow, 0.25, '1.8초(가장 어두움)');
  near(boostLook(0.6 + 2.4, false, 0).glow, 0.45, '한 주기 뒤');
});

test('아무도 없을 때 판 하나에 조각이 동시에 3~5개 떠 있다', () => {
  const { every, max } = boostLook(0, false, 0);
  const alive = RISE_LIFE / every; // 일정하게 만들면 동시에 떠 있는 개수
  ok(alive >= 3 && alive <= 5, `동시에 ${alive}개`);
  ok(max >= 3 && max <= 5, `최대 ${max}개`);
});

test('서 있지 않으면 모으는 정도는 무시한다', () => {
  const a = boostLook(1, false, 0);
  const b = boostLook(1, false, 1);
  near(a.glow, b.glow, '밝기');
  near(a.every, b.every, '주기');
  near(a.max, b.max, '개수');
});

test('캐릭터가 서면 숨 쉬는 가장 밝은 때보다도 밝다', () => {
  for (const t of [0, 0.6, 1.8]) ok(boostLook(t, true, 0).glow > 0.45, `${t}초`);
});

test('모을수록 조각이 빨리, 많이 솟고 더 밝다. 다 모으면 밝기 1', () => {
  const s = [0, 0.5, 1].map((c) => boostLook(0.6, true, c));
  ok(s[0].every > s[1].every && s[1].every > s[2].every, '주기가 짧아진다');
  ok(s[0].max < s[1].max && s[1].max < s[2].max, '개수가 늘어난다');
  ok(s[0].glow < s[1].glow && s[1].glow <= 1, '밝아진다');
  near(s[2].glow, 1, '다 모으면');
  ok(boostLook(0, true, 0).every < boostLook(0, false, 0).every, '서기만 해도 조금 빨라진다');
});

test('모은 정도가 0~1 밖이면 끝 값으로 자른다', () => {
  near(boostLook(0.6, true, 3).every, boostLook(0.6, true, 1).every, '1 넘음');
  near(boostLook(0.6, true, -2).every, boostLook(0.6, true, 0).every, '0 밑');
});

let failed = 0;
for (const t of tests) {
  try { t.fn(); console.log(`ok   ${t.name}`); } catch (e) { failed++; console.log(`FAIL ${t.name}\n     ${e.message}`); }
}
console.log(`${tests.length - failed}/${tests.length} 통과`);
process.exit(failed ? 1 : 0);
