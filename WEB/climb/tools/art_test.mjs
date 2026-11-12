// public/art.js 시험: 높이별로 받을 구간, 목록표 조회, 감정이 그림 배율, 받기 관리.
// 사용: node tools/art_test.mjs
import { zoneOf, zonesAround, AHEAD, zoneFiles, charFiles, LOGICAL, createLoader, loadTree, leaves } from '../public/art.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};

// 구간 3개: 0~100, 100~200, 200~260
const Z = [{ from: 0, to: 100 }, { from: 100, to: 200 }, { from: 200, to: 260 }];

test('경계 높이에 서면 아래 구간(main.js 의 지형 구간과 같은 규칙)', () => {
  eq([zoneOf(-3, Z), zoneOf(0, Z), zoneOf(100, Z), zoneOf(100.1, Z), zoneOf(999, Z)], [0, 0, 0, 1, 2], '구간');
});

test('처음에는 지금 구간과 바로 위 구간', () => {
  eq(AHEAD, 18, '미리 받는 거리(칸)');
  eq(zonesAround(0, Z), [0, 1], '시작');
  eq(zonesAround(150, Z), [1, 2], '구간 2 한가운데');
});

test('다음 경계 18칸 앞부터 그 위 구간도, 아래 경계 18칸 안이면 아래 구간도', () => {
  eq(zonesAround(100 - 18 - 0.1, Z), [0, 1], '경계 18칸 밖');
  eq(zonesAround(100 - 18, Z), [0, 1, 2], '경계 18칸 앞');
  eq(zonesAround(100 + 17, Z), [0, 1, 2], '위 구간에 막 들어섬: 떨어질 수 있는 아래 구간');
  eq(zonesAround(250, Z), [2], '마지막 구간 꼭대기 근처');
});

const MAN = {
  zones: {
    nyc: { bg: { src: 'nyc/bg.webp?v=1', w: 1024, h: 1536 }, nod: { src: 'nyc/nod.webp?v=2', w: 480, h: 200 }, kit: { top: [{ src: 'nyc/kit/top_1.webp?v=3', w: 256, h: 256 }, { src: 'nyc/kit/top_2.webp?v=4', w: 256, h: 256 }] } },
  },
  characters: { angry: { front_body: { src: 'characters/angry/front_body.webp?v=5', w: 384, h: 384 }, jump_rise: { src: 'characters/angry/jump_rise.webp?v=6', w: 1024, h: 1024 } } },
};

test('목록표 조회: 구간 폴더의 파일 주소와 장식 개수', () => {
  const f = zoneFiles(MAN, 'nyc');
  eq(f.bg, '/assets/nyc/bg.webp?v=1', '배경');
  eq(f.nod, '/assets/nyc/nod.webp?v=2', '발판');
  eq(f.hot, undefined, '없는 그림');
  eq(f.kit.top, ['/assets/nyc/kit/top_1.webp?v=3', '/assets/nyc/kit/top_2.webp?v=4'], '장식 번호순');
  eq(f.kit.hang, undefined, '없는 장식 종류');
  eq(zoneFiles(MAN, 'nowhere'), { kit: {} }, '없는 폴더');
});

test('감정이 그림: 실제 크기와 상관없이 논리 크기 1024 좌표로 그리게 배율을 준다', () => {
  eq(LOGICAL, 1024, '논리 크기');
  eq(charFiles(MAN, 'angry', ['front_body', 'jump_rise', 'side_hand']), {
    front_body: { src: '/assets/characters/angry/front_body.webp?v=5', data: { resolution: 0.375 } },
    jump_rise: { src: '/assets/characters/angry/jump_rise.webp?v=6', data: { resolution: 1 } },
  }, '384px 는 0.375, 1024px 는 1, 없는 부위는 뺀다');
});

test('받기 관리: 같은 주소는 한 번만, 실패하면 null(예외 없음), 진행률', async () => {
  const calls = [];
  const loader = createLoader(async (asset) => {
    calls.push(asset.src ?? asset);
    if (String(asset.src ?? asset).includes('bad')) throw new Error('깨진 그림');
    return `tex:${asset.src ?? asset}`;
  }, { warn: () => {} });
  const [a, a2, bad] = await Promise.all([loader.get('/a.png'), loader.get('/a.png'), loader.get('/bad.png')]);
  eq([a, a2, bad], ['tex:/a.png', 'tex:/a.png', null], '결과');
  eq(calls, ['/a.png', '/bad.png'], '한 번씩만 받는다');
  eq(loader.progress(), { done: 2, total: 2 }, '진행률');
  eq(await loader.get({ src: '/c.png', data: { resolution: 0.375 } }), 'tex:/c.png', '옵션이 있는 그림');
  eq(loader.has('/c.png'), true, '받은 것');
  eq(loader.has('/d.png'), false, '아직 안 받은 것');
});

test('주소 나무(구간 그림)를 같은 모양의 텍스처 나무로 함께 받는다', async () => {
  const order = [];
  const loader = createLoader(async (src) => { order.push(src); await new Promise((r) => setTimeout(r, src.includes('top_1') ? 20 : 1)); return src.includes('top_2') ? Promise.reject(new Error('x')) : `T${src}`; }, { warn: () => {} });
  const t = await loadTree(zoneFiles(MAN, 'nyc'), loader.get);
  eq(t, { bg: 'T/assets/nyc/bg.webp?v=1', nod: 'T/assets/nyc/nod.webp?v=2', kit: { top: ['T/assets/nyc/kit/top_1.webp?v=3', null] } }, '모양 그대로, 실패한 장은 null');
  eq(order.length, 4, '모두 한꺼번에 요청(직렬로 기다리지 않는다)');
  eq(leaves(zoneFiles(MAN, 'nyc')).length, 4, '잎(받을 그림 수)');
  eq(leaves(charFiles(MAN, 'angry', ['front_body'])), [{ src: '/assets/characters/angry/front_body.webp?v=5', data: { resolution: 0.375 } }], 'asset 잎');
});

let fail = 0;
for (const t of tests) {
  try { await t.fn(); console.log(`ok   ${t.name}`); } catch (e) { fail++; console.log(`FAIL ${t.name}\n     ${e.message}`); }
}
console.log(fail ? `${fail}개 실패` : `${tests.length}개 모두 통과`);
if (fail) process.exit(1);
