// 실제 main.js 이벤트를 최소 DOM 대역에서 실행한다. 서버·브라우저 설치 없이 입력/비동기 경계를 검증한다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { StairsGame } from '../public/stairs/game.js';
import { direction, cap } from '../public/stairs/rules.js';
const source = fs.readFileSync(new URL('../public/stairs/main.js', import.meta.url), 'utf8').replace(/^import .*;\n/gm, '');
const html = fs.readFileSync(new URL('../public/stairs.html', import.meta.url), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
async function tab() {
  const events = {}, nodes = {}, calls = [], writes = [];
  let now = 1000, frame, drawnGame, reply = async () => ({ status: 200, body: { height: 0, timeMs: 0, rank: 7, best: { height: 4, timeMs: 300 } } });
  const document = { activeElement: null, hidden: false, getElementById: id => nodes[id], querySelectorAll: () => [] };
  class Element {
    constructor(id) { this.id = id; this.value = ''; this.hidden = false; this.textContent = ''; this.style = {}; this.parentElement = this; }
    addEventListener(type, fn) { this[type] = fn; }
    setAttribute(name, value) { this[name] = value; }
    focus() { document.activeElement = this; }
    blur() { document.activeElement = null; }
  }
  class Input extends Element {}
  for (const [, id] of html.matchAll(/id="([^"]+)"/g)) nodes[id] = id === 'nickname' ? new Input(id) : new Element(id);
  nodes.result.hidden = nodes['play-hud'].hidden = true;
  const net = {
    stairsToday: async () => { calls.push('today'); return { status: 200, body: { day: new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date()), seed: 123 } }; },
    stairsRanking: async () => { calls.push('ranking'); return { status: 200, body: { top: [{ rank: 7, nickname: '히유', character: 'angry', height: 4, timeMs: 300 }] } }; },
    stairsRun: body => { calls.push(structuredClone(body)); return reply(body); },
  };
  vm.runInNewContext(source, {
    document, location: { hostname: 'localhost' }, performance: { now: () => now }, innerWidth: 375,
    HTMLInputElement: Input, HTMLTextAreaElement: class extends Element {}, Intl, Date, StairsGame, cap,
    createNet: () => net, apiForHostname: () => '/', FACE: id => id, TINT: {}, medal: () => '',
    createScene: async () => ({ reset() {}, hop() {}, draw(game) { drawnGame = game; } }),
    localStorage: { getItem: key => { assert.equal(key, 'pheeeew-stairs-character'); return null; }, setItem: (k, v) => writes.push([k, v]) },
    addEventListener: (name, fn) => events[name] = fn, requestAnimationFrame: fn => frame = fn,
  });
  await flush();
  const key = (code, extra = {}) => events.keydown({ code, target: document.activeElement ?? nodes.result, preventDefault() {}, ...extra });
  const result = async () => {
    await nodes.retry.onclick();
    now += 1000;
    key(direction(123, 0) === 'L' ? 'ArrowRight' : 'ArrowLeft');
    now += 400; frame(now); await flush();
    assert.equal(nodes.result.hidden, false);
  };
  return { nodes, calls, writes, document, key, result, events, setReply: fn => reply = fn,
    advance: ms => { now += ms; frame(now); }, get game() { return drawnGame; } };
}
const gaugeTab = await tab();
await gaugeTab.nodes.start.onclick();
gaugeTab.advance(0);
assert.equal(gaugeTab.nodes['gauge-fill'].style.width, '100%');
for (let i = 0; i < 300; i++) {
  if (i) gaugeTab.advance(45);
  gaugeTab.key(direction(123, i) === 'L' ? 'ArrowLeft' : 'ArrowRight');
}
gaugeTab.advance(0);
assert.equal(gaugeTab.game.height, 300);
assert.equal(gaugeTab.nodes['gauge-fill'].style.width, '100%');
gaugeTab.advance(500);
assert.equal(gaugeTab.nodes['gauge-fill'].style.width, '75%');
assert.equal(gaugeTab.nodes['gauge-fill']['aria-valuenow'], '75');
gaugeTab.game.gauge = cap(300) * 2;
gaugeTab.advance(0);
assert.equal(gaugeTab.nodes['gauge-fill'].style.width, '100%', '상한 초과 표시를 100%로 자른다');
assert.equal(gaugeTab.nodes['gauge-fill']['aria-valuenow'], '100');
gaugeTab.game.gauge = 0;
gaugeTab.advance(0);
assert.equal(gaugeTab.nodes['gauge-fill'].style.width, '0%');
const t = await tab();
assert.equal(t.nodes.start.disabled, false);
assert.deepEqual(t.calls, ['today', 'ranking']);
await t.result();
t.nodes.nickname.value = '건너뜀'; t.nodes.nickname.input();
await t.result();
assert.equal(t.nodes.nickname.value, '건너뜀', '등록을 건너뛰어도 직전 입력을 탭에서 기억');
t.nodes.nickname.value = ''; t.nodes.nickname.input();
await t.result();
assert.equal(t.calls.filter(c => typeof c === 'object').length, 0, '끝나도 자동 등록하지 않음');
t.key('Enter'); assert.equal(t.document.activeElement, t.nodes.nickname);
t.nodes.nickname.value = '나!'; t.key('Enter');
assert.match(t.nodes['save-status'].textContent, /1~10/);
assert.equal(t.calls.filter(c => typeof c === 'object').length, 0);
t.nodes.nickname.value = ' 히유 ';
t.key('Space'); t.key('KeyA'); t.key('KeyD');
t.events.pointerdown({ pointerType: 'touch', clientX: 10, preventDefault() { throw Error('결과 화면 터치를 막음'); } });
assert.equal(t.nodes.result.hidden, false);
t.key('Enter', { isComposing: true });
assert.equal(t.calls.filter(c => typeof c === 'object').length, 0);
t.key('Enter'); await flush();
assert.equal(t.calls.at(-2).nickname, '히유');
assert.match(t.nodes['save-status'].textContent, /7위/);
assert.match(t.nodes['result-best'].textContent, /4칸/);
assert.equal(t.nodes.register.disabled, true);
t.key('Enter'); await flush();
assert.equal(t.calls.filter(c => typeof c === 'object').length, 1, '중복 등록 방지');
await t.result(); assert.equal(t.nodes.nickname.value, '히유');
assert.equal(t.nodes.nickname.disabled, false);
let resolveOld;
t.setReply(() => new Promise(resolve => resolveOld = resolve));
t.nodes.nickname.value = '다음'; t.key('Enter');
assert.equal(t.nodes.register.disabled, true);
t.nodes.nickname.blur(); t.key('Space'); await flush();
assert.equal(t.nodes['play-hud'].hidden, false, '전송 중에도 Space 재시작');
// 현재 판을 끝내 새 결과 화면으로 바꾼다.
t.key(direction(123, 0) === 'L' ? 'ArrowRight' : 'ArrowLeft');
// result()가 fall 상태에서 재시작하지 않고 fall 프레임을 진행한다.
await t.result();
assert.equal(t.nodes.nickname.value, '다음');
resolveOld({ status: 200, body: { rank: 99, best: { height: 99, timeMs: 0 } } }); await flush();
assert.equal(t.nodes['save-status'].textContent, '');
assert.equal(t.nodes.register.disabled, false);
t.setReply(async () => ({ status: 0 })); t.key('Enter'); await flush();
assert.match(t.nodes['save-status'].textContent, /연결하지 못/);
assert.equal(t.nodes.register.disabled, false);
t.setReply(async () => ({ status: 429, body: { message: '1분 뒤 다시 시도해 주세요.' } })); t.key('Enter'); await flush();
assert.match(t.nodes['save-status'].textContent, /1분/);
assert.deepEqual(t.writes, [], '닉네임·신원 저장 없음');
const fresh = await tab(); await fresh.result();
assert.equal(fresh.nodes.nickname.value, '', '새 탭은 닉네임을 기억하지 않음');
assert.doesNotMatch(source, /createGuest|net\.me\(|createNicknameDialog|sessionStorage|document\.cookie/);
console.log('stairs UI: PASS height-based gauge/clamping, no identity, opt-in registration, nickname validation, Enter/Space/IME/touch, retry, late response, tab memory');
