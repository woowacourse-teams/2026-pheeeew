// 화면 위에 얹는 UI(제목, 감정이 고르기, HUD, 구간 완료, 확인 창). 모양 규칙은 ui.css 에 있다.
// 1280×720 논리 화면 기준으로 만들고, 게임 화면과 같은 배율로 늘린다.

export const TINT = { defeated: '#9ed6f7', tired: '#cdbcf5', frustrated: '#f9c1a8', annoyed: '#f9b3cd', angry: '#f58476' };

const el = (tag, cls, html) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (html !== undefined) e.innerHTML = html;
  return e;
};
const face = (id, cls) => `<div class="${cls}"><img src="/assets/characters/${id}/front.png" alt=""></div>`;
const clock = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

export function createUI({ ids, info, zones, goal }) {
  const root = document.getElementById('ui');
  const loading = document.getElementById('loading');

  // ---- 제목 ----
  const title = el('div', 'screen veil');
  title.hidden = true;
  title.innerHTML = `
    <div class="logo">피유 등반</div>
    <div class="sub">길게 모아서, 한 번에 뛰어요</div>
    <div class="card resume" data-resume></div>
    <div class="row">
      <button class="btn" data-continue><span class="key">Enter</span>이어서 오르기</button>
      <button class="btn ghost" data-new><span class="key">N</span>처음부터</button>
    </div>`;

  // ---- 감정이 고르기 ----
  const select = el('div', 'screen veil');
  select.hidden = true;
  select.innerHTML = `
    <div class="logo" style="font-size:64px">누구와 오를까요?</div>
    <div class="heads">${ids.map((id) => `
      <div class="card pick" data-id="${id}" style="--tint:${TINT[id]}">
        ${face(id, 'face')}
        <div class="name">${info[id].name}</div>
        <div class="ability">${info[id].ability}</div>
      </div>`).join('')}</div>
    <div class="hint"><span class="key">◀</span><span class="key">▶</span>고르기 <span class="key">Space</span>시작 · 실력은 모두 같아요. 한 번 고르면 끝까지 함께 갑니다</div>`;
  const picks = [...select.querySelectorAll('.pick')];

  // ---- HUD ----
  const hud = el('div');
  hud.id = 'hud';
  hud.hidden = true;
  hud.innerHTML = `
    <div class="top">
      <div class="pill who"><span data-face></span><span data-name></span></div>
      <div class="pill"><small>높이</small><span data-h>0</span><small>칸</small></div>
      <div class="pill"><small>떨어짐</small><span data-falls>0</span></div>
      <div class="pill"><small>시간</small><span data-time>0:00</span></div>
    </div>
    <div class="pill zone"><small data-zone-no></small><span data-zone></span></div>
    <div id="tower"><div class="fill"></div>${zones.slice(0, -1).map((z) => `<div class="tick" style="bottom:${(z.to / goal) * 100}%"></div>`).join('')}<div class="best"></div><div class="me"></div></div>
    <div class="keys"><span><span class="key">◀</span> <span class="key">▶</span> 걷기</span><span><span class="key">Space</span> 길게 눌러 모으고, 놓으면 점프</span><span><span class="key">N</span> 처음부터</span><span><span class="key">J</span> 움직임 비교</span><span><span class="key">M</span> 소리</span></div>`;
  const q = (sel) => hud.querySelector(sel);
  const parts = { face: q('[data-face]'), name: q('[data-name]'), h: q('[data-h]'), falls: q('[data-falls]'), time: q('[data-time]'), zoneNo: q('[data-zone-no]'), zone: q('[data-zone]'), fill: q('#tower .fill'), best: q('#tower .best'), me: q('#tower .me'), keys: q('.keys') };
  const shown = {}; // 바뀐 값만 다시 쓴다
  const put = (k, v) => { if (shown[k] !== v) { shown[k] = v; parts[k].textContent = v; } };

  // ---- 구간 완료 ----
  const banner = el('div', 'card');
  banner.id = 'banner';
  banner.hidden = true;

  // ---- 확인 창 ----
  const modal = el('div', 'screen veil');
  modal.hidden = true;
  modal.innerHTML = `<div class="card modal"><div class="big">처음부터 다시 오를까요?</div><div class="small">지금까지의 높이와 기록이 지워집니다.</div>
    <div class="row" style="justify-content:center"><button class="btn" data-yes><span class="key">Enter</span>다시 시작</button><button class="btn ghost" data-no><span class="key">Esc</span>계속 오르기</button></div></div>`;

  root.append(hud, banner, select, title, modal);

  const api = {
    // 게임 화면과 같은 자리·배율로 맞춘다
    fit(x, y, s) { root.style.transform = `translate(${x}px, ${y}px) scale(${s})`; },
    loaded() { loading.hidden = true; },
    title(saved, onContinue, onNew) {
      title.style.setProperty('--accent', TINT[saved.id]);
      title.querySelector('[data-resume]').innerHTML = `${face(saved.id, 'face')}<div><div class="who">${info[saved.id].name}</div><div class="meta">높이 ${Math.max(0, Math.round(saved.h))}칸 · 떨어짐 ${saved.falls} · ${clock(saved.time)}</div></div>`;
      title.querySelector('[data-continue]').onclick = onContinue;
      title.querySelector('[data-new]').onclick = onNew;
      title.hidden = false;
      select.hidden = hud.hidden = true;
    },
    select(index, onPick, onStart) {
      picks.forEach((p, i) => {
        p.classList.toggle('on', i === index);
        p.onclick = () => (i === index ? onStart() : onPick(i));
      });
      select.hidden = false;
      title.hidden = hud.hidden = true;
    },
    play(id) {
      root.style.setProperty('--tint', TINT[id]);
      parts.face.innerHTML = face(id, 'avatar');
      put('name', info[id].name);
      title.hidden = select.hidden = modal.hidden = true;
      hud.hidden = false;
      parts.keys.style.opacity = 1;
      clearTimeout(api.keysTimer);
      api.keysTimer = setTimeout(() => { parts.keys.style.opacity = 0; }, 12000); // 조작 안내는 잠깐만
    },
    hud({ h, best, falls, time }) {
      put('h', String(h));
      put('falls', String(falls));
      put('time', clock(time));
      const zi = Math.max(0, zones.findIndex((z, i) => h < z.to || i === zones.length - 1));
      put('zoneNo', `${zi + 1}구간`);
      put('zone', zones[zi].name);
      const pct = Math.max(0, Math.min(100, (h / goal) * 100));
      parts.fill.style.height = `${pct}%`;
      parts.me.style.top = `${100 - pct}%`;
      parts.best.style.bottom = `${Math.max(0, Math.min(100, (best / goal) * 100))}%`;
    },
    banner(big, small) {
      banner.innerHTML = `<div class="big">${big}</div><div class="small">${small}</div>`;
      banner.hidden = true;
      void banner.offsetWidth; // 등장 효과를 다시 튼다
      banner.hidden = false;
      clearTimeout(api.bannerTimer);
      api.bannerTimer = setTimeout(() => { banner.hidden = true; }, 5000);
    },
    confirm(onYes, onNo) {
      modal.querySelector('[data-yes]').onclick = onYes;
      modal.querySelector('[data-no]').onclick = onNo;
      modal.hidden = false;
    },
    closeConfirm() { modal.hidden = true; },
  };
  return api;
}
