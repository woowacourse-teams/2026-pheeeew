// public/net.js 시험. 가짜 fetch·WebSocket·시계를 넣어 서버 없이 확인한다.
// 사용: node tools/net_test.mjs
import { createNet } from '../public/net.js';

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A} !== ${B}`);
};

// 가짜 시계: advance(ms) 만큼 시간을 흘리며 때가 된 타이머를 순서대로 부른다
function clock() {
  let t = 0;
  let seq = 0;
  const timers = new Map();
  return {
    now: () => t,
    setTimeout: (fn, ms) => { const id = ++seq; timers.set(id, { at: t + Math.max(0, ms), fn }); return id; },
    clearTimeout: (id) => timers.delete(id),
    advance(ms) {
      const end = t + ms;
      for (;;) {
        let next = null;
        for (const [id, x] of timers) if (x.at <= end && (!next || x.at < next[1].at)) next = [id, x];
        if (!next) break;
        timers.delete(next[0]);
        t = next[1].at;
        next[1].fn();
      }
      t = end;
    },
  };
}

// 가짜 웹소켓: 만든 것을 sockets 에 모은다
function fakeSockets() {
  const sockets = [];
  class FakeWebSocket {
    constructor(url) { this.url = url; this.sent = []; this.readyState = 0; sockets.push(this); }
    send(data) { if (this.readyState !== 1) throw new Error('열리지 않은 소켓에 보냄'); this.sent.push(JSON.parse(data)); }
    close() { this.readyState = 3; this.closedByClient = true; }
    // 시험이 서버 쪽 일을 흉내 낸다
    serverOpen() { this.readyState = 1; this.onopen?.({}); }
    serverSend(msg) { this.onmessage?.({ data: JSON.stringify(msg) }); }
    serverClose(code = 1006, reason = '') { this.readyState = 3; this.onclose?.({ code, reason }); }
  }
  return { sockets, WebSocket: FakeWebSocket };
}

// 가짜 fetch: 경로별 응답을 정하고, 받은 요청을 calls 에 모은다
function fakeFetch(routes) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    calls.push({ url, ...init, body: init.body && JSON.parse(init.body) });
    const key = `${init.method ?? 'GET'} ${url}`;
    const r = typeof routes === 'function' ? routes(key, calls.at(-1)) : routes[key];
    if (!r) throw new TypeError('Failed to fetch');
    return { status: r.status, json: async () => r.body };
  };
  return { calls, fetch };
}

const API = 'http://127.0.0.1:4320/api/v1/climb';
const welcome = (run = null) => ({ t: 'welcome', me: { id: 'g1', nickname: '피유', tag: '4f2a' }, run, online: 1, serverTime: 0 });

function setup({ routes = {}, fetch } = {}) {
  const c = clock();
  const s = fakeSockets();
  const f = fakeFetch(routes);
  const net = createNet({ map: 'v1', api: '/api/v1/climb', base: 'http://127.0.0.1:4320/', fetch: fetch ?? f.fetch, WebSocket: s.WebSocket, setTimeout: c.setTimeout, clearTimeout: c.clearTimeout, now: c.now });
  const statuses = [];
  net.on('status', (x) => statuses.push(x));
  return { net, clock: c, sockets: s.sockets, calls: f.calls, statuses };
}
// 연결하고 hello 에 welcome 까지 받은 상태
function online(t, opts = { character: 'angry' }, run = null) {
  t.net.connect(opts);
  const ws = t.sockets.at(-1);
  ws.serverOpen();
  ws.serverSend(welcome(run));
  return ws;
}

test('첫 접속: me 가 401 이면 손님을 만들고, 그 뒤 me 는 200', async () => {
  let made = false;
  const t = setup({ routes: (key) => {
    if (key === `GET ${API}/me`) return made ? { status: 200, body: { id: 'g1', nickname: '피유', tag: '4f2a', run: null } } : { status: 401, body: { code: 'UNAUTHORIZED' } };
    if (key === `POST ${API}/guests`) { made = true; return { status: 201, body: { id: 'g1', nickname: '피유', tag: '4f2a' } }; }
  } });
  eq((await t.net.me()).status, 401, '처음 me');
  const created = await t.net.createGuest('피유');
  eq([created.status, created.body.tag], [201, '4f2a'], '손님 만들기');
  const post = t.calls[1];
  eq([post.method, post.credentials, post.body], ['POST', 'include', { nickname: '피유' }], '손님 만들기 요청');
  eq(post.headers['Content-Type'], 'application/json', '본문 형식');
  eq((await t.net.me()).body.nickname, '피유', '다시 me');
  eq(t.calls.every((x) => x.credentials === 'include'), true, '모든 요청에 쿠키');
});

test('닉네임 규칙 위반은 400 과 사유 코드를 그대로 돌려준다', async () => {
  const t = setup({ routes: { [`POST ${API}/guests`]: { status: 400, body: { code: 'NICKNAME_LENGTH', message: '…' } }, [`PATCH ${API}/me`]: { status: 429, body: { code: 'NICKNAME_CHANGE_LIMITED' } } } });
  const r = await t.net.createGuest('피');
  eq([r.status, r.body.code], [400, 'NICKNAME_LENGTH'], '400');
  const p = await t.net.rename('피유유');
  eq([p.status, p.body.code, t.calls[1].method], [429, 'NICKNAME_CHANGE_LIMITED', 'PATCH'], '이름 바꾸기');
});

test('순위표는 board 와 맵 버전을 붙여 부른다', async () => {
  const t = setup({ routes: { [`GET ${API}/ranking?board=today&map=v1`]: { status: 200, body: { top: [], me: null, online: 0 } } } });
  eq((await t.net.ranking('today')).body.online, 0, '오늘 순위');
});

test('연결하면 hello 를 먼저 보내고, welcome 을 받으면 online', () => {
  const t = setup();
  const got = [];
  t.net.on('welcome', (m) => got.push(m.me.tag));
  t.net.connect({ character: 'angry' });
  const ws = t.sockets[0];
  eq(ws.url, 'ws://127.0.0.1:4320/api/v1/climb/ws', '주소');
  eq(t.net.status, 'connecting', '여는 중');
  ws.serverOpen();
  eq(ws.sent, [{ t: 'hello', v: 1, map: 'v1', character: 'angry' }], 'hello');
  ws.serverSend(welcome());
  eq([t.net.status, got], ['online', ['4f2a']], 'welcome');
  eq(t.statuses, ['connecting', 'online'], '상태 변화');
});

test('rank·event 를 구독자에게 넘긴다', () => {
  const t = setup();
  const got = [];
  t.net.on('rank', (m) => got.push(m.me.rank));
  t.net.on('event', (m) => got.push(m.kind));
  const ws = online(t);
  ws.serverSend({ t: 'rank', top: [], me: { rank: 4, best: 131 }, near: [], online: 3 });
  ws.serverSend({ t: 'event', kind: 'zone', nickname: '피유', tag: '4f2a', zone: 1 });
  eq(got, [4, 'zone'], '받은 것');
});

test('https 주소면 wss 로 연결한다', () => {
  const c = clock();
  const s = fakeSockets();
  const net = createNet({ map: 'v1', api: 'https://api.example.com/api/v1/climb', base: 'https://climb.example.com/', fetch: async () => ({}), WebSocket: s.WebSocket, setTimeout: c.setTimeout, clearTimeout: c.clearTimeout, now: c.now });
  net.connect({ character: 'tired' });
  eq(s.sockets[0].url, 'wss://api.example.com/api/v1/climb/ws', '주소');
});

test('끊기면 1초, 2초, 4초… 최대 30초 간격으로 다시 연결하고, welcome 을 받으면 간격을 처음으로', () => {
  const t = setup();
  t.net.connect({ character: 'angry' });
  const gaps = [];
  for (let i = 0; i < 7; i++) {
    const before = t.sockets.length;
    t.sockets.at(-1).serverClose(1006);
    eq(t.net.status, 'offline', '끊긴 뒤 상태');
    let waited = 0;
    while (t.sockets.length === before && waited < 60000) { t.clock.advance(100); waited += 100; }
    gaps.push(waited);
  }
  eq(gaps, [1000, 2000, 4000, 8000, 16000, 30000, 30000], '간격');
  const ws = t.sockets.at(-1);
  ws.serverOpen();
  eq(ws.sent[0].t, 'hello', '다시 연결하면 hello 부터');
  ws.serverSend(welcome());
  ws.serverClose(1012, 'SERVER_DRAINING');
  t.clock.advance(999);
  const n = t.sockets.length;
  t.clock.advance(1);
  eq(t.sockets.length, n + 1, 'welcome 뒤에는 다시 1초');
});

test('CONNECTION_REPLACED 면 다시 연결하지 않는다', () => {
  const t = setup();
  const ws = online(t);
  ws.serverSend({ t: 'error', code: 'CONNECTION_REPLACED' });
  ws.serverClose(1008, 'CONNECTION_REPLACED');
  t.clock.advance(120000);
  eq([t.sockets.length, t.net.status], [1, 'replaced'], '다시 연결 안 함');
});

test('닫힘 사유만 CONNECTION_REPLACED 여도 다시 연결하지 않는다', () => {
  const t = setup();
  online(t).serverClose(1008, 'CONNECTION_REPLACED');
  t.clock.advance(120000);
  eq(t.sockets.length, 1, '다시 연결 안 함');
});

test('서버가 없어도 예외 없이 offline', async () => {
  const t = setup({ fetch: async () => { throw new TypeError('Failed to fetch'); } });
  eq(await t.net.me(), { status: 0, body: null }, 'me');
  eq((await t.net.createGuest('피유')).status, 0, '손님 만들기');
  eq((await t.net.ranking('all')).status, 0, '순위');
  t.net.connect({ character: 'angry' });
  t.sockets[0].serverClose(1006); // 연결 거절
  eq(t.net.status, 'offline', '상태');
  t.net.reportPos({ h: 3, best: 3, falls: 0, time: 1, x: 120 });
  t.net.reportZone({ zone: 1, time: 1 });
  t.net.reportFinish({ time: 9, falls: 0 });
  t.net.restart();
  eq(t.sockets[0].sent, [], '아무것도 보내지 않음');
  // 생성자가 던지는 브라우저(잘못된 주소 등)
  const c = clock();
  const net = createNet({ map: 'v1', api: '/api/v1/climb', base: 'http://x/', fetch: async () => { throw new Error(); }, WebSocket: class { constructor() { throw new SyntaxError('bad'); } }, setTimeout: c.setTimeout, clearTimeout: c.clearTimeout, now: c.now });
  net.connect({ character: 'angry' });
  c.advance(5000);
  eq(net.status, 'offline', '생성자 오류');
});

const kinds = (ws) => ws.sent.map((m) => m.t);

test('pos 는 바로 보내되 2초에 한 번으로 줄이고, 밀린 것은 마지막 값만 2초 끝에 보낸다', () => {
  const t = setup();
  const ws = online(t);
  t.net.reportPos({ h: 3.4, best: 3, falls: 0, time: 1.7, x: 120.6 });
  eq(ws.sent.at(-1), { t: 'pos', h: 3, best: 3, falls: 0, time: 1, x: 121 }, '정수로 바로 보냄');
  t.clock.advance(500);
  t.net.reportPos({ h: 5, best: 5, falls: 0, time: 2, x: 140 });
  t.clock.advance(1000);
  t.net.reportPos({ h: 2, best: 5, falls: 1, time: 3, x: 80 });
  eq(kinds(ws), ['hello', 'pos'], '2초 안에는 더 보내지 않음');
  t.clock.advance(499);
  eq(kinds(ws).length, 2, '아직');
  t.clock.advance(1);
  eq(ws.sent.at(-1), { t: 'pos', h: 2, best: 5, falls: 1, time: 3, x: 80 }, '2초 끝에 마지막 값');
  t.clock.advance(10000);
  eq(kinds(ws).length, 3, '새 보고가 없으면 보내지 않음');
  t.net.reportPos({ h: 4, best: 5, falls: 1, time: 15, x: 80 });
  eq(kinds(ws).length, 4, '2초 지났으면 바로');
});

test('best 는 h 보다 작게 보내지 않는다', () => {
  const t = setup();
  const ws = online(t);
  t.net.reportPos({ h: 7, best: 5, falls: 0, time: 1, x: 0 });
  eq(ws.sent.at(-1).best, 7, 'best');
});

test('끊긴 동안의 pos 는 다시 연결되면 마지막 값만 보낸다', () => {
  const t = setup();
  online(t).serverClose(1006);
  t.net.reportPos({ h: 3, best: 3, falls: 0, time: 1, x: 0 });
  t.net.reportPos({ h: 9, best: 9, falls: 0, time: 4, x: 0 });
  t.clock.advance(1000);
  const ws = t.sockets.at(-1);
  ws.serverOpen();
  eq(kinds(ws), ['hello'], 'welcome 전에는 보내지 않음');
  ws.serverSend(welcome());
  eq(ws.sent.slice(1), [{ t: 'pos', h: 9, best: 9, falls: 0, time: 4, x: 0 }], '마지막 값');
});

test('구간은 순서대로 보내고, 다시 연결하면 지난 구간부터 다시 보낸다(서버가 모르는 진행 따라잡기)', () => {
  const t = setup();
  const ws = online(t, { character: 'angry', zone: 2 });
  eq(ws.sent.slice(1), [{ t: 'zone', zone: 1, time: 0 }, { t: 'zone', zone: 2, time: 0 }], '이어 하기 구간');
  t.net.reportZone({ zone: 4, time: 300.9 });
  eq(ws.sent.slice(3), [{ t: 'zone', zone: 3, time: 300 }, { t: 'zone', zone: 4, time: 300 }], '건너뛴 구간도 차례로');
  t.net.reportZone({ zone: 4, time: 301 });
  eq(ws.sent.length, 5, '같은 구간은 한 번');
  ws.serverClose(1006);
  t.clock.advance(1000);
  const ws2 = t.sockets.at(-1);
  ws2.serverOpen();
  ws2.serverSend(welcome());
  eq(ws2.sent.slice(1).map((m) => m.zone), [1, 2, 3, 4], '다시 연결: 1구간부터');
});

test('완주는 한 번만 보내고, 그 뒤로는 그 등반을 더 보고하지 않는다', () => {
  const t = setup();
  const ws = online(t, { character: 'angry', zone: 5 });
  t.net.reportFinish({ time: 640.5, falls: 12 });
  eq(ws.sent.at(-1), { t: 'finish', time: 640, falls: 12 }, '완주');
  const n = ws.sent.length;
  t.net.reportPos({ h: 663, best: 663, falls: 12, time: 650, x: 0 });
  t.net.reportZone({ zone: 5, time: 650 });
  t.net.reportFinish({ time: 650, falls: 12 });
  eq(ws.sent.length, n, '더 보내지 않음');
  ws.serverClose(1006);
  t.clock.advance(1000);
  const ws2 = t.sockets.at(-1);
  ws2.serverOpen();
  ws2.serverSend(welcome());
  eq(kinds(ws2), ['hello'], '다시 연결해도 완주한 등반은 보내지 않음');
});

test('끊긴 동안 완주하면 다시 연결된 뒤 마지막 높이를 먼저 보내고 구간을 따라잡고 완주를 보낸다', () => {
  const t = setup();
  online(t, { character: 'angry', zone: 4 }).serverClose(1006);
  t.net.reportPos({ h: 663, best: 663, falls: 3, time: 640, x: 200 });
  t.net.reportZone({ zone: 5, time: 600 });
  t.net.reportFinish({ time: 640, falls: 3 });
  t.clock.advance(1000);
  const ws = t.sockets.at(-1);
  ws.serverOpen();
  ws.serverSend(welcome({ map: 'v1', character: 'angry', height: 600, best: 600, x: 0, falls: 3, time: 600 }));
  eq(kinds(ws), ['hello', 'pos', 'zone', 'zone', 'zone', 'zone', 'zone', 'finish'], '높이, 따라잡기, 완주 순서');
  eq(ws.sent[1].best, 663, '꼭대기 높이');
});

test('구간·완주 앞에는 그 높이의 pos 를 2초 제한을 기다리지 않고 먼저 보낸다', () => {
  const t = setup();
  const ws = online(t);
  t.net.reportPos({ h: 120, best: 120, falls: 0, time: 60, x: 0 });
  t.clock.advance(500);
  t.net.reportPos({ h: 135, best: 135, falls: 0, time: 61, x: 40 });
  t.net.reportZone({ zone: 1, time: 61 });
  eq(ws.sent.slice(1), [
    { t: 'pos', h: 120, best: 120, falls: 0, time: 60, x: 0 },
    { t: 'pos', h: 135, best: 135, falls: 0, time: 61, x: 40 },
    { t: 'zone', zone: 1, time: 61 },
  ], 'pos 다음 구간');
  t.clock.advance(5000);
  eq(kinds(ws).length, 4, '같은 pos 를 2초 끝에 또 보내지 않음');
  t.net.reportPos({ h: 261, best: 261, falls: 0, time: 90, x: 0 });
  t.net.reportZone({ zone: 2, time: 90 });
  eq(kinds(ws).slice(4), ['pos', 'zone'], '이미 보낸 pos 는 다시 보내지 않음');
});

test('처음부터 다시: 연결 중이면 바로 restart, 아니면 다음 welcome 에 이어 할 등반이 있을 때 보낸다', () => {
  const t = setup();
  const ws = online(t, { character: 'angry', zone: 2 });
  t.net.restart();
  eq(ws.sent.at(-1), { t: 'restart' }, '바로');
  t.net.reportPos({ h: 1, best: 1, falls: 0, time: 1, x: 0 });
  eq(ws.sent.at(-1).t, 'pos', '새 등반 보고');
  ws.serverClose(1006);
  t.clock.advance(1000);
  const ws2 = t.sockets.at(-1);
  ws2.serverOpen();
  ws2.serverSend(welcome({ map: 'v1', character: 'angry', height: 1, best: 1, x: 0, falls: 0, time: 1 }));
  eq(kinds(ws2), ['hello'], '새 등반은 구간을 다시 보내지 않음');

  const u = setup();
  u.net.restart(); // 아직 연결 전
  const a = online(u, { character: 'tired' }, { map: 'v1', character: 'angry', height: 300, best: 300, x: 0, falls: 0, time: 90 });
  eq(kinds(a), ['hello', 'restart'], '이어 할 등반이 있으면 restart');
  const v = setup();
  v.net.restart();
  eq(kinds(online(v)), ['hello'], '이어 할 등반이 없으면 보내지 않음');
});

test('다른 감정이로 connect 하면 지금 연결을 닫고 새 hello 를 보낸다. 같은 감정이면 그대로', () => {
  const t = setup();
  const ws = online(t);
  t.net.connect({ character: 'angry' });
  eq(t.sockets.length, 1, '같은 감정이');
  t.net.connect({ character: 'tired' });
  eq([ws.closedByClient, t.sockets.length], [true, 2], '닫고 새로');
  ws.serverClose(1000); // 닫은 소켓의 close 는 무시한다
  t.clock.advance(60000);
  eq(t.sockets.length, 2, '닫은 소켓 때문에 다시 연결하지 않음');
  t.sockets[1].serverOpen();
  eq(t.sockets[1].sent[0].character, 'tired', 'hello');
});

let failed = 0;
for (const { name, fn } of tests) {
  try { await fn(); console.log(`통과  ${name}`); } catch (e) { failed++; console.log(`실패  ${name}\n      ${e.message}`); }
}
console.log(failed ? `\n${failed}개 실패 / ${tests.length}개` : `\n모두 통과 (${tests.length}개)`);
process.exitCode = failed ? 1 : 0;
