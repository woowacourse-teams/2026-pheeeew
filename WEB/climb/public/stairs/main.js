import { createNet } from '../net.js';
import { FACE, TINT } from '../ui.js';
import { apiForHostname } from '../api-config.js';
import { cap } from './rules.js';
import { StairsGame } from './game.js';
import { createScene } from './scene.js';
import { rankingRows } from './ranking.js';

const ids = ['defeated', 'tired', 'frustrated', 'annoyed', 'angry'];
const names = ['좌절이', '지침이', '답답이', '짜증이', '분노'];
const $ = (id) => document.getElementById(id);
const seconds = (ms) => `${(ms / 1000).toFixed(3)}초`;
const net = createNet({ api: apiForHostname(location.hostname) });
let character = ids[0], today, scene, game, mode = 'loading', board = { top: [] };
let firstGuide = true, endedAt = 0, latestRun = null, refreshing = false;
let lastNickname = '', tabBest = null;
let registered = null, rankingRequest = 0;
const rankingDialog = $('full-ranking');
try {
  const preferred = localStorage.getItem('pheeeew-stairs-character');
  if (ids.includes(preferred)) character = preferred;
} catch { /* 저장 차단 브라우저에서도 플레이 가능 */ }
function storage(key, value) { try { localStorage.setItem(key, value); } catch {} }
function select(id) {
  character = id; storage('pheeeew-stairs-character', id);
  document.querySelectorAll('.character').forEach(el => el.setAttribute('aria-pressed', String(el.dataset.id === id)));
}
$('characters').innerHTML = ids.map((id, i) => `<button class="character" data-id="${id}" style="--tint:${TINT[id]}" aria-pressed="${id === character}" aria-label="${names[i]}"><img src="${FACE(id)}" alt=""><span>${names[i]}</span></button>`).join('');
$('characters').addEventListener('click', e => { const button = e.target.closest('[data-id]'); if (button) select(button.dataset.id); });

function renderBoard() {
  const markup = rankingRows(board.top.slice(0, 10));
  $('lobby-ranking').innerHTML = $('result-ranking').innerHTML = markup;
  $('lobby-best').textContent = tabBest ? `이 탭 최고 ${tabBest.height}칸 · ${seconds(tabBest.timeMs)}` : '이 탭 최고 · 아직 기록이 없어요';
}
async function openRanking() {
  if (!['lobby', 'result'].includes(mode) || !today?.day || rankingDialog.open) return;
  const request = ++rankingRequest, day = today.day;
  $('full-ranking-list').innerHTML = '';
  $('full-ranking-list').scrollTop = 0;
  $('ranking-count').textContent = '';
  $('ranking-status').textContent = '순위를 불러오는 중…';
  $('full-ranking-list').setAttribute('aria-busy', 'true');
  rankingDialog.showModal();
  $('ranking-close').focus();
  try {
    const response = await net.stairsRanking(day);
    if (request !== rankingRequest || !rankingDialog.open) return;
    if (response.status !== 200 || !Array.isArray(response.body?.top)) throw new Error('ranking');
    const { top, total = top.length } = response.body;
    $('ranking-count').textContent = `오늘 ${total}명 도전`;
    $('ranking-status').textContent = total > top.length ? `상위 ${top.length}명까지 표시해요.` : '';
    $('full-ranking-list').innerHTML = rankingRows(top, registered?.day === day ? registered.nickname : '');
    $('full-ranking-list').querySelector('[data-mine]')?.scrollIntoView({ block: 'center' });
  } catch {
    if (request === rankingRequest && rankingDialog.open) $('ranking-status').textContent = '순위를 불러오지 못했어요. 닫고 다시 열어 주세요.';
  } finally {
    if (request === rankingRequest) $('full-ranking-list').setAttribute('aria-busy', 'false');
  }
}
function closeRanking() {
  ++rankingRequest; // 닫기 전에 시작한 요청은 다음 창에 반영하지 않는다.
  rankingDialog.close();
}
$('lobby-ranking-open').onclick = $('result-ranking-open').onclick = openRanking;
$('lobby-ranking-card').onclick = $('result-ranking-card').onclick = openRanking;
$('ranking-close').onclick = closeRanking;
rankingDialog.addEventListener('click', e => { if (e.target === rankingDialog) closeRanking(); });
rankingDialog.addEventListener('cancel', e => { e.preventDefault(); closeRanking(); });
async function refreshBoard(day = today?.day) {
  if (!day) return;
  const result = await net.stairsRanking(day);
  if (result.status === 200 && today?.day === day) { board = result.body; renderBoard(); }
  else if (result.status !== 200) $('lobby-status').textContent = '순위를 불러오지 못했어요. 시작할 때 다시 확인해요.';
}
function showLobby() {
  mode = 'lobby'; $('lobby').hidden = false; $('result').hidden = $('play-hud').hidden = true;
  $('start').disabled = false; $('start').textContent = '오르기 시작 ↗';
  renderBoard(); refreshBoard();
}
// 판마다 서버가 새 무작위 계단(seed)과 표(ticket)를 준다. 다음 판 것은 미리 받아 두어 다시 하기가 바로 된다.
const fetchRun = () => net.stairsToday()
  .then(r => (r.status === 200 && Number.isInteger(r.body?.seed) && r.body?.ticket ? { ...r.body, at: Date.now() } : null))
  .catch(() => null);
let nextRun = null, currentRun = null;
async function start() {
  if (rankingDialog.open || !['lobby', 'result'].includes(mode) || refreshing) return;
  refreshing = true;
  let run = await (nextRun ?? fetchRun()); nextRun = null;
  if (!run || Date.now() - run.at > 10 * 60 * 1000) run = await fetchRun(); // 오래 기다린 표는 서버가 받지 않는다
  refreshing = false;
  if (!run) { $('lobby-status').textContent = $('save-status').textContent = '새 계단을 불러오지 못했어요. 다시 눌러 주세요.'; return; }
  if (run.day !== today.day) { today = run; board = { top: [] }; $('day').textContent = today.day; }
  currentRun = run; nextRun = fetchRun();
  document.activeElement?.blur();
  game = new StairsGame(run.seed); scene.reset(); mode = 'play';
  $('lobby').hidden = $('result').hidden = true; $('play-hud').hidden = false;
  $('ready-label').hidden = false; $('touch-guide').hidden = !firstGuide;
  firstGuide = false;
}
function input(d) {
  if (rankingDialog.open || mode !== 'play') return;
  const now = performance.now();
  if (game.input(d, now)) scene.hop(now);
  $('ready-label').hidden = true;
  if (game.height >= 5 || game.ended) $('touch-guide').hidden = true;
  if (game.ended) finish(now);
}
function saveStatus() {
  const run = latestRun;
  $('register').disabled = !run || run.sending || Boolean(run.saved);
  $('nickname').disabled = Boolean(run?.sending || run?.saved);
  $('register').textContent = run?.saved ? '등록 완료' : run?.sending ? '등록 중…' : '기록 등록';
  if (run?.saved) {
    $('save-status').textContent = `등록 완료 · ${run.body.nickname} ${run.saved.rank}위`;
    $('result-best').textContent = `이 닉네임 오늘 최고 ${run.saved.best.height}칸 · ${seconds(run.saved.best.timeMs)}`;
  } else $('save-status').textContent = run?.sending ? '등록 중… 바로 다시 해도 돼요.' : run?.error ?? '';
}
async function register() {
  if (rankingDialog.open || mode !== 'result' || !latestRun || latestRun.sending || latestRun.saved) return;
  const nickname = $('nickname').value.trim();
  if (!/^[가-힣a-zA-Z0-9]{1,10}$/.test(nickname)) {
    latestRun.error = nickname ? '한글, 영문, 숫자 1~10자로 입력해 주세요.' : '닉네임을 입력해 주세요.';
    saveStatus(); $('nickname').focus(); return;
  }
  lastNickname = nickname;
  const run = latestRun;
  run.body.nickname = nickname; run.sending = true; saveStatus();
  const response = await net.stairsRun(run.body); run.sending = false;
  if (response.status === 200) {
    run.saved = response.body;
    registered = { day: run.body.day, nickname: run.body.nickname };
    await refreshBoard(run.body.day);
  } else run.error = response.body?.message ?? '연결하지 못했어요. 기록 등록을 다시 눌러 주세요.';
  // 이전 판의 늦은 응답이 새 결과 화면의 입력이나 상태를 바꾸지 않는다.
  if (latestRun === run && mode === 'result') saveStatus();
}
function finish(now) {
  if (mode !== 'play') return;
  mode = 'fall'; endedAt = now;
  $('touch-guide').hidden = true;
  $('end-reason').textContent = game.reason;
  $('result-height').textContent = game.height;
  $('result-time').textContent = `첫 입력 → 마지막 성공 ${seconds(game.timeMs)}`;
  latestRun = { body: { day: currentRun.day, seed: currentRun.seed, ticket: currentRun.ticket, character, inputs: game.inputs.map(it => ({ ...it })) }, sending: false };
  if (!tabBest || game.height > tabBest.height || (game.height === tabBest.height && game.timeMs < tabBest.timeMs)) {
    tabBest = { height: game.height, timeMs: game.timeMs };
  }
  $('nickname').value = lastNickname;
  $('result-best').textContent = '';
  renderBoard(); saveStatus();
}
$('start').onclick = () => mode === 'error' ? initialize() : start();
$('retry').onclick = start;
$('home').onclick = showLobby;
$('nickname').addEventListener('input', () => { lastNickname = $('nickname').value; });
$('registration').onsubmit = e => { e.preventDefault(); register(); };
addEventListener('keydown', e => {
  if (rankingDialog.open) return; // dialog의 스크롤·닫기·Tab만 브라우저가 처리한다.
  if (e.target instanceof HTMLButtonElement && e.target.id.endsWith('-ranking-open') && ['Space', 'Enter'].includes(e.code)) return;
  if (e.isComposing || e.keyCode === 229 || e.repeat) return;
  if (mode === 'result' && e.code === 'Enter') { e.preventDefault(); register(); return; }
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement || e.target?.isContentEditable) return;
  if (['ArrowLeft', 'ArrowRight', 'KeyA', 'KeyD', 'Space', 'Enter'].includes(e.code)) e.preventDefault();
  if (['Space', 'Enter'].includes(e.code)) { start(); return; }
  if (['ArrowLeft', 'KeyA'].includes(e.code)) input('L');
  if (['ArrowRight', 'KeyD'].includes(e.code)) input('R');
});
addEventListener('pointerdown', e => {
  if (rankingDialog.open) return;
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
      const percent = Math.max(0, Math.min(100, game.gaugeAt(now) / cap(game.height) * 100));
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
    const [day, art] = await Promise.all([net.stairsToday(), scene ?? createScene($('stairs-canvas'), ids)]);
    if (day.status !== 200 || !Number.isInteger(day.body?.seed)) throw new Error('오늘의 계단을 불러오지 못했어요. 서버 연결을 확인해 주세요.');
    today = day.body; scene = art; nextRun = Promise.resolve({ ...day.body, at: Date.now() });
    $('day').textContent = today.day; showLobby();
  } catch (error) {
    mode = 'error'; $('start').disabled = false; $('start').textContent = '다시 연결';
    $('lobby-status').textContent = error.message ?? '그림을 불러오지 못했어요. 다시 연결해 주세요.';
  }
}
requestAnimationFrame(frame); initialize();
