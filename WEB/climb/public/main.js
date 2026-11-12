import { Application, Assets, Container, Graphics, Sprite, Text, TilingSprite } from '/vendor/pixi.mjs';
import { TILE, COLS, DT, IDS, params, newPlayer, step } from './physics.js';
import { level, DECOR, HEIGHT, START, SCREEN, ZONES, GOAL, MAP_VERSION, nods, winds, updateNods, NOD_STAND, HOT_STAND, yOf, hOf } from './level.js';
import { Rig, PARTS, JUMPS } from './rig.js';
import { JellyRig } from './jelly.js';
import { traceTerrain, sketch } from './terrain.js';
import { createUI, TINT } from './ui.js';
import { createFx } from './fx.js';
import { boostLook, RISE_LIFE } from './boost.js';
import { sfx, unlock, toggleMute, isMuted, audioCtx } from './sfx.js';
import { createBgm, CREDITS } from './bgm.js';
import { createNet } from './net.js';
import { createGhosts } from './ghosts.js';
import * as admin from './admin.js';

const W = COLS * TILE; // 1280
const H = 720;
const DEBUG = new URLSearchParams(location.search).has('debug');
const ADMIN = new URLSearchParams(location.search).has('admin'); // 관리자 모드: 아무 곳으로나 옮겨 다니며 구간을 해 본다. 서버에는 아무것도 보내지 않는다
const SAVE_KEY = ADMIN ? 'pheeeew-climb-admin' : DEBUG ? 'pheeeew-climb-debug' : 'pheeeew-climb-v1'; // 시험 화면은 실제 저장을 건드리지 않는다

const INFO = {
  defeated: { name: '좌절이', ability: '쉽게 풀이 죽어요', fall: '역시 난 안 돼…' },
  tired: { name: '지침이', ability: '늘 졸리고 기운이 없어요', fall: '하… 또 올라가야 해?' },
  frustrated: { name: '답답이', ability: '속이 꽉 막혀 답답해요', fall: '아니 왜 안 되는데!' },
  annoyed: { name: '짜증이', ability: '툭하면 뾰로통해요', fall: '아, 짜증 나.' },
  angry: { name: '분노', ability: '금방 화르르 타올라요', fall: '으아아악!!' },
};
const FONT = 'Jua, "Apple SD Gothic Neo", sans-serif';
const ui = createUI({ ids: IDS, info: INFO, zones: ZONES, goal: ZONES.at(-1).to, credits: CREDITS });
const bgm = createBgm({ getCtx: audioCtx, muted: isMuted, zones: ZONES }); // 첫 키 입력·클릭(unlock) 뒤에 시작
await document.fonts.load('20px Jua'); // 캔버스 글자(말풍선)도 같은 글꼴로 그리려면 먼저 불러와야 한다
const INK = 0x2a2420;
const app = new Application();
await app.init({ resizeTo: window, background: 0x1c1a22, antialias: true, autoDensity: true, resolution: Math.min(2, window.devicePixelRatio || 1) });
document.body.appendChild(app.canvas);

const view = new Container(); // 1280×720 논리 화면
app.stage.addChild(view);
view.mask = view.addChild(new Graphics().rect(0, 0, W, H).fill(0xffffff));
function fit() {
  const s = Math.min(app.screen.width / W, app.screen.height / H);
  view.scale.set(s);
  view.position.set((app.screen.width - W * s) / 2, (app.screen.height - H * s) / 2);
  ui.fit(view.x, view.y, s);
}
app.renderer.on('resize', fit);
fit();

const tex = {};
for (const id of IDS) {
  tex[id] = {};
  for (const p of PARTS) tex[id][p] = await Assets.load(`/assets/characters/${id}/${p}.png`);
  // 점프 자세 그림은 있는 감정이만 쓴다
  if ((await fetch(`/assets/characters/${id}/jump_splat.png`, { method: 'HEAD' })).ok) for (const p of JUMPS) tex[id][p] = await Assets.load(`/assets/characters/${id}/${p}.png`);
}

// ---- 맵 ----
const world = new Container();
view.addChild(world);
// 구간 그림: 배경(천천히 따라 움직임), 지형 속 재질, 경사 재질, 윗면 띠. 그림이 없는 구간은 구간 1 그림을 쓴다.
const has = async (url) => (await fetch(url, { method: 'HEAD' })).ok;
const arts = [];
for (const z of ZONES) {
  // 새 그림 4장이 모두 있을 때만 새 폴더를 쓴다(그리는 도중이면 예전 그림)
  let themed = true;
  for (const k of ['bg', 'rock', 'fabric', 'trim']) themed = themed && (await has(`/assets/${z.art}/${k}.png`));
  const dir = themed ? z.art : z.old;
  const a = { dir, themed };
  for (const k of ['bg', 'rock', 'fabric', 'trim']) a[k] = await Assets.load(`/assets/${dir}/${k}.png`);
  for (const k of ['nod', 'hot', 'boost', 'flag']) a[k] = (await has(`/assets/${dir}/${k}.png`)) ? await Assets.load(`/assets/${dir}/${k}.png`) : null; // 발판 장치와 바람 깃발
  a.kit = { top: [], hang: [], side: [], far: [], in: [] }; // in = 지형 면 위에 찍는 얼룩 무늬
  for (const k of Object.keys(a.kit)) {
    for (let i = 1; await has(`/assets/${dir}/kit/${k}_${i}.png`); i++) a.kit[k].push(await Assets.load(`/assets/${dir}/kit/${k}_${i}.png`));
  }
  arts.push(a);
}
// 칸의 윗면 높이로 어느 구간인지 정한다
const zoneOf = (h) => Math.max(0, ZONES.findIndex((z, i) => h <= z.to || i === ZONES.length - 1));
// 구간을 시작할 때 배경 그림의 어느 부분이 보일지 맞춘다(bgAt). 없으면 그림 맨 위부터.
const bgShift = ZONES.map((z, i) => {
  if (z.bgAt === undefined) return 0;
  const cam0 = Math.min(HEIGHT * TILE - H, yOf(z.from) - H * 0.55);
  return cam0 * 0.35 - z.bgAt * arts[i].bg.height * (W / arts[i].bg.width);
});
const bgs = arts.map((a, i) => {
  const b = new TilingSprite({ texture: a.bg, width: W, height: H });
  b.tileScale.set(W / a.bg.width);
  view.addChildAt(b, i);
  return b;
});
for (let n = 1; DEBUG && n * SCREEN < HEIGHT; n++) { // 화면 경계 표시(시험 화면에서만)
  const y = yOf(n * SCREEN);
  world.addChild(new Graphics().rect(TILE, y, W - 2 * TILE, 2).fill({ color: INK, alpha: 0.08 }));
  const label = new Text({ text: `화면 ${n + 1}`, style: { fontFamily: FONT, fontSize: 18, fontWeight: '700', fill: INK } });
  label.alpha = 0.25;
  label.position.set(TILE + 12, y - 28);
  world.addChild(label);
}
// 같은 맵이면 늘 같은 자리에 놓이도록 씨앗이 있는 난수를 쓴다
function seeded(seed) {
  let a = seed >>> 0;
  return () => { a = (a + 0x6d2b79f5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const rnd = seeded(20261111);
const any = (list) => list[Math.floor(rnd() * list.length)];

// 지형 뒤 큰 배경물: 지형보다 느리게 움직여 멀리 있는 것처럼 보인다
const FAR = 0.6;
const far = new Container();
view.addChildAt(far, bgs.length);
ZONES.forEach((z, i) => {
  if (!arts[i].kit.far.length) return;
  for (let h = z.from + 6; h < z.to - 4; h += 15) {
    const sp = new Sprite(any(arts[i].kit.far));
    sp.anchor.set(0.5);
    sp.scale.set(0.55 + rnd() * 0.2);
    sp.alpha = z.farAlpha ?? 0.45; // 멀리 있는 것처럼 옅게(야경은 불빛이 보이게 진하게)
    sp.position.set(160 + rnd() * (W - 320), yOf(h) * FAR);
    far.addChild(sp);
  }
});

// ---- 지형 그리기 ----
// 충돌은 칸 그대로. 보이는 윤곽은 모서리를 굴리고 선을 살짝 흔들어 손그림처럼 만든다.
const LINE = 0x3a2e33; // 캐릭터의 검은 선보다 부드러운 어두운 색. 지형이 캐릭터 뒤로 물러나 보인다
const loops = traceTerrain(level, HEIGHT).map((l) => sketch(l.pts));

const shadow = new Graphics(); // 배경에 떨어지는 그림자
for (const flat of loops) shadow.poly(flat).fill({ color: 0x241c22, alpha: 0.13 });
shadow.position.set(8, 10);
world.addChild(shadow);

const shape = new Graphics();
for (const flat of loops) shape.poly(flat).fill(0xffffff);
const skins = new Container(); // 구간마다 그 높이 띠에만 재질을 깐다
ZONES.forEach((z, i) => {
  const top = i === ZONES.length - 1 ? 0 : yOf(z.to);
  const bottom = i === 0 ? HEIGHT * TILE : yOf(z.from);
  const skin = new TilingSprite({ texture: arts[i].rock, width: W, height: bottom - top });
  skin.tileScale.set(0.5);
  skin.y = top;
  skins.addChild(skin);
});
const rim = new Graphics(); // 가장자리에서 안으로 번지는 음영
for (const [width, alpha] of [[44, 0.07], [24, 0.08], [11, 0.1]]) for (const flat of loops) rim.poly(flat).stroke({ width, color: 0x241c22, alpha, join: 'round' });
skins.mask = shape;
world.addChild(shape, skins);

// 경사면: 미끄러운 면에만 천 띠(두께 CLOTH)를 덮는다
const CLOTH = 20;
ZONES.forEach((_, z) => {
  const cloth = new Graphics();
  for (let r = 0; r < HEIGHT; r++) for (let c = 0; c < COLS; c++) {
    if (zoneOf(HEIGHT - 1 - r) !== z) continue;
    const t = level.raw(c, r);
    const x = c * TILE;
    const y = r * TILE;
    // 면을 따라 아래로 CLOTH 만큼 내린 띠. 칸끼리 끊기지 않고, 지형 밖으로 나간 부분은 지형 모양에 잘린다
    if (t === 2) cloth.poly([x, y + TILE, x + TILE, y, x + TILE, y + CLOTH, x, y + TILE + CLOTH]);
    else if (t === 3) cloth.poly([x, y, x + TILE, y + TILE, x + TILE, y + TILE + CLOTH, x, y + CLOTH]);
  }
  cloth.fill(0xffffff);
  const skin = new TilingSprite({ texture: arts[z].fabric, width: W, height: HEIGHT * TILE });
  skin.tileScale.set(0.5);
  skin.mask = cloth;
  skins.addChild(cloth, skin);
});
{ // 지형 면 위에 찍는 무늬. 창문 구간은 줄 맞춰, 나머지는 가장자리 가까운 칸에 드문드문
  const solid = (c, r) => level.raw(c, r) === 1;
  for (let r = 1; r < HEIGHT - 1; r++) for (let c = 1; c < COLS - 1; c++) {
    const z = zoneOf(HEIGHT - 1 - r);
    const list = arts[z].kit.in;
    if (!list.length || !solid(c, r)) continue;
    const sp = new Sprite(any(list));
    sp.anchor.set(0.5);
    if (ZONES[z].windows && arts[z].themed) {
      // 두 칸마다 한 자리, 둘레 한 칸까지 모두 지형인 곳에만(벽 한가운데)
      let inside = c % 2 === 1 && r % 3 === 0;
      for (let dr = -1; dr <= 2 && inside; dr++) for (let dc = -1; dc <= 2; dc++) inside = inside && solid(c + dc, r + dr);
      if (!inside || rnd() > 0.7) continue;
      sp.scale.set(0.36);
      sp.position.set((c + 1) * TILE, (r + 1) * TILE);
    } else {
      const nearEdge = !solid(c, r - 1) || !solid(c, r + 1) || !solid(c - 1, r) || !solid(c + 1, r);
      if (rnd() > (nearEdge ? 0.16 : 0.05)) continue;
      sp.scale.set((0.45 + rnd() * 0.35) * (rnd() < 0.5 ? -1 : 1), 0.45 + rnd() * 0.35);
      sp.rotation = (rnd() - 0.5) * 0.5;
      sp.alpha = 0.85;
      sp.position.set((c + rnd()) * TILE, (r + rnd()) * TILE);
    }
    skins.addChild(sp);
  }
}
skins.addChild(rim); // 음영은 천과 얼룩 위에도 덮는다

const map = new Graphics(); // 선: 넓게 번진 선 위에 가는 본선
for (const flat of loops) map.poly(flat).stroke({ width: 10, color: LINE, alpha: 0.13, join: 'round' });
for (const flat of loops) map.poly(flat).stroke({ width: 3, color: LINE, alpha: 0.8, join: 'round' });
world.addChild(map);

// 윗면 덮개 띠(옥상 난간, 쌓인 눈): 위가 빈 지형 칸이 가로로 이어진 구간마다 하나
for (let r = 1; r < HEIGHT; r++) for (let c = 0; c < COLS; c++) {
  const z = zoneOf(HEIGHT - 1 - r);
  const top = (cc) => level.raw(cc, r) === 1 && level.raw(cc, r - 1) === 0;
  if (!ZONES[z].cap || !arts[z].themed || !top(c)) continue;
  let e = c;
  while (e + 1 < COLS && top(e + 1)) e++;
  const strip = new TilingSprite({ texture: arts[z].trim, width: (e - c + 1) * TILE + 8, height: 64 });
  strip.tileScale.set(0.5);
  strip.tilePosition.x = -c * TILE;
  strip.position.set(c * TILE - 4, r * TILE - 20); // 그림의 y=40 줄이 윗면
  world.addChild(strip);
  c = e;
}

// 가장자리 장식을 뿌린다. 반듯한 선을 덮어 깨고, 지형에서 자라난 것처럼 보이게 한다.
{
  const solid = (c, r) => level.raw(c, r) === 1;
  const empty = (c, r) => level.raw(c, r) === 0;
  const put = (texture, x, y, px, py, flip) => {
    const sp = new Sprite(texture);
    sp.pivot.set(px, py);
    sp.scale.set(flip ? -0.5 : 0.5, 0.5);
    sp.position.set(x, y);
    world.addChild(sp);
  };
  for (let r = 1; r < HEIGHT - 1; r++) for (let c = 1; c < COLS - 1; c++) {
    if (!solid(c, r)) continue;
    const kit = arts[zoneOf(HEIGHT - 1 - r)].kit;
    if (!kit.top.length) continue;
    const x = c * TILE;
    const y = r * TILE;
    // 윗면: 양옆도 윗면인 칸에만(끝에 걸치지 않게), 세 칸에 하나꼴
    if (empty(c, r - 1) && solid(c - 1, r) && empty(c - 1, r - 1) && solid(c + 1, r) && empty(c + 1, r - 1) && rnd() < 0.3) put(any(kit.top), x + TILE / 2 + (rnd() - 0.5) * 16, y, 128, 200, rnd() < 0.5);
    if (empty(c, r + 1) && solid(c - 1, r) && empty(c - 1, r + 1) && solid(c + 1, r) && empty(c + 1, r + 1) && rnd() < 0.3) put(any(kit.hang), x + TILE / 2 + (rnd() - 0.5) * 16, y + TILE, 128, 56, rnd() < 0.5);
    if (empty(c + 1, r) && solid(c, r - 1) && empty(c + 1, r - 1) && solid(c, r + 1) && empty(c + 1, r + 1) && rnd() < 0.16) put(any(kit.side), x + TILE, y + TILE / 2, 56, 128, false);
    if (empty(c - 1, r) && solid(c, r - 1) && empty(c - 1, r - 1) && solid(c, r + 1) && empty(c - 1, r + 1) && rnd() < 0.16) put(any(kit.side), x, y + TILE / 2, 56, 128, true);
  }
}
// 꾸벅 발판: 서 있으면 점점 처지다가 꺼진다
const nodViews = nods.map((n) => {
  const w = (n.c1 - n.c0 + 1) * TILE;
  const tex2 = arts[zoneOf(n.h)][n.kind];
  const v = new Container();
  if (n.kind === 'boost') { // 분사 판: 위로 번지는 빛 기둥. 밝기는 boostLook 이 정한다
    v.halo = new Graphics();
    for (let i = 0; i < 10; i++) v.halo.rect(-w / 2 + 3, -8 * (i + 1), w - 6, 8).fill({ color: 0x8ff5e0, alpha: 0.3 * (1 - i / 10) ** 1.6 }); // 위로 갈수록 옅어진다
    v.halo.blendMode = 'add';
    v.addChild(v.halo);
  }
  if (tex2) {
    const sp = new Sprite(tex2);
    sp.anchor.set(0.5, 20 / 160); // 그림(512×160)의 y=20 줄이 윗면. 분사 불꽃은 그 아래로 판 밑까지 내려온다
    sp.width = w + 12;
    sp.height = TILE * 1.25;
    v.addChild(sp);
    if (n.kind === 'boost') { // 같은 그림을 더해 그려 판 자체를 밝힌다
      v.shine = new Sprite(tex2);
      v.shine.anchor.copyFrom(sp.anchor);
      v.shine.width = sp.width;
      v.shine.height = sp.height;
      v.shine.blendMode = 'add';
      v.addChild(v.shine);
    }
  } else {
    v.addChild(new Graphics().roundRect(-w / 2, 0, w, TILE, 16).fill({ nod: 0xf3ecd8, hot: 0xff8a5c, boost: 0x7fd0ff }[n.kind]).stroke({ width: 3.5, color: INK })); // 그림이 오기 전의 임시 모양
  }
  v.position.set(n.c0 * TILE + w / 2, yOf(n.h));
  world.addChild(v);
  return v;
});

// 장식 소품: 충돌 없음. 보통은 바닥 중앙 (256,450), hang 은 천장에 매달려 위쪽 (256,60) 이 기준
for (const d of DECOR) {
  const sp = new Sprite(await Assets.load(`/assets/${arts[zoneOf(d.h)].dir}/${d.img}.png`));
  sp.pivot.set(256, d.hang ? 60 : 450);
  sp.scale.set(d.flip ? -d.s : d.s, d.s);
  sp.position.set(d.c * TILE, yOf(d.h) + (d.hang ? -4 : 5)); // 살짝 묻혀서 지형에 박힌 것처럼
  if (!d.hang) world.addChild(new Graphics().ellipse(d.c * TILE, yOf(d.h) + 2, (d.w ?? 130) * d.s, 7).fill({ color: 0x241c22, alpha: 0.18 }));
  world.addChild(sp);
}

// 물방울: 지형 밑면 여기저기에 맺혔다가 제각각 떨어진다
const drops = new Graphics();
world.addChild(drops);
const taps = []; // 물이 새는 자리
for (let r = 1; r < HEIGHT - 1; r++) for (let c = 1; c < COLS - 1; c++) {
  if (!ZONES[zoneOf(HEIGHT - 1 - r)].drips) continue;
  if (level.raw(c, r) === 1 && level.raw(c, r + 1) === 0 && rnd() < 0.09) taps.push({ x: (c + 0.2 + rnd() * 0.6) * TILE, y: (r + 1) * TILE, wait: rnd() * 5, every: 1.6 + rnd() * 4.5, y2: 0, vy: 0, grow: 0, splash: 0, sx: 0, sy: 0 });
}
function updateDrops(dt) {
  drops.clear();
  for (const d of taps) {
    if (Math.abs(d.y - (camY + H / 2)) > H * 1.3) continue; // 화면 근처만
    if (d.splash > 0) { // 닿아서 튄 자국
      d.splash -= dt;
      const k = 1 - d.splash / 0.28;
      drops.ellipse(d.sx, d.sy, 4 + k * 10, 1.5 + k * 2).stroke({ width: 1.6, color: 0xffffff, alpha: 0.7 * (1 - k) });
    }
    if (d.wait > 0) { d.wait -= dt; continue; }
    if (d.grow < 1) { // 맺히는 중
      d.grow = Math.min(1, d.grow + dt / 0.7);
      d.y2 = d.y + 2 + d.grow * 4;
      d.vy = 0;
    } else { // 떨어지는 중
      d.vy = Math.min(900, d.vy + 1500 * dt);
      d.y2 += d.vy * dt;
      if (level.tile(Math.floor(d.x / TILE), Math.floor(d.y2 / TILE)) !== 0 || d.y2 - d.y > 1400) {
        d.splash = 0.28;
        d.sx = d.x;
        d.sy = Math.floor(d.y2 / TILE) * TILE;
        d.grow = 0;
        d.wait = d.every * (0.6 + Math.random() * 0.8);
        continue;
      }
    }
    const rad = 1.5 + d.grow * 2.3;
    const stretch = Math.min(9, d.vy * 0.012); // 빨리 떨어질수록 길쭉하다
    drops.moveTo(d.x, d.y2 - rad * 2.2 - stretch).quadraticCurveTo(d.x + rad * 1.5, d.y2 + rad * 0.4, d.x, d.y2 + rad).quadraticCurveTo(d.x - rad * 1.5, d.y2 + rad * 0.4, d.x, d.y2 - rad * 2.2 - stretch)
      .fill({ color: 0xbfe2fb, alpha: 0.92 }).stroke({ width: 1.2, color: LINE, alpha: 0.55 });
  }
}

// 함께 오르기: 다른 사람의 감정이. 내 감정이보다 뒤에 반투명하게 그리고, 충돌 계산에는 넣지 않는다.
const ghostLayer = new Container();
world.addChild(ghostLayer);
const actor = new Container();
world.addChild(actor);
const fx = createFx(); // 먼지, 별, 반짝이 (캐릭터 위에 그린다)
world.addChild(fx.view);
const hitbox = new Graphics();
world.addChild(hitbox);
const gauge = new Graphics();
world.addChild(gauge);
// 떨어졌을 때 한마디: 말풍선
const quip = new Container();
const quipBox = new Graphics();
const quipText = new Text({ text: '', style: { fontFamily: FONT, fontSize: 20, fill: INK } });
quipText.anchor.set(0.5);
quip.addChild(quipBox, quipText);
quip.visible = false;
world.addChild(quip);
function say(text) {
  quipText.text = text;
  const w = quipText.width + 30;
  quipBox.clear()
    .roundRect(-w / 2, -21, w, 46, 16).fill(0x2b2433) // 아래로 떨어진 그림자
    .poly([-9, 22, 9, 22, 0, 36]).fill(0x2b2433)
    .roundRect(-w / 2, -25, w, 46, 16).fill(0xfff8ea).stroke({ width: 3, color: 0x2b2433 })
    .poly([-8, 19, 8, 19, 0, 30]).fill(0xfff8ea);
  quipText.y = -2;
}

// ---- HUD ----
// 눈: 눈 내리는 구간에서만 화면 전체에 내린다
const snow = new Graphics();
const flakes = Array.from({ length: 90 }, () => ({ x: Math.random() * W, y: Math.random() * H, r: 1.2 + Math.random() * 2.6, v: 30 + Math.random() * 60, ph: Math.random() * 6 }));
function updateSnow(dt, camH) {
  snow.clear();
  let amount = 0; // 카메라가 눈 구간 안에 얼마나 들어와 있는지
  for (const z of ZONES) if (z.snow) amount = Math.max(amount, Math.min(1, (camH - z.from + 6) / 12, (z.to + 6 - camH) / 12));
  if (amount <= 0) return;
  for (const f of flakes) {
    f.y += f.v * dt;
    f.ph += dt;
    f.x += Math.sin(f.ph) * 18 * dt;
    if (f.y > H + 5) { f.y = -5; f.x = Math.random() * W; }
    snow.circle(f.x, f.y, f.r).fill({ color: 0xffffff, alpha: 0.85 * amount });
  }
}
view.addChild(snow);

// 바람: 구역 안에 바람 줄무늬가 흐르고, 깃발이 방향을 알려 준다
const gusts = new Graphics();
world.addChild(gusts);
const streaks = winds.flatMap((w) => {
  const n = Math.max(6, Math.round(((w.c1 - w.c0 + 1) * (w.h1 - w.h0)) / 9));
  return Array.from({ length: n }, () => ({ w, x: Math.random(), y: Math.random(), len: 26 + Math.random() * 46, k: 0.6 + Math.random() * 0.8 }));
});
for (const w of winds) {
  const flag = arts[zoneOf(w.h0 + 1)].flag;
  if (!flag) continue;
  for (const cx of [w.c0 + 0.3, w.c1 + 0.7]) { // 구역 양 끝 아래에 깃발
    const sp = new Sprite(flag);
    sp.anchor.set(40 / 256, 240 / 256);
    sp.scale.set((w.ax < 0 ? -1 : 1) * 0.3, 0.3);
    sp.position.set(cx * TILE, yOf(w.h0));
    world.addChild(sp);
  }
}
function updateWind(dt) {
  gusts.clear();
  for (const s of streaks) {
    const { w } = s;
    const wide = (w.c1 - w.c0 + 1) * TILE;
    s.x += (w.ax / wide) * s.k * dt * 1.6;
    if (s.x > 1) { s.x -= 1; s.y = Math.random(); }
    if (s.x < 0) { s.x += 1; s.y = Math.random(); }
    const x = w.c0 * TILE + s.x * wide;
    const y = yOf(w.h1) + s.y * (w.h1 - w.h0) * TILE;
    if (y < camY - 40 || y > camY + H + 40) continue;
    const fade = Math.sin(Math.PI * s.x); // 구역 끝에서 옅어진다
    gusts.moveTo(x, y).lineTo(x - Math.sign(w.ax) * s.len, y).stroke({ width: 3, color: 0xffffff, alpha: 0.5 * fade, cap: 'round' });
  }
}

// ---- 상태 ----
let mode = 'select'; // title | select | play | confirm
let id = IDS[0];
let P = params(id);
let p = newPlayer(START[0] * TILE, yOf(START[1]) - P.h);
let rig = null;
let stats = { falls: 0, time: 0, best: 0, cleared: 0 };
let camY = HEIGHT * TILE - H;
let quipT = 0;
const keys = { left: false, right: false, jump: false };
let flail = 0;
let clockT = 0; // 놀이 시간(초). 멈춘 채 game.frame(dt) 로 넘겨도 효과가 따라간다

function save() {
  localStorage.setItem(SAVE_KEY, JSON.stringify({ id, x: p.x, h: hOf(p.y + P.h), ...stats })); // 맵이 위로 늘어나도 위치가 유지되게 높이로 저장
}

// 움직임 방식: 젤리(몸을 그물로 나눠 계산) / 예전(그림 바꿔 끼우기). J 키로 바꿔 가며 비교한다.
let jelly = localStorage.getItem('pheeeew-climb-rig') !== 'old';
function makeRig() {
  actor.removeChildren();
  rig = jelly ? new JellyRig(tex[id]) : new Rig(tex[id]);
  rig.root.position.set(p.x + P.w / 2, p.y + P.h);
  actor.addChild(rig.root);
}

function begin(who, saved) {
  id = who;
  P = params(id);
  const sh = saved ? saved.h ?? (5080 - saved.y - P.h) / TILE : START[1]; // 예전 저장(y 픽셀, 맵 높이 128칸 기준)도 읽는다
  p = newPlayer(saved ? saved.x : START[0] * TILE, yOf(sh) - P.h);
  stats = saved ? { falls: saved.falls, time: saved.time, best: saved.best, cleared: Number(saved.cleared) || 0 } : { falls: 0, time: 0, best: 0, cleared: 0 };
  makeRig();
  camY = Math.min(HEIGHT * TILE - H, p.y + P.h / 2 - H * 0.55); // 시작하자마자 제자리(바닥 아래를 비추지 않게)
  ui.play(id);
  mode = 'play';
  sfx.start();
  if (guest) net.connect({ character: id, zone: Math.min(stats.cleared, ZONES.length - 1), finished: stats.cleared >= ZONES.length });
  showRanks();
}

// ---- 화면 전환 ----
let pick = 0;
let saved = null;
// 처음부터 다시: 로컬 저장을 지우고 서버에도 새 등반을 알린다
function forget() {
  localStorage.removeItem(SAVE_KEY);
  saved = null;
  net.restart();
}
function showSelect() {
  mode = 'select';
  ui.select(pick, (i) => { pick = i; showSelect(); }, () => { begin(IDS[pick]); save(); });
}
const go = () => (saved ? begin(saved.id, saved) : showSelect());
function showTitle() {
  mode = 'title';
  ui.title(saved, go, () => { forget(); showSelect(); }, showCredits);
  ui.guest(guest, rename);
  showBoards();
}
async function showBoards() {
  const [today, all] = await Promise.all([net.ranking('today'), net.ranking('all')]);
  if (mode === 'title') ui.boards(today.status === 200 ? today.body.top : null, all.status === 200 ? all.body.top : null, guest?.tag);
}
function showCredits() {
  mode = 'credits';
  ui.credits(() => { ui.closeCredits(); mode = 'title'; });
}
function askRestart() {
  mode = 'confirm';
  keys.left = keys.right = keys.jump = false;
  ui.confirm(() => { forget(); ui.closeConfirm(); showSelect(); }, () => { ui.closeConfirm(); mode = 'play'; });
}

// ---- 서버(순위) ----
// 서버가 없으면 guest 가 null 인 채로 지금처럼 혼자 한다
const net = createNet({ map: MAP_VERSION });
if (ADMIN) for (const k of ['connect', 'reportPos', 'reportState', 'reportZone', 'reportFinish', 'restart', 'createGuest', 'rename']) net[k] = () => ({ status: 0 }); // 관리자 모드: 보고하지 않는다
let guest = null; // { id, nickname, tag }
let rank = null; // 마지막으로 받은 rank 메시지
const showRanks = () => ui.ranks(guest && { rank, status: net.status, tag: guest.tag });
net.on('rank', (m) => { rank = m; showRanks(); });
net.on('status', showRanks);
net.on('welcome', (m) => { guest = m.me; });
const ghosts = createGhosts();
const ghostViews = new Map(); // n -> { c, rig, label, character }
let showGhosts = localStorage.getItem('pheeeew-climb-ghosts') !== 'off';
ghostLayer.visible = showGhosts;
net.on('in', (m) => ghosts.enter(m.p));
net.on('sts', (m) => ghosts.states(m.s, performance.now()));
net.on('out', (m) => ghosts.leave(m.n));
net.on('status', (s) => { if (s !== 'online') ghosts.clear(); }); // 다시 연결되면 서버가 in 부터 다시 보낸다
function dropGhost(n) {
  ghostViews.get(n)?.c.destroy({ children: true }); // 그림(텍스처)은 내 감정이와 같이 쓰므로 지우지 않는다
  ghostViews.delete(n);
}
function drawGhosts(dt) {
  const list = ghosts.sample(performance.now());
  if (!showGhosts) { for (const n of [...ghostViews.keys()]) dropGhost(n); return; }
  const seen = new Set();
  for (const g of list) {
    seen.add(g.n);
    let v = ghostViews.get(g.n);
    if (v && v.character !== g.character) { dropGhost(g.n); v = null; }
    if (!v) {
      const c = new Container();
      const rig = new JellyRig(tex[g.character]);
      const label = new Text({ text: g.nickname, style: { fontFamily: FONT, fontSize: 15, fill: 0xfff8ea, stroke: { color: INK, width: 4 } } }); // 어두운 배경에서도 읽히게
      label.anchor.set(0.5, 1);
      c.addChild(rig.root, label);
      ghostLayer.addChild(c);
      v = { c, rig, label, character: g.character };
      ghostViews.set(g.n, v);
    }
    const feet = yOf(g.h);
    v.c.visible = feet > camY - 160 && feet < camY + H + 160; // 화면 밖 사람은 그리지 않는다
    if (!v.c.visible) continue;
    v.c.alpha = 0.45 * g.alpha;
    const ground = (g.flags & 1) > 0;
    const charging = (g.flags & 2) > 0;
    const splat = (g.flags & 8) > 0;
    v.rig.pose({ ground, charging, charge: g.charge / 100, splat, vx: g.vx, vy: g.vy, facing: g.flags & 4 ? -1 : 1, walking: ground && !charging && !splat && Math.abs(g.vx) > 20 }, dt);
    v.rig.root.position.set(g.x, feet);
    v.label.position.set(g.x, feet - 84);
  }
  for (const n of [...ghostViews.keys()]) if (!seen.has(n)) dropGhost(n);
}
net.on('event', (m) => {
  if (m.tag === guest?.tag) return; // 내 구간 완료는 배너로 이미 보인다
  ui.toast(m.kind === 'finish' ? `${m.nickname} 님이 꼭대기까지 올랐어요!` : `${m.nickname} 님이 ${m.zone}구간(${ZONES[m.zone - 1]?.name ?? ''}) 통과!`);
});
const NICK_ERR = {
  NICKNAME_LENGTH: '닉네임은 2~10자로 정해 주세요.',
  NICKNAME_CHARACTERS: '한글, 영문, 숫자만 쓸 수 있어요.',
  GUEST_CREATE_LIMITED: '잠깐 사이에 너무 많이 만들었어요. 조금 뒤에 다시 해 주세요.',
  NICKNAME_CHANGE_LIMITED: '닉네임은 하루에 3번까지 바꿀 수 있어요.',
};
const nickError = (r) => (r.status === 400 || r.status === 429 ? NICK_ERR[r.body?.code] ?? r.body?.message ?? '닉네임을 다시 확인해 주세요.' : '서버에 닿지 않아요. 혼자 하기로 오를 수 있어요.');
// 첫 접속: 닉네임을 정하거나 혼자 하기
function askNickname() {
  mode = 'nickname';
  ui.nickname({ heading: '닉네임을 정해 주세요', ok: '정하기', alt: '혼자 하기' }, async (name) => {
    const r = await net.createGuest(name);
    if (r.status !== 201) { ui.nicknameError(nickError(r)); return; }
    guest = r.body;
    ui.closeNickname();
    showTitle();
  }, () => { ui.closeNickname(); saved ? showTitle() : showSelect(); });
}
function rename() {
  mode = 'nickname';
  ui.nickname({ heading: '닉네임 바꾸기', value: guest.nickname, ok: '바꾸기', alt: '취소' }, async (name) => {
    const r = await net.rename(name);
    if (r.status !== 200) { ui.nicknameError(nickError(r)); return; }
    guest = r.body;
    ui.closeNickname();
    showTitle();
  }, () => { ui.closeNickname(); showTitle(); });
}

addEventListener('keydown', (e) => {
  if (e.repeat || mode === 'nickname') return; // 닉네임 입력 중에는 게임 키를 쓰지 않는다
  const k = e.code;
  unlock(); // 브라우저는 첫 키 입력 뒤에야 소리를 낸다
  if (k === 'KeyM') { const off = toggleMute(); if (mode === 'play') { say(off ? '소리 끔' : '소리 켬'); quipT = 1.2; } return; }
  if (k === 'KeyB') { const off = bgm.toggle(); if (mode === 'play') { say(off ? '음악 끔' : '음악 켬'); quipT = 1.2; } return; }
  if (['Space', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(k)) e.preventDefault();
  if (mode === 'credits') {
    if (k === 'Escape' || k === 'Enter') { ui.closeCredits(); mode = 'title'; }
    return;
  }
  if (mode === 'title') {
    if (k === 'Enter' || k === 'Space') go();
    if (k === 'KeyN' && saved) { forget(); showSelect(); }
    return;
  }
  if (mode === 'select') {
    if (k === 'ArrowLeft') { pick = (pick + 4) % 5; showSelect(); sfx.blip(); }
    if (k === 'ArrowRight') { pick = (pick + 1) % 5; showSelect(); sfx.blip(); }
    if (k === 'Space' || k === 'Enter') { begin(IDS[pick]); save(); }
    return;
  }
  if (mode === 'confirm') {
    if (k === 'Enter') { forget(); ui.closeConfirm(); showSelect(); }
    if (k === 'Escape' || k === 'KeyN') { ui.closeConfirm(); mode = 'play'; }
    return;
  }
  if (ADMIN && adminKey(k)) { e.preventDefault(); return; }
  if (k === 'ArrowLeft' || k === 'KeyA') { keys.left = true; flail = -1; }
  if (k === 'ArrowRight' || k === 'KeyD') { keys.right = true; flail = 1; }
  if (k === 'Space') keys.jump = true;
  if (k === 'KeyN') askRestart();
  if (k === 'Tab') { e.preventDefault(); ui.foldRanks(); }
  if (k === 'KeyG') { showGhosts = !showGhosts; ghostLayer.visible = showGhosts; localStorage.setItem('pheeeew-climb-ghosts', showGhosts ? 'on' : 'off'); say(showGhosts ? '다른 사람 보이기' : '다른 사람 숨기기'); quipT = 1.2; }
  if (k === 'KeyJ') { jelly = !jelly; localStorage.setItem('pheeeew-climb-rig', jelly ? 'jelly' : 'old'); makeRig(); say(jelly ? '젤리 움직임' : '예전 움직임'); quipT = 1.2; }
});
addEventListener('keyup', (e) => {
  const k = e.code;
  if (k === 'ArrowLeft' || k === 'KeyA') keys.left = false;
  if (k === 'ArrowRight' || k === 'KeyD') keys.right = false;
  if (k === 'Space') keys.jump = false;
});
// ---- 관리자 모드 ----
let back = admin.newTrack(); // 되돌리기 자리
// 캐릭터를 상자 자리(box)로 옮긴다. 가만히 선 채로 놓고, 공중이면 그대로 떨어진다.
function teleport(box) {
  if (!box) return; // 관리자 모드: 되돌릴 자리가 아직 없거나 없는 구간
  const facing = p.facing;
  p = newPlayer(box.x, box.y);
  p.facing = facing;
  acc = 0;
  back = admin.newTrack();
  camY = Math.min(HEIGHT * TILE - H, p.y + P.h / 2 - H * 0.55); // 멀리 옮기면 카메라도 바로
  rig.root.position.set(p.x + P.w / 2, p.y + P.h);
  save();
}
function adminKey(k) {
  if (k === 'KeyV') { ui.adminMap(!mini.hidden ? null : drawMini); return true; }
  if (k === 'PageUp' || k === 'BracketRight') { teleport(admin.screenJump(level, P, p, 1)); return true; }
  if (k === 'PageDown' || k === 'BracketLeft') { teleport(admin.screenJump(level, P, p, -1)); return true; }
  if (/^Digit[1-9]$/.test(k)) { teleport(admin.zoneStart(P, Number(k.slice(5)))); return true; }
  if (k === 'Backspace' || k === 'KeyR') { const b = back.back; teleport(b); return true; } // 옮긴 뒤 가만히 서 있으면 그 자리를 다시 기억해 몇 번이고 되풀이한다
  return false;
}
const mini = ADMIN ? ui.adminPanel(admin.MINI, (mx, my) => {
  const at = admin.fromMini(mx, my);
  if (!at) return;
  teleport(admin.placeAt(level, P, at.x, at.y));
  ui.adminMap(null);
}) : { hidden: true };
function drawMini(ctx) { admin.drawMini(ctx, level, ZONES, p.x + P.w / 2, p.y + P.h); }
if (ADMIN) app.canvas.addEventListener('pointerdown', (e) => { // UI 버튼 위를 누르면 버튼이 받으므로 여기 오지 않는다
  if (mode !== 'play' || !mini.hidden) return;
  const at = admin.toMap(e.clientX, e.clientY, { x: view.x, y: view.y, s: view.scale.x }, camY);
  if (at) teleport(admin.placeAt(level, P, at.x, at.y));
});

addEventListener('pointerdown', unlock); // 버튼을 눌러 시작해도 소리가 나게
document.addEventListener('visibilitychange', () => bgm.hidden(document.hidden));
addEventListener('blur', () => { keys.left = keys.right = keys.jump = false; });

function tick() {
  const fallV = p.vy; // 닿기 직전의 속도와 모은 정도(step 이 지운다)
  const charged = Math.min(1, p.charge / P.chargeMax);
  const dirV = Math.sign(p.vx);
  const pad = p.ground ? level.padUnder(p.x, p.y, P) : null; // 뛰기 직전 발밑(물리도 같은 자리를 본다)
  step(p, { ...keys, flail }, level, P);
  updateNods(p, P, DT);
  if (ADMIN) admin.track(back, p);
  feel(fallV, charged, dirV, pad);
  flail = 0;
  stats.time += DT;
  if (p.ev === 'bounce') rig.wallT = 0.16;
  if (p.ev === 'land' || p.ev === 'splat') {
    rig.landT = 0.18;
    if (p.y - p.takeoffY > 4 * TILE) { // 뛴 곳보다 4칸 넘게 아래에 떨어졌다
      stats.falls++;
      say(INFO[id].fall);
      quipT = 2.2;
    }
    const h = Math.round(hOf(p.y + P.h));
    if (h > stats.best) stats.best = h;
    // 서버는 그 높이의 pos 를 받은 뒤에만 구간·완주를 받으므로 먼저 보고한다(구간 보고가 따라오면 net 이 바로 보낸다)
    net.reportPos({ h: Math.min(GOAL, Math.max(0, h)), best: Math.min(GOAL, stats.best), falls: stats.falls, time: stats.time, x: p.x }); // 평소에는 2초에 한 번으로 net 이 줄인다
    const done = ZONES.filter((z) => h >= z.to).length;
    if (done > stats.cleared) {
      stats.cleared = done;
      net.reportZone({ zone: Math.min(done, ZONES.length - 1), time: stats.time }); // 마지막 구간을 넘는 것 = 완주
      if (done === ZONES.length) net.reportFinish({ time: stats.time, falls: stats.falls });
      ui.banner(`${done}구간 ${ZONES[done - 1].name} 완료! 피유~`, `떨어진 횟수 ${stats.falls} · ${clock(stats.time)}`);
      sfx.clear();
      fx.confetti(p.x + P.w / 2, p.y, Object.values(TINT).map((c) => parseInt(c.slice(1), 16)));
    }
    save();
  }
}

// 손맛: 물리에서 일어난 일(p.ev)에 먼지·별·흔들림·소리를 붙인다
function feel(fallV, charged, dirV, pad) {
  const cx = p.x + P.w / 2;
  const feet = p.y + P.h;
  const tint = parseInt(TINT[id].slice(1), 16);
  if (p.ev === 'jump' && pad?.kind === 'boost') { // 분사 판: 판 밑으로 불꽃, 길게 치솟는 소리, 살짝 흔들림
    const w = (pad.c1 - pad.c0 + 1) * TILE;
    sfx.boost(charged);
    fx.boost(pad.c0 * TILE + w / 2, yOf(pad.h), w);
    fx.dust(cx, feet, 0.3 + charged * 0.4);
    fx.shake(3.5);
  } else if (p.ev === 'jump') { sfx.jump(charged); fx.dust(cx, feet, 0.25 + charged * 0.5); }
  else if (p.ev === 'land') { const v = Math.min(1, Math.max(0, fallV) / 1200); sfx.land(v); fx.dust(cx, feet, 0.2 + v * 0.8); if (v > 0.75) fx.shake(2.5 * v); }
  else if (p.ev === 'splat') { sfx.splat(); fx.dust(cx, feet, 1); fx.stars(cx, feet - 14, 5); fx.shake(9); }
  else if (p.ev === 'bounce') { sfx.bounce(); fx.stars(cx + dirV * P.w / 2, p.y + P.h / 2, 3, tint); fx.shake(1.5); }
  else if (p.ev === 'bump') { sfx.bump(); fx.stars(cx, p.y, 3); fx.shake(1.5); }
  else if (p.ev === 'slide') sfx.slide();
  else if (p.ev === 'hot') { sfx.bump(); fx.stars(cx, feet, 4, 0xff8a5c); fx.shake(3); say('앗 뜨거!'); quipT = 1; }
  else if (p.ev === 'flail') { sfx.blip(); fx.dust(cx, p.y + P.h / 2, 0.3, -Math.sign(p.vx) || 0); }
}

const clock = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;

let acc = 0;
function frame(dt) {
  if (mode !== 'play') return;
  acc += dt;
  while (acc >= DT) { tick(); acc -= DT; }

  const walking = p.ground && !p.charging && p.lock <= 0 && keys.left !== keys.right;
  rig.pose({ ground: p.ground, charging: p.charging, charge: Math.min(1, p.charge / P.chargeMax), splat: p.splat, vx: p.vx, vy: p.vy, facing: p.facing, walking }, dt);
  rig.root.position.set(p.x + P.w / 2, p.y + P.h);
  // 함께 오르기: [몸 가운데 x, 발 높이(칸), vx, vy, 자세 표시, 모은 정도]. 표시 1 땅 위, 2 모으는 중, 4 왼쪽을 봄, 8 엎어짐, 16 미끄러지는 중
  if (!ADMIN) net.reportState([p.x + P.w / 2, Math.max(0, hOf(p.y + P.h)), p.vx, p.vy, (p.ground ? 1 : 0) | (p.charging ? 2 : 0) | (p.facing < 0 ? 4 : 0) | (p.splat ? 8 : 0) | (p.slide ? 16 : 0), Math.min(1, p.charge / P.chargeMax)]);

  sfx.charge(p.charging ? Math.min(1, p.charge / P.chargeMax) : -1);
  if (p.slide && Math.random() < dt * 14) fx.dust(p.x + P.w / 2, p.y + P.h, 0.15); // 미끄러지는 동안 먼지
  gauge.clear();
  if (p.charging) {
    const c = Math.min(1, p.charge / P.chargeMax);
    if (Math.random() < dt * (6 + c * 40)) fx.sparkle(p.x + P.w / 2, p.y + P.h, c > 0.85 ? 0xff6b5e : parseInt(TINT[id].slice(1), 16)); // 모을수록 많이 올라온다
    const gx = p.x + P.w / 2 - 32;
    const gy = p.y - 34;
    gauge.roundRect(gx, gy + 3, 64, 12, 6).fill(0x2b2433); // 그림자
    gauge.roundRect(gx, gy, 64, 12, 6).fill(0xfff8ea).stroke({ width: 3, color: 0x2b2433 });
    if (c > 0.04) gauge.roundRect(gx + 3, gy + 3, 58 * c, 6, 3).fill(c > 0.85 ? 0xff6b5e : TINT[id]);
  }
  hitbox.clear();
  if (DEBUG) hitbox.rect(p.x, p.y, P.w, P.h).stroke({ width: 1, color: 0xff0000 });
  quipT -= dt;
  quip.visible = quipT > 0;
  quip.position.set(p.x + P.w / 2, p.y - 58);

  const target = Math.min(HEIGHT * TILE - H, p.y + P.h / 2 - H * 0.55);
  camY += (target - camY) * (1 - Math.exp(-dt * 7));
  fx.update(dt);
  const [sx, sy] = fx.offset();
  world.x = sx;
  world.y = -camY + sy;
  far.y = -camY * FAR;
  const camH = hOf(camY + H / 2);
  bgs.forEach((b, i) => {
    b.tilePosition.y = -camY * 0.35 + bgShift[i];
    b.alpha = i === 0 ? 1 : Math.max(0, Math.min(1, (camH - (ZONES[i].from - 6)) / 12)); // 구간 경계에서 배경이 서서히 바뀐다
  });
  clockT += dt;
  const under = p.ground ? level.padUnder(p.x, p.y, P) : null;
  const charge = p.charging ? Math.min(1, p.charge / P.chargeMax) : 0;
  nods.forEach((n, i) => {
    const v = nodViews[i];
    if (n.kind === 'boost') { // 분사 판: 숨 쉬듯 빛나고 위로 화살표가 솟는다. 화면 밖이면 건너뛴다
      v.visible = Math.abs(yOf(n.h) - (camY + H / 2)) < H * 0.75;
      if (!v.visible) return;
      const on = under === n;
      const look = boostLook(clockT, on, charge);
      v.halo.alpha = look.glow;
      if (v.shine) v.shine.alpha = look.glow * 0.5;
      v.scale.set(1, on && charge > 0 ? 1 + Math.sin(clockT * 70) * 0.03 * charge : 1); // 모으는 동안 부르르 떤다
      const x0 = n.c0 * TILE;
      const w = (n.c1 - n.c0 + 1) * TILE;
      n.rise = (n.rise ?? []).filter((t) => clockT - t < RISE_LIFE);
      n.riseAcc = Math.min(look.every, (n.riseAcc ?? Math.random() * look.every) + dt);
      if (n.riseAcc >= look.every && n.rise.length < look.max) {
        n.riseAcc = 0;
        n.rise.push(clockT);
        const spread = on ? 40 : 0; // 서 있으면 캐릭터 양옆으로도 솟아 가려지지 않는다
        fx.rise(x0 + 8 - spread + Math.random() * (w - 16 + 2 * spread), yOf(n.h) - 6);
      }
      return;
    }
    if (n.kind === 'hot') { // 달궈진 판: 서 있을수록 붉어지고 떨린다
      const k = Math.min(1, n.t / HOT_STAND);
      v.tint = 0xffffff - (Math.round(k * 110) << 8) - Math.round(k * 140);
      v.x = n.c0 * TILE + (n.c1 - n.c0 + 1) * TILE / 2 + (k > 0.5 ? Math.sin(performance.now() / 22) * 2.5 * k : 0);
      return;
    }
    const sag = Math.max(0, (n.t - NOD_STAND * 0.4) / (NOD_STAND * 0.6)); // 0 -> 1: 꺼지기 직전
    if (n.away > 0 && !v.gone) { sfx.nod(); fx.ring((n.c0 + n.c1 + 1) / 2 * TILE, yOf(n.h) + 10, 0xfff8ea, 60); } // 발판이 꺼지는 순간
    v.gone = n.away > 0;
    v.alpha = n.away > 0 ? 0.12 : 1;
    v.scale.set(1 + sag * 0.06, 1 - sag * 0.3);
    v.y = yOf(n.h) + (n.away > 0 ? 10 : 0);
    v.rotation = sag > 0 ? Math.sin(performance.now() / 40) * 0.02 * sag : 0;
  });
  updateDrops(dt);
  updateSnow(dt, camH);
  updateWind(dt);
  drawGhosts(dt); // 카메라가 정해진 뒤
  ui.hud({ h: Math.max(0, Math.round(hOf(p.y + P.h))), best: stats.best, falls: stats.falls, time: stats.time });
  if (ADMIN) {
    const h = hOf(p.y + P.h);
    const z = Math.max(0, ZONES.findIndex((zz, i) => h < zz.to || i === ZONES.length - 1)); // HUD 와 같이: 경계 높이에 서면 다음 구간
    ui.adminInfo(`높이 ${Math.round(h)}칸 · 화면 ${Math.floor(Math.max(0, h) / SCREEN) + 1} · ${z + 1}구간 ${ZONES[z].name} · 칸 (${Math.floor((p.x + P.w / 2) / TILE)}, ${Math.floor(h)})`);
  }
}
app.ticker.add((tk) => {
  if (!window.paused) frame(Math.min(0.1, tk.deltaMS / 1000)); // paused: 시험할 때 시간을 멈춘다
  bgm.update({ screen: mode, h: hOf(p.y + P.h) });
});

saved = JSON.parse(localStorage.getItem(SAVE_KEY) ?? 'null');
if (saved && !IDS.includes(saved.id)) saved = null;
if (saved && saved.h === undefined) saved.h = (5080 - saved.y - 48) / TILE; // 예전 저장(y 픽셀) 읽기
const me = ADMIN ? { status: 0 } : await net.me(); // 관리자 모드: 닉네임 창 없이 혼자 하기
if (me.status === 200) {
  guest = me.body;
  // 서버에 같은 맵의 이어 할 등반이 있으면 로컬 저장보다 그것을 쓴다(로컬 저장은 서버가 없을 때의 예비)
  const r = me.body.run;
  if (r && r.map === MAP_VERSION && IDS.includes(r.character)) {
    saved = { id: r.character, x: r.x > 0 ? r.x : START[0] * TILE, h: r.height + 0.4, falls: r.falls, time: r.time, best: r.best, cleared: ZONES.filter((z) => r.best >= z.to).length }; // 서버 높이는 정수라 살짝 위에서 내려놓는다
  }
}
ui.loaded();
if (me.status === 401) askNickname();
else if (saved || guest) showTitle();
else showSelect();

if (DEBUG || ADMIN) window.game = { get p() { return p; }, admin: { teleport, key: adminKey, get back() { return back; } }, get rig() { return rig; }, app, render: () => app.render(), view, get camY() { return camY; }, P: () => P, yOf, keys, begin, frame, get stats() { return stats; }, bgm }; // 숨겨진 창에서도 frame(dt) 로 시간을 넘겨 시험한다
