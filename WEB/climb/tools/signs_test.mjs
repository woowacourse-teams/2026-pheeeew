// public/signs.js 시험과 실제 맵의 안내 표지판(level.js 의 SIGNS) 자리 검사.
// 사용: node tools/signs_test.mjs
import { nearestSign, signReader, signKey, splitSign, bubbleX, READ_AFTER, signLook, SIGN_ART, besideSign, SIGN_S, signScale, signTall, signHalf } from '../public/signs.js';
import { existsSync } from 'node:fs';
import { level, SIGNS, ZONES, HEIGHT, DECOR } from '../public/level.js';
import { TILE } from '../public/physics.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};
const ok = (v, what) => { if (!v) throw new Error(what); };

const A = { c: 10, h: 20, text: 'A' };
const B = { c: 15, h: 20, text: 'B' };

test('가로 3칸, 세로 2칸 안에 들어오면 그 표지판을 띄운다', () => {
  eq(nearestSign([A], 13, 20), 0, '가로 3칸');
  eq(nearestSign([A], 7, 22), 0, '왼쪽 3칸, 2칸 위');
  eq(nearestSign([A], 10, 18), 0, '2칸 아래');
});

test('멀면 아무것도 띄우지 않는다', () => {
  eq(nearestSign([A], 13.5, 20), -1, '가로 3.5칸');
  eq(nearestSign([A], 10, 22.5), -1, '2.5칸 위');
  eq(nearestSign([], 10, 20), -1, '표지판 없음');
});

test('둘 다 가까우면 더 가까운 하나만', () => {
  eq(nearestSign([A, B], 12, 20), 0, 'A 쪽');
  eq(nearestSign([A, B], 13, 20), 1, 'B 쪽');
});

test(`${READ_AFTER}초 동안 계속 가까이 있으면 읽은 것으로 치고, 그때 한 번만 알린다`, () => {
  const r = signReader();
  const k = signKey(A);
  eq(r.update(k, READ_AFTER / 2), false, '절반');
  eq(r.isRead(k), false, '아직 안 읽음');
  eq(r.update(k, READ_AFTER / 2), true, '다 채움');
  eq(r.isRead(k), true, '읽음');
  eq(r.update(k, 10), false, '다시 알리지 않는다');
  eq(r.keys(), [k], '저장할 목록');
});

test('중간에 벗어나면 처음부터 다시 센다', () => {
  const r = signReader();
  const k = signKey(A);
  r.update(k, READ_AFTER * 0.8);
  r.update(null, 0.1);
  eq(r.update(k, READ_AFTER * 0.8), false, '다시 들어와 0.8');
  eq(r.isRead(k), false, '안 읽음');
});

test('다른 표지판으로 옮기면 그 표지판을 새로 센다', () => {
  const r = signReader();
  r.update(signKey(A), READ_AFTER * 0.8);
  eq(r.update(signKey(B), READ_AFTER * 0.8), false, 'B 0.8');
  eq(r.isRead(signKey(A)), false, 'A 안 읽음');
});

test('저장해 둔 읽음 목록으로 시작할 수 있다', () => {
  const r = signReader(['10:20']);
  eq(r.isRead(signKey(A)), true, 'A 는 저장돼 있다');
  eq(r.isRead(signKey(B)), false, 'B 는 아니다');
});

test('"이름: 설명" 문구는 두 줄로, 쌍점이 없으면 한 줄로', () => {
  eq(splitSign('분사 판: 여기서 뛰면 두 배 높이 올라요'), ['분사 판', '여기서 뛰면 두 배 높이 올라요'], '두 줄');
  eq(splitSign('조심하세요'), ['조심하세요'], '한 줄');
});

test('화면 가장자리 근처의 말풍선은 안쪽으로 당긴다', () => {
  eq(bubbleX(640, 300, 1280), 640, '가운데는 그대로');
  eq(bubbleX(60, 300, 1280), 162, '왼쪽 끝: 반 폭 150 + 여백 12');
  eq(bubbleX(1250, 300, 1280), 1118, '오른쪽 끝');
});

test('안 읽은 표지판은 멀리서 꼬리표와 빛 무리, 가까이 가면 꼬리표 대신 카드', () => {
  eq(signLook(false, false), { tag: true, glow: true, card: false }, '안 읽음, 멀리');
  eq(signLook(false, true), { tag: false, glow: true, card: true }, '안 읽음, 가까이');
});

test('읽은 표지판은 꼬리표도 빛 무리도 없고, 가까이 가면 카드만 뜬다', () => {
  eq(signLook(true, false), { tag: false, glow: false, card: false }, '읽음, 멀리');
  eq(signLook(true, true), { tag: false, glow: false, card: true }, '읽음, 가까이');
});

test('표지판 자리 좌우 1.5칸 안의 같은 윗면에는 윗면 장식을 놓지 않는다', () => {
  eq(besideSign([A], 10, 20), true, '바로 그 자리');
  eq(besideSign([A], 8.6, 20), true, '왼쪽 1.4칸');
  eq(besideSign([A], 11.4, 20), true, '오른쪽 1.4칸');
  eq(besideSign([A], 8.4, 20), false, '왼쪽 1.6칸');
  eq(besideSign([A], 11.6, 20), false, '오른쪽 1.6칸');
  eq(besideSign([A], 10, 21), false, '다른 높이의 윗면');
  eq(besideSign([A, B], 14, 20), true, '둘 중 하나라도 가까우면');
  eq(besideSign([], 10, 20), false, '표지판 없음');
});

// ---- 실제 맵 ----
const zoneOf = (h) => ZONES.findIndex((z, i) => h < z.to || i === ZONES.length - 1);
// 높이 h 칸(윗면이 h+1 인 칸)의 행 번호
const row = (h) => HEIGHT - 2 - h;

test('새 장치 다섯 가지마다 처음 나오는 구간에 하나씩 있다', () => {
  const want = [
    [0, '미끄러운 면: 닿으면 설 수 없고 미끄러져요'],
    [1, '꾸벅 발판: 2초 서 있으면 푹 꺼졌다가 돌아와요'],
    [3, '바람 구역: 공중에 있으면 옆으로 밀려요'],
    [4, '달궈진 판: 오래 서 있으면 튕겨 나요'],
    [4, '분사 판: 여기서 뛰면 두 배 높이 올라요'],
  ];
  eq(SIGNS.map((s) => [s.zone, s.text]), want, '구간과 문구');
  for (const s of SIGNS) eq(zoneOf(s.h), s.zone, `${s.text} 의 높이 ${s.h} 가 그 구간 안`);
});

test('표지판마다 장치 종류가 있고, 그 구간에 카드에 넣을 장치 그림이 있다', () => {
  eq(SIGNS.map((s) => s.kind), ['slope', 'nod', 'wind', 'hot', 'boost'], '종류');
  eq(SIGNS.map((s) => SIGN_ART[s.kind]), ['fabric', 'nod', 'flag', 'hot', 'boost'], '그림 이름');
  for (const s of SIGNS) {
    const file = new URL(`../public/assets/${ZONES[s.zone].art}/${SIGN_ART[s.kind]}.png`, import.meta.url);
    ok(existsSync(file), `${s.text}: ${file.pathname} 이 없다`);
  }
});

test('표지판은 방향 표지보다 크다(기본 배율), 좁은 자리만 따로 줄인다', () => {
  const arrows = DECOR.filter((d) => d.img === 'arrow_sign').map((d) => d.s);
  ok(SIGN_S > Math.max(...arrows), `기본 ${SIGN_S} 가 방향 표지 최대 ${Math.max(...arrows)} 보다 크다`);
  eq(signScale({ c: 1, h: 1 }), SIGN_S, '기본');
  eq(signScale({ c: 1, h: 1, s: 0.3 }), 0.3, '따로 정한 배율');
});

test('표지판은 지형 윗면 위에 서 있고, 표지판과 머리 위 꼬리표 자리가 비어 있다', () => {
  for (const s of SIGNS) {
    eq(level.raw(Math.floor(s.c), row(s.h - 1)), 1, `${s.text}: 바로 아래 칸이 지형`);
    const rows = Math.ceil(signTall(s, ZONES[s.zone].art) / TILE);
    for (let c = Math.floor(s.c - signHalf(s) / TILE); c <= Math.floor(s.c + signHalf(s) / TILE); c++) {
      for (let h = s.h; h < s.h + rows; h++) eq(level.raw(c, row(h)), 0, `${s.text}: 칸 ${c}, 높이 ${h} 가 빈 칸`);
    }
  }
});

let failed = 0;
for (const t of tests) {
  try { t.fn(); console.log(`ok   ${t.name}`); } catch (e) { failed++; console.log(`FAIL ${t.name}\n     ${e.message}`); }
}
console.log(`${tests.length - failed}/${tests.length} 통과`);
process.exit(failed ? 1 : 0);
