// public/bgm.js 시험. 소리는 내지 않는다: 곡 고르기와 반복 구간은 순수 함수로, 재생은 가짜 AudioContext 로 확인한다.
// 사용: node tools/bgm_test.mjs
import { zoneWithLag, pickTrack, loopPoints, createBgm, TRACKS, ZONE_TRACKS } from '../public/bgm.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};
const ZONES = [{ from: 0, to: 135 }, { from: 135, to: 261 }, { from: 261, to: 397 }];

test('높이로 구간을 고르되, 경계에서 3칸 안쪽으로 왔다 갔다 하면 그대로 둔다', () => {
  eq(zoneWithLag(10, ZONES, null), 0, '처음');
  eq(zoneWithLag(136, ZONES, 0), 0, '경계 1칸 위: 아직 구간 1 곡');
  eq(zoneWithLag(137.9, ZONES, 0), 0, '경계 2.9칸 위');
  eq(zoneWithLag(138, ZONES, 0), 1, '경계 3칸 위: 구간 2');
  eq(zoneWithLag(133, ZONES, 1), 1, '다시 2칸 내려옴: 구간 2 그대로');
  eq(zoneWithLag(131.9, ZONES, 1), 0, '3칸 넘게 내려옴: 구간 1');
  eq(zoneWithLag(300, ZONES, 0), 2, '멀리 옮기면 바로 그 구간');
  eq(zoneWithLag(9999, ZONES, null), 2, '꼭대기 위는 마지막 구간');
});

test('화면 상태와 구간으로 곡을 고른다. 곡이 없는 구간은 null(효과음만)', () => {
  eq(pickTrack('title', 0), 'title', '제목');
  eq(pickTrack('select', 3), 'title', '고르기');
  eq(pickTrack('nickname', 3), 'title', '닉네임 창');
  eq(pickTrack('play', 0), 'nyc', '구간 1');
  eq(pickTrack('confirm', 0), 'nyc', '다시 시작 확인 창도 게임 중');
  eq(pickTrack('play', 1), null, '구간 2: 아직 곡 없음');
  eq(pickTrack('play', 5), 'moon', '달');
  eq(ZONE_TRACKS.length, 6, '구간 6개');
  for (const k of ZONE_TRACKS) if (k) eq(typeof TRACKS[k].src, 'string', `${k} 파일`);
});

test('반복 구간: 앞뒤 무음(모든 채널이 문턱 아래)을 잘라 낸 [시작, 끝)', () => {
  const l = new Float32Array([0, 0.0001, 0, 0.2, -0.5, 0.1, 0.0002, 0]);
  const r = new Float32Array([0, 0, 0.003, 0, 0, 0, 0, 0]);
  eq(loopPoints([l, r]), { start: 2, end: 6 }, '오른쪽 채널 소리가 시작을 당긴다');
  eq(loopPoints([l]), { start: 3, end: 6 }, '한 채널');
  eq(loopPoints([new Float32Array(4)]), { start: 0, end: 4 }, '전부 무음이면 전체');
});

// ---- 가짜 AudioContext ----
function fakeParam(v) {
  const p = { value: v, log: [] };
  p.setValueAtTime = (x) => { p.value = x; p.log.push(['set', x]); };
  p.linearRampToValueAtTime = (x, t) => { p.target = x; p.log.push(['ramp', x, t]); };
  p.cancelScheduledValues = () => {};
  p.setTargetAtTime = (x) => { p.target = x; };
  return p;
}
function fakeCtx() {
  const ctx = {
    currentTime: 0, state: 'running', sources: [],
    destination: {},
    createGain: () => ({ gain: fakeParam(1), connect: (n) => n, disconnect() {} }),
    createBufferSource: () => {
      const s = { buffer: null, loop: false, loopStart: 0, loopEnd: 0, connect: (n) => n, disconnect() {}, started: null, stopped: null };
      s.start = (at, offset) => { s.started = [at, offset]; };
      s.stop = (at) => { s.stopped = at ?? 0; };
      ctx.sources.push(s);
      return s;
    },
    decodeAudioData: async (ab) => {
      if (ab.bad) throw new Error('decode');
      const ch = new Float32Array(100).fill(0.5);
      return { sampleRate: 10, length: 100, duration: 10, numberOfChannels: 1, getChannelData: () => ch };
    },
    suspend: async () => { ctx.state = 'suspended'; },
    resume: async () => { ctx.state = 'running'; },
  };
  return ctx;
}
const flush = () => new Promise((r) => setTimeout(r, 0));
const loads = [];
const okLoad = async (url) => { loads.push(url); return {}; };

test('구간이 바뀌면 이전 곡은 0으로 줄이고 새 곡은 제 음량으로 올린다', async () => {
  const ctx = fakeCtx();
  const bgm = createBgm({ getCtx: () => ctx, load: okLoad, muted: () => false, zones: ZONES });
  bgm.update({ screen: 'play', h: 10 });
  await flush();
  bgm.update({ screen: 'play', h: 10 });
  const nyc = bgm.debug().playing;
  eq(nyc.key, 'nyc', '구간 1 곡');
  eq(nyc.gain.gain.target, TRACKS.nyc.vol, '올라감');
  ctx.currentTime = 50;
  bgm.update({ screen: 'play', h: 300 }); // 구간 3
  await flush();
  bgm.update({ screen: 'play', h: 300 });
  const castle = bgm.debug().playing;
  eq(castle.key, 'castle', '구간 3 곡');
  eq(nyc.gain.gain.target, 0, '이전 곡은 줄어듦');
  eq(castle.gain.gain.target, TRACKS.castle.vol, '새 곡은 올라감');
  const ramp = castle.gain.gain.log.find((l) => l[0] === 'ramp');
  eq(ramp[2] - 50, 2, '2초에 걸쳐');
});

test('곡이 없는 구간으로 가면 이전 곡만 줄이고 아무것도 틀지 않는다', async () => {
  const ctx = fakeCtx();
  const bgm = createBgm({ getCtx: () => ctx, load: okLoad, muted: () => false, zones: ZONES });
  bgm.update({ screen: 'play', h: 10 });
  await flush();
  bgm.update({ screen: 'play', h: 10 });
  const nyc = bgm.debug().playing;
  bgm.update({ screen: 'play', h: 200 });
  await flush();
  eq(bgm.debug().playing, null, '없음');
  eq(nyc.gain.gain.target, 0, '이전 곡 줄어듦');
});

test('음소거(M)이거나 배경음악 끔(B)이면 배경음악 전체 음량이 0', async () => {
  const ctx = fakeCtx();
  let muted = false;
  const store = {};
  const bgm = createBgm({ getCtx: () => ctx, load: okLoad, muted: () => muted, zones: ZONES, storage: { getItem: (k) => store[k] ?? null, setItem: (k, v) => { store[k] = v; } } });
  bgm.update({ screen: 'title', h: 0 });
  eq(bgm.debug().bus.gain.value, 0.35, '기본 음량');
  muted = true;
  bgm.update({ screen: 'title', h: 0 });
  eq(bgm.debug().bus.gain.value, 0, '음소거');
  muted = false;
  eq(bgm.toggle(), true, '끔');
  bgm.update({ screen: 'title', h: 0 });
  eq(bgm.debug().bus.gain.value, 0, 'B 로 끔');
  eq(store['pheeeew-climb-bgm'], 'off', '저장');
  eq(bgm.toggle(), false, '다시 켬');
  bgm.update({ screen: 'title', h: 0 });
  eq(bgm.debug().bus.gain.value, 0.35, '다시 켜짐');
});

test('파일을 못 받거나 디코딩에 실패해도 예외 없이 조용하다', async () => {
  for (const load of [async () => { throw new Error('404'); }, async () => ({ bad: true })]) {
    const ctx = fakeCtx();
    const bgm = createBgm({ getCtx: () => ctx, load, muted: () => false, zones: ZONES });
    bgm.update({ screen: 'play', h: 10 });
    await flush();
    await flush();
    bgm.update({ screen: 'play', h: 10 });
    eq(bgm.debug().playing, null, '재생 없음');
    eq(ctx.sources.length, 0, '소스 없음');
  }
});

test('AudioContext 가 아직 없으면(첫 입력 전) 아무것도 하지 않는다', () => {
  const bgm = createBgm({ getCtx: () => null, load: okLoad, muted: () => false, zones: ZONES });
  bgm.update({ screen: 'title', h: 0 });
  eq(bgm.debug().playing, null, '재생 없음');
});

test('반복: trim 곡은 잘라 낸 구간으로 loop, cross 곡은 끝나기 전에 다음 판을 겹쳐 튼다', async () => {
  const ctx = fakeCtx();
  const bgm = createBgm({ getCtx: () => ctx, load: okLoad, muted: () => false, zones: [{ from: 0, to: 10 }, { from: 10, to: 20 }, { from: 20, to: 30 }, { from: 30, to: 40 }] });
  bgm.update({ screen: 'play', h: 5 }); // nyc: trim
  await flush();
  bgm.update({ screen: 'play', h: 5 });
  const s = ctx.sources.at(-1);
  eq([s.loop, s.loopStart, s.loopEnd], [true, 0, 10], 'trim 반복');
  bgm.update({ screen: 'play', h: 35 }); // balloon: cross
  await flush();
  bgm.update({ screen: 'play', h: 35 });
  const n = ctx.sources.length;
  const first = ctx.sources.at(-1);
  eq(first.loop, false, 'cross 는 loop 하지 않는다');
  ctx.currentTime = 10 - TRACKS.balloon.cross - 1; // 다음 판 시작 1초 전
  bgm.update({ screen: 'play', h: 35 });
  eq(ctx.sources.length, n + 1, '다음 판 예약');
  eq(ctx.sources.at(-1).started[0], first.started[0] + 10 - TRACKS.balloon.cross, '끝나기 cross 초 전에 시작');
});

test('탭이 숨겨지면 멈추고 돌아오면 이어 튼다', async () => {
  const ctx = fakeCtx();
  const bgm = createBgm({ getCtx: () => ctx, load: okLoad, muted: () => false, zones: ZONES });
  bgm.hidden(true);
  eq(ctx.state, 'suspended', '멈춤');
  bgm.hidden(false);
  eq(ctx.state, 'running', '이어서');
});

test('다음 구간 경계에 가까워지면 다음 곡을 미리 받는다', async () => {
  loads.length = 0;
  const ctx = fakeCtx();
  const Z = [{ from: 0, to: 135 }, { from: 135, to: 261 }, { from: 261, to: 397 }, { from: 397, to: 523 }];
  const bgm = createBgm({ getCtx: () => ctx, load: okLoad, muted: () => false, zones: Z });
  bgm.update({ screen: 'play', h: 300 });
  await flush();
  eq(loads, [TRACKS.castle.src], '지금 곡만');
  bgm.update({ screen: 'play', h: 393 }); // 경계(397)까지 4칸
  await flush();
  eq(loads, [TRACKS.castle.src, TRACKS.balloon.src], '다음 곡 미리');
});

let fail = 0;
for (const { name, fn } of tests) {
  try { await fn(); console.log(`ok   ${name}`); } catch (e) { fail++; console.log(`FAIL ${name}\n     ${e.message}`); }
}
console.log(fail ? `${fail}개 실패` : `${tests.length}개 모두 통과`);
process.exit(fail ? 1 : 0);
