// 피유 등반 서버 연결(BE/climb/README.md 규약). 화면을 모른다.
// 서버가 없거나 끊겨도 예외를 던지지 않는다. REST 는 { status, body } 를 돌려주고(닿지 않으면 status 0), 웹소켓은 상태만 바꾼다.
// 브라우저 전역(fetch, WebSocket, 타이머)은 시험에서 가짜로 바꿔 넣는다(tools/net_test.mjs).

export function createNet({
  map,
  api = globalThis.CLIMB_API ?? '/api/v1/climb',
  base = globalThis.location?.href,
  fetch = (...a) => globalThis.fetch(...a),
  WebSocket = globalThis.WebSocket,
  setTimeout = (...a) => globalThis.setTimeout(...a),
  clearTimeout = (...a) => globalThis.clearTimeout(...a),
  now = () => Date.now(),
} = {}) {
  const root = new URL(api, base).href.replace(/\/$/, '');

  async function call(method, path, body) {
    try {
      const res = await fetch(root + path, {
        method,
        credentials: 'include', // 손님 쿠키는 HttpOnly 라 스크립트가 읽지 않는다
        headers: body ? { 'Content-Type': 'application/json' } : undefined,
        body: body ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout?.(5000),
      });
      let data = null;
      try { data = await res.json(); } catch { /* 본문 없음 */ }
      return { status: res.status, body: data };
    } catch {
      return { status: 0, body: null };
    }
  }

  // ---- 구독 ----
  const subs = { rank: [], event: [], welcome: [], status: [] };
  const emit = (type, data) => {
    for (const fn of subs[type]) {
      try { fn(data); } catch (e) { console.error(e); } // 화면 쪽 실수가 연결을 끊지 않게
    }
  };
  let status = 'offline'; // offline | connecting | online | replaced
  const setStatus = (s) => { if (s !== status) { status = s; emit('status', s); } };

  // ---- 웹소켓 ----
  const wsUrl = `${root.replace(/^http/, 'ws')}/ws`;
  let ws = null;
  let live = false; // welcome 을 받았다
  let character = null;
  let retry = 0; // 연달아 실패한 횟수
  let retryTimer = null;
  const raw = (msg) => { try { ws.send(JSON.stringify(msg)); } catch { /* 닫히는 중 */ } };

  // ---- 지금 등반의 보고 ----
  // 서버는 구간을 순서대로만 받으므로, 연결할 때마다 1구간부터 다시 보낸다(이미 아는 구간은 서버가 조용히 버린다).
  // 덕분에 끊긴 동안 넘은 구간이나 완주도 다시 연결되면 따라잡는다.
  let run = { zone: 0, time: 0, finish: null, done: false, restart: false };
  let sentZone = 0;
  let pos = null; // 아직 보내지 않은 마지막 pos
  let posAt = -Infinity;
  let posTimer = null;
  const int = (v) => Math.max(0, Math.floor(Number(v) || 0)); // 서버는 정수만 받는다

  function flush() {
    if (!live || run.done) return;
    while (sentZone < run.zone) raw({ t: 'zone', zone: ++sentZone, time: run.time });
    if (run.finish) {
      raw({ t: 'finish', ...run.finish });
      run.finish = null;
      run.done = true; // 완주한 등반은 더 보고하지 않는다(다시 연결하면 서버는 새 등반을 만든다)
      return;
    }
    sendPos();
  }
  // 착지할 때마다 부르지만 2초에 한 번만 보낸다. 2초 안에 온 것은 마지막 값만 2초 끝에 보낸다.
  function sendPos() {
    if (!live || !pos || posTimer !== null) return;
    const wait = posAt + 2000 - now();
    if (wait > 0) {
      posTimer = setTimeout(() => { posTimer = null; sendPos(); }, wait);
      return;
    }
    if (run.done) return;
    raw(pos);
    pos = null;
    posAt = now();
  }

  function open() {
    let sock;
    retryTimer = null;
    try { sock = new WebSocket(wsUrl); } catch { setStatus('offline'); later(); return; }
    ws = sock;
    setStatus('connecting');
    let replaced = false;
    sock.onopen = () => raw({ t: 'hello', v: 1, map, character });
    sock.onmessage = (e) => {
      if (ws !== sock) return;
      let m;
      try { m = JSON.parse(e.data); } catch { return; }
      if (m.t === 'welcome') {
        live = true;
        retry = 0;
        sentZone = 0;
        if (run.restart) {
          run.restart = false;
          if (m.run) raw({ t: 'restart' }); // hello 가 이어 붙인 예전 등반을 새 등반으로 바꾼다
        }
        setStatus('online');
        flush();
        emit('welcome', m);
      } else if (m.t === 'rank' || m.t === 'event') emit(m.t, m);
      else if (m.t === 'error' && m.code === 'CONNECTION_REPLACED') replaced = true;
    };
    sock.onerror = () => {}; // 뒤따르는 close 에서 처리한다
    sock.onclose = (e) => {
      if (ws !== sock) return;
      ws = null;
      live = false;
      // 같은 손님이 다른 탭에서 연결했다. 다시 연결하면 서로 밀어내므로 멈춘다.
      if (replaced || e.reason === 'CONNECTION_REPLACED') { setStatus('replaced'); return; }
      setStatus('offline');
      later();
    };
  }
  // 1초, 2초, 4초… 최대 30초
  function later() {
    clearTimeout(retryTimer);
    retryTimer = setTimeout(open, Math.min(30000, 1000 * 2 ** retry++));
  }

  return {
    get status() { return status; },
    on(type, fn) { subs[type].push(fn); },
    // zone = 이미 넘은 구간 수, finished = 이미 완주한 등반(이어 하기)
    connect({ character: c, zone = 0, finished = false }) {
      run = { ...run, zone: Math.max(run.zone, zone), done: finished };
      if (ws && c === character) { flush(); return; }
      if (ws) { const old = ws; ws = null; live = false; old.close(); } // 감정이가 바뀌면 hello 를 다시 한다
      character = c;
      retry = 0;
      clearTimeout(retryTimer);
      open();
    },
    reportPos({ h, best, falls, time, x }) {
      if (run.done) return;
      pos = { t: 'pos', h: int(h), best: Math.max(int(best), int(h)), falls: int(falls), time: int(time), x: Math.round(Number(x) || 0) };
      sendPos();
    },
    reportZone({ zone, time }) {
      if (run.done) return;
      run.zone = Math.max(run.zone, zone);
      run.time = int(time);
      flush();
    },
    reportFinish({ time, falls }) {
      if (run.done) return;
      run.finish = { time: int(time), falls: int(falls) };
      flush();
    },
    restart() {
      run = { zone: 0, time: 0, finish: null, done: false, restart: !live };
      pos = null;
      if (live) { raw({ t: 'restart' }); sentZone = 0; }
    },
    me: () => call('GET', '/me'),
    createGuest: (nickname) => call('POST', '/guests', { nickname }),
    rename: (nickname) => call('PATCH', '/me', { nickname }),
    ranking: (board) => call('GET', `/ranking?board=${encodeURIComponent(board)}&map=${encodeURIComponent(map)}`),
  };
}
