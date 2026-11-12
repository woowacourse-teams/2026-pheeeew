// 화면 위에 얹는 UI(제목, 감정이 고르기, HUD, 구간 완료, 확인 창, 닉네임 창, 순위). 모양 규칙은 ui.css 에 있다.
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
// 닉네임은 다른 사람이 정한 글자라 HTML 로 넣기 전에 바꾼다
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
// 순위 한 줄: 순위, 감정이 얼굴, 닉네임#꼬리표, 최고 높이(완주면 시간)
const row = (it, mine) => `<div class="r${it.tag === mine ? ' me' : ''}"><b>${it.rank}</b>${TINT[it.character] ? face(it.character, 'avatar mini') : '<div class="avatar mini"></div>'}<span class="n">${esc(it.nickname)}<small>#${esc(it.tag)}</small></span><span class="v">${it.finished ? `완주${it.time != null ? ` ${clock(it.time)}` : ''}` : `${it.best}칸`}</span></div>`;
const STATUS = { connecting: '연결하는 중…', offline: '연결 끊김 · 혼자 오르는 중', replaced: '다른 창에서 오르는 중' };

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
      <button class="btn" data-continue><span class="key">Enter</span><span data-continue-label>이어서 오르기</span></button>
      <button class="btn ghost" data-new><span class="key">N</span>처음부터</button>
    </div>
    <div class="boards" data-boards hidden>${['오늘', '역대'].map((n) => `<div class="card board"><div class="head">${n} 순위</div><div class="list"></div></div>`).join('')}</div>
    <div class="guest" data-guest hidden><span data-guest-name></span><button class="btn ghost small" data-rename>닉네임 바꾸기</button></div>`;

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
    <div id="ranks" class="card" hidden><div class="head"><span data-ranks-head></span><span class="key">Tab</span></div><div class="list" data-ranks-list></div></div>
    <div id="toast" class="pill" hidden></div>
    <div id="tower"><div class="fill"></div>${zones.slice(0, -1).map((z) => `<div class="tick" style="bottom:${(z.to / goal) * 100}%"></div>`).join('')}<div class="others"></div><div class="best"></div><div class="me"></div></div>
    <div class="keys"><span><span class="key">◀</span> <span class="key">▶</span> 걷기</span><span><span class="key">Space</span> 길게 눌러 모으고, 놓으면 점프</span><span><span class="key">N</span> 처음부터</span><span><span class="key">J</span> 움직임 비교</span><span><span class="key">G</span> 다른 사람</span><span><span class="key">M</span> 소리</span></div>`;
  const q = (sel) => hud.querySelector(sel);
  const parts = { face: q('[data-face]'), name: q('[data-name]'), h: q('[data-h]'), falls: q('[data-falls]'), time: q('[data-time]'), zoneNo: q('[data-zone-no]'), zone: q('[data-zone]'), fill: q('#tower .fill'), best: q('#tower .best'), me: q('#tower .me'), others: q('#tower .others'), keys: q('.keys'), ranks: q('#ranks'), ranksHead: q('[data-ranks-head]'), ranksList: q('[data-ranks-list]'), toast: q('#toast') };
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

  // ---- 닉네임 창 ----
  const nick = el('div', 'screen veil');
  nick.hidden = true;
  nick.innerHTML = `<div class="card modal"><div class="big" data-title></div><div class="small">순위표에 나오는 이름이에요. 2~10자, 한글·영문·숫자</div>
    <input class="nick" data-input maxlength="10" autocomplete="off" spellcheck="false"><div class="err" data-err></div>
    <div class="row" style="justify-content:center"><button class="btn" data-ok><span class="key">Enter</span><span data-ok-label></span></button><button class="btn ghost" data-alt><span class="key">Esc</span><span data-alt-label></span></button></div></div>`;
  const nickInput = nick.querySelector('[data-input]');

  root.append(hud, banner, select, title, modal, nick);

  const api = {
    // 게임 화면과 같은 자리·배율로 맞춘다
    fit(x, y, s) { root.style.transform = `translate(${x}px, ${y}px) scale(${s})`; },
    loaded() { loading.hidden = true; },
    // saved 가 없으면(첫 등반) 이어하기 카드 없이 "오르기 시작"만
    title(saved, onContinue, onNew) {
      const resume = title.querySelector('[data-resume]');
      resume.hidden = title.querySelector('[data-new]').hidden = !saved;
      title.querySelector('[data-continue-label]').textContent = saved ? '이어서 오르기' : '오르기 시작';
      if (saved) {
        title.style.setProperty('--accent', TINT[saved.id]);
        resume.innerHTML = `${face(saved.id, 'face')}<div><div class="who">${info[saved.id].name}</div><div class="meta">높이 ${Math.max(0, Math.round(saved.h))}칸 · 떨어짐 ${saved.falls} · ${clock(saved.time)}</div></div>`;
      } else title.style.removeProperty('--accent');
      title.querySelector('[data-continue]').onclick = onContinue;
      title.querySelector('[data-new]').onclick = onNew;
      title.hidden = false;
      select.hidden = hud.hidden = nick.hidden = true;
    },
    select(index, onPick, onStart) {
      picks.forEach((p, i) => {
        p.classList.toggle('on', i === index);
        p.onclick = () => (i === index ? onStart() : onPick(i));
      });
      select.hidden = false;
      title.hidden = hud.hidden = nick.hidden = true;
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
    // 제목 화면: 내 닉네임(없으면 숨김)과 오늘·역대 순위(못 불러오면 숨김)
    guest(g, onRename) {
      title.querySelector('[data-guest]').hidden = !g;
      if (!g) return;
      title.querySelector('[data-guest-name]').innerHTML = `${esc(g.nickname)}<small>#${esc(g.tag)}</small>`;
      title.querySelector('[data-rename]').onclick = onRename;
    },
    boards(today, all, mine) {
      const box = title.querySelector('[data-boards]');
      box.hidden = !today && !all;
      [today, all].forEach((top, i) => {
        box.children[i].querySelector('.list').innerHTML = !top ? '<div class="none">불러오지 못했어요</div>' : top.length ? top.slice(0, 5).map((it) => row(it, mine)).join('') : '<div class="none">아직 아무도 없어요</div>';
      });
    },
    // 닉네임 창. onOk(이름), onAlt() 는 main 이 정한다
    nickname({ heading, value = '', ok, alt }, onOk, onAlt) {
      nick.querySelector('[data-title]').textContent = heading;
      nick.querySelector('[data-ok-label]').textContent = ok;
      nick.querySelector('[data-alt-label]').textContent = alt;
      nick.querySelector('[data-err]').textContent = '';
      nickInput.value = value;
      const submit = () => onOk(nickInput.value.trim());
      nick.querySelector('[data-ok]').onclick = submit;
      nick.querySelector('[data-alt]').onclick = onAlt;
      nickInput.onkeydown = (e) => {
        e.stopPropagation(); // 게임 조작 키(M 소리, N 처음부터 등)로 새지 않게
        if (e.isComposing) return; // 한글 조합 중의 Enter 는 글자 확정
        if (e.key === 'Enter') submit();
        if (e.key === 'Escape') onAlt();
      };
      nick.hidden = false;
      title.hidden = select.hidden = hud.hidden = true;
      nickInput.focus();
      nickInput.select(); // 바꾸기: 지금 이름을 통째로 덮어쓰게
    },
    nicknameError(text) { nick.querySelector('[data-err]').textContent = text; nickInput.focus(); },
    closeNickname() { nick.hidden = true; nickInput.blur(); },
    // 게임 중 순위 패널과 탑 진행 막대의 점. state 가 없으면(혼자 하기) 숨긴다
    ranks(state) {
      parts.ranks.hidden = !state;
      if (!state) { parts.others.innerHTML = ''; return; }
      const { rank, status, tag } = state;
      const on = status === 'online' && rank;
      parts.ranksHead.innerHTML = on ? `${rank.me ? `<b>${rank.me.rank}위</b> · ` : ''}접속 ${rank.online}명` : STATUS[status] ?? STATUS.connecting;
      let list = '';
      if (on) {
        const top = rank.top.slice(0, 5);
        list = top.map((it) => row(it, tag)).join('');
        const mineRow = rank.me && rank.me.rank > top.length && (rank.near ?? []).find((it) => it.tag === tag);
        if (mineRow) list += `<div class="gap"></div>${row(mineRow, tag)}`;
      }
      parts.ranksList.innerHTML = list;
      parts.others.innerHTML = on ? rank.top.filter((it) => it.tag !== tag).slice(0, 5).map((it) => `<i style="bottom:${Math.max(0, Math.min(100, (it.best / goal) * 100))}%;--c:${TINT[it.character] ?? '#fff8ea'}"></i>`).join('') : '';
    },
    foldRanks() { parts.ranks.classList.toggle('folded'); },
    // 다른 사람의 구간 통과·완주: 화면 위에 잠깐 한 줄
    // 관리자 모드(?admin): 오른쪽 아래 정보 표시와 키 안내, V 전체 지도. onPick(지도 위 x, y) 는 main 이 정한다
    adminPanel(mini, onPick) {
      const info = el('div', 'card', '<b>관리자 모드</b><span data-admin-info></span><div class="keys"><span><span class="key">클릭</span> 그 자리로</span><span><span class="key">PgUp</span><span class="key">PgDn</span> 한 화면</span><span><span class="key">1</span>~<span class="key">6</span> 구간</span><span><span class="key">⌫</span> 되돌리기</span><span><span class="key">V</span> 지도</span></div>');
      info.id = 'admin';
      hud.append(info);
      api.adminText = info.querySelector('[data-admin-info]');
      const map = el('div', 'screen veil', `<div class="card adminmap"><div class="head">전체 지도 · 누르면 그 자리로 <span class="key">V</span></div><canvas width="${mini.w}" height="${mini.h}"></canvas></div>`);
      map.hidden = true;
      root.append(map);
      const canvas = map.querySelector('canvas');
      canvas.onpointerdown = (e) => {
        const r = canvas.getBoundingClientRect(); // 화면 배율이 걸려 있어 그 비율로 되돌린다
        onPick(((e.clientX - r.left) / r.width) * mini.w, ((e.clientY - r.top) / r.height) * mini.h);
      };
      api.adminMapEl = map;
      return map;
    },
    // draw(ctx) 를 주면 지도를 그려 열고, null 이면 닫는다
    adminMap(draw) {
      const map = api.adminMapEl;
      if (draw) draw(map.querySelector('canvas').getContext('2d'));
      map.hidden = !draw;
    },
    adminInfo(text) { if (api.adminText.textContent !== text) api.adminText.textContent = text; },
    toast(text) {
      parts.toast.textContent = text;
      parts.toast.hidden = true;
      void parts.toast.offsetWidth;
      parts.toast.hidden = false;
      clearTimeout(api.toastTimer);
      api.toastTimer = setTimeout(() => { parts.toast.hidden = true; }, 4000);
    },
  };
  return api;
}
