import { createNet } from '../net.js';
import { createNicknameDialog, FACE, TINT, medal } from '../ui.js';
import { apiForHostname } from '../api-config.js';
import { GAUGE_MAX } from './rules.js';
import { StairsGame } from './game.js';
import { createScene } from './scene.js';

const ids = ['defeated', 'tired', 'frustrated', 'annoyed', 'angry'];
const names = ['좌절이', '지침이', '답답이', '짜증이', '분노'];
const $ = (id) => document.getElementById(id);
const escape = (value) => String(value ?? '').replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);
const seconds = (ms) => `${(ms / 1000).toFixed(3)}초`;
const net = createNet({ api: apiForHostname(location.hostname) });
const nick = createNicknameDialog(); $('nickname-root').append(nick.element);
let character = ids[0], today, guest, scene, game, mode = 'loading', board = { top: [], me: null };
let firstGuide = true, endedAt = 0, savingGuest = false, latestRun = null, refreshing = false;
const pending = new Set();
try {
  const saved = JSON.parse(localStorage.getItem('pheeeew-climb-v1') ?? 'null');
  const preferred = localStorage.getItem('pheeeew-stairs-character') ?? saved?.id;
  if (ids.includes(preferred)) character = preferred;
  firstGuide = localStorage.getItem('pheeeew-stairs-played') !== 'yes';
} catch { /* 저장 차단 브라우저에서도 플레이 가능 */ }
function storage(key, value) { try { localStorage.setItem(key, value); } catch {} }
function select(id) {
  character = id; storage('pheeeew-stairs-character', id);
  document.querySelectorAll('.character').forEach(el => el.setAttribute('aria-pressed', String(el.dataset.id === id)));
}
$('characters').innerHTML = ids.map((id, i) => `<button class="character" data-id="${id}" style="--tint:${TINT[id]}" aria-pressed="${id === character}" aria-label="${names[i]}"><img src="${FACE(id)}" alt=""><span>${names[i]}</span></button>`).join('');
$('characters').addEventListener('click', e => { const button = e.target.closest('[data-id]'); if (button) select(button.dataset.id); });

function renderBoard() {
  const markup = board.top.slice(0, 10).map(it => `<div class="r ${medal(it.rank) ? `podium ${medal(it.rank)}` : ''}"><b class="${medal(it.rank) ? 'medal' : ''}">${it.rank}</b><div class="avatar mini"><img src="${FACE(ids.includes(it.character) ? it.character : ids[0])}" alt=""></div><span class="n">${escape(it.nickname)}<small>#${escape(it.tag)}</small></span><span class="v">${it.height}칸<small>${seconds(it.timeMs)}</small></span></div>`).join('') || '<p class="none">첫 기록의 주인공이 되어 주세요!</p>';
  $('lobby-ranking').innerHTML = $('result-ranking').innerHTML = markup;
  const best = board.me ? `내 최고 ${board.me.height}칸 · ${seconds(board.me.timeMs)} · ${board.me.rank}위` : '내 최고 · 아직 기록이 없어요';
  $('lobby-best').textContent = $('result-best').textContent = best;
}
async function refreshBoard(day = today?.day) {
  if (!day) return;
  const result = await net.stairsRanking(day);
  if (result.status === 200 && today?.day === day) { board = result.body; renderBoard(); }
  else if (result.status !== 200) $('lobby-status').textContent = '순위를 불러오지 못했어요. 시작할 때 다시 확인해요.';
}
function showLobby() {
  mode = 'lobby'; $('lobby').hidden = false; $('result').hidden = $('play-hud').hidden = true;
  $('identity').textContent = guest ? `${guest.nickname} #${guest.tag}` : '처음 시작할 때 닉네임을 정해요';
  $('start').disabled = false; $('start').textContent = '오르기 시작 ↗';
  renderBoard(); refreshBoard();
}
function askNickname() {
  mode = 'nickname';
  nick.show({ heading: '닉네임을 정해 주세요', ok: '정하기', alt: '취소' }, async name => {
    if (savingGuest) return;
    savingGuest = true;
    const response = await net.createGuest(name);
    savingGuest = false;
    if (response.status !== 201) { nick.error(response.body?.message ?? '서버에 닿지 않아요. 다시 시도해 주세요.'); return; }
    guest = response.body; nick.close(); mode = 'lobby'; start();
  }, () => { nick.close(); showLobby(); });
}
async function start() {
  if (!['lobby', 'result'].includes(mode) || refreshing) return;
  if (!guest) { askNickname(); return; }
  // 자정을 넘긴 대기 화면은 서버에서 오늘의 시드를 새로 받는다.
  const localDay = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date());
  if (today.day !== localDay) {
    refreshing = true;
    const response = await net.stairsToday(); refreshing = false;
    if (response.status !== 200) { $('lobby-status').textContent = $('save-status').textContent = '오늘의 계단을 불러오지 못했어요. 다시 눌러 주세요.'; return; }
    today = response.body; board = { top: [], me: null }; $('day').textContent = today.day;
  }
  game = new StairsGame(today.seed); scene.reset(); mode = 'play';
  $('lobby').hidden = $('result').hidden = true; $('play-hud').hidden = false;
  $('ready-label').hidden = false; $('touch-guide').hidden = !firstGuide;
  storage('pheeeew-stairs-played', 'yes'); firstGuide = false;
}
function input(d) {
  if (mode !== 'play') return;
  const now = performance.now();
  if (game.input(d, now)) scene.hop(now);
  $('ready-label').hidden = true;
  if (game.height >= 5 || game.ended) $('touch-guide').hidden = true;
  if (game.ended) finish(now);
}
function saveStatus() {
  const failed = [...pending].filter(run => !run.sending);
  $('save-retry').hidden = !failed.length;
  if (latestRun?.saved) $('save-status').textContent = `기록 저장 완료 · 내 최고 ${latestRun.saved.best.height}칸 · ${latestRun.saved.rank}위${failed.length ? ` / 미전송 ${failed.length}건` : ''}`;
  else if (latestRun?.sending) $('save-status').textContent = '기록 저장 중… 바로 다시 시작해도 돼요.';
  else $('save-status').textContent = latestRun?.error ?? '';
}
async function sendRun(run) {
  if (run.sending) return;
  run.sending = true; saveStatus();
  const response = await net.stairsRun(run.body); run.sending = false;
  if (response.status === 200) {
    run.saved = response.body; pending.delete(run);
    if (today.day === run.body.day && (!board.me || response.body.best.height > board.me.height
      || (response.body.best.height === board.me.height && response.body.best.timeMs <= board.me.timeMs))) {
      board.me = { ...response.body.best, rank: response.body.rank }; renderBoard();
    }
    await refreshBoard(run.body.day);
  } else {
    run.error = response.body?.message ?? '저장하지 못했어요. 기록 다시 전송을 눌러 주세요.';
    if (response.status === 401) guest = null;
    if (response.status >= 400 && response.status < 500 && response.status !== 429) pending.delete(run);
  }
  saveStatus();
}
function finish(now) {
  if (mode !== 'play') return;
  mode = 'fall'; endedAt = now;
  $('touch-guide').hidden = true;
  $('end-reason').textContent = game.reason;
  $('result-height').textContent = game.height;
  $('result-time').textContent = `첫 입력 → 마지막 성공 ${seconds(game.timeMs)}`;
  latestRun = { body: { day: today.day, character, inputs: game.inputs.map(it => ({ ...it })) }, sending: false };
  pending.add(latestRun); sendRun(latestRun);
}
$('start').onclick = () => mode === 'error' ? initialize() : start();
$('retry').onclick = start;
$('home').onclick = showLobby;
$('save-retry').onclick = () => { for (const run of pending) if (!run.sending) sendRun(run); };
addEventListener('keydown', e => {
  if (e.target instanceof HTMLInputElement || e.isComposing || e.repeat) return;
  if (['ArrowLeft', 'ArrowRight', 'KeyA', 'KeyD', 'Space', 'Enter'].includes(e.code)) e.preventDefault();
  if (['Space', 'Enter'].includes(e.code)) { start(); return; }
  if (['ArrowLeft', 'KeyA'].includes(e.code)) input('L');
  if (['ArrowRight', 'KeyD'].includes(e.code)) input('R');
});
addEventListener('pointerdown', e => {
  if (mode !== 'play' || (e.pointerType === 'mouse' && e.button !== 0)) return;
  e.preventDefault(); input(e.clientX < innerWidth / 2 ? 'L' : 'R');
}, { passive: false });
addEventListener('contextmenu', e => { if (mode === 'play' || mode === 'fall') e.preventDefault(); });
// 숨겨진 탭도 시계가 흐른다. 복귀 때 tick이 만료를 처리한다.
addEventListener('visibilitychange', () => { if (!document.hidden && mode === 'play') { game.tick(performance.now()); if (game.ended) finish(performance.now()); } });
function frame(now) {
  if (scene && game) {
    if (mode === 'play') { game.tick(now); if (game.ended) finish(now); }
    if (mode === 'play' || mode === 'fall') {
      scene.draw(game, character, now, mode === 'fall' ? now - endedAt : 0);
      $('height').textContent = game.height;
      $('elapsed').textContent = seconds(game.startedAt === null ? 0 : Math.max(0, Math.floor(now - game.startedAt)));
      const percent = Math.max(0, Math.min(100, game.gaugeAt(now) / GAUGE_MAX * 100));
      $('gauge-fill').style.width = `${percent}%`; $('gauge-fill').style.background = percent < 25 ? '#f58476' : '#ffd35a';
      $('gauge-fill').parentElement.setAttribute('aria-valuenow', String(Math.round(percent)));
    }
    if (mode === 'fall' && now - endedAt >= 340) { mode = 'result'; $('play-hud').hidden = true; $('result').hidden = false; saveStatus(); }
  }
  requestAnimationFrame(frame);
}
async function initialize() {
  mode = 'loading'; $('start').disabled = true; $('start').textContent = '계단 준비 중…';
  $('lobby-status').textContent = '';
  try {
    const [day, me, art] = await Promise.all([net.stairsToday(), net.me(), scene ?? createScene($('stairs-canvas'), ids)]);
    if (day.status !== 200 || !Number.isInteger(day.body?.seed)) throw new Error('오늘의 계단을 불러오지 못했어요. 서버 연결을 확인해 주세요.');
    if (me.status !== 200 && me.status !== 401) throw new Error('닉네임을 확인하지 못했어요. 서버 연결을 확인해 주세요.');
    today = day.body; guest = me.status === 200 ? me.body : null; scene = art;
    // 기기에 저장된 선택이 없으면 기존 등반의 서버 선택도 이어 쓴다.
    try { if (!localStorage.getItem('pheeeew-stairs-character') && ids.includes(guest?.run?.character)) select(guest.run.character); } catch {}
    $('day').textContent = today.day; showLobby();
  } catch (error) {
    mode = 'error'; $('start').disabled = false; $('start').textContent = '다시 연결';
    $('lobby-status').textContent = error.message ?? '그림을 불러오지 못했어요. 다시 연결해 주세요.';
  }
}
requestAnimationFrame(frame); initialize();
