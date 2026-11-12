// 맵 데이터. 단위는 칸(40px). 높이는 맨 아래 바닥에서 위로 센다. 화면 1개 = 높이 18칸.
// 칸 종류: 0 빈 곳, 1 꽉 찬 지형, 2 경사 '/'(오른쪽이 높음), 3 경사 '\'(왼쪽이 높음), 4 꾸벅 발판
import { TILE, COLS } from './physics.js';
import * as zone1 from './zones/zone1.js';
import * as zone2 from './zones/zone2.js';
import * as zone3 from './zones/zone3.js';
import * as zone4 from './zones/zone4.js';
import * as zone5 from './zones/zone5.js';
import * as zone6 from './zones/zone6.js';

export const SCREEN = 18;
// 맵 버전: 지형이나 구간 경계가 바뀌면 올린다. 서버는 버전마다 순위를 따로 두고, 꼭대기·경계를 설정(pheeeew.climb.maps)에서 읽는다.
export const MAP_VERSION = 'v1';
// 구간 목록. 구간 파일이 자기 꼭대기 높이(top)를 정하고, 아래 구간이 길어지면 위 구간은 통째로 올라간다.
// base = 그 파일이 "내 시작 바닥은 이 높이"라고 보고 쓴 높이(구간 2만 117, 나머지는 0).
// art = 그림 폴더(public/assets/<art>)
// cap = 윗면 전체를 덮는 띠(옥상 난간, 쌓인 눈), windows = 벽에 창문을 줄 맞춰 찍는다, snow = 눈이 내린다
const FILES = [
  { mod: zone1, base: 0, name: '뉴욕 빌딩 숲', art: 'nyc', cap: true, windows: true, farAlpha: 0.85, bgAt: 0.27 }, // farAlpha = 원경 건물의 진하기, bgAt = 구간을 시작할 때 화면 맨 위에 올 배경 그림의 세로 위치(0~1)
  { mod: zone2, base: 117, name: '크리스마스 마을', art: 'xmas', cap: true, snow: true },
  { mod: zone3, base: 0, name: '구름 위 성', art: 'castle', cap: true },
  { mod: zone4, base: 0, name: '열기구 축제 하늘', art: 'balloon', cap: true },
  { mod: zone5, base: 0, name: '로켓 발사대와 우주', art: 'space', cap: true },
  { mod: zone6, base: 0, name: '달', art: 'moon', cap: true },
];
// to = 이 높이에 서면 그 구간 완료.
export const ZONES = [];
{
  let from = 0;
  for (const f of FILES) {
    const to = from + (f.mod.top - f.base);
    const { mod, base, ...info } = f;
    ZONES.push({ ...info, from, to, shift: from - base, mod });
    from = to;
  }
}
export const GOAL = ZONES.at(-1).to; // 이 높이에 서면 완주
export const HEIGHT = GOAL + 11; // 전체 칸 수(끝 쉼터 위 여유 포함)
export const START = [3, 0]; // 시작 칸, 높이

export const groundY = (HEIGHT - 1) * TILE; // 맨 아래 바닥 윗면의 화면 좌표
const grid = new Uint8Array(COLS * HEIGHT);
const set = (c, h, v) => { grid[(HEIGHT - 2 - h) * COLS + c] = v; };

// 지형 덩어리: 칸 c0~c1, 높이 h0~h1 (윗면이 h1)
function rock(c0, c1, h0, h1) {
  for (let h = h0; h < h1; h++) for (let c = c0; c <= c1; c++) set(c, h, 1);
}
// 오른쪽으로 올라가는 경사 n칸. 첫 칸은 (c, h). 각 경사 칸 아래를 depth 칸만큼, 또는 높이 to 까지 채운다.
function up(c, h, n, { to, depth = 0 } = {}) {
  for (let k = 0; k < n; k++) {
    set(c + k, h + k, 2);
    for (let f = to ?? h + k - depth; f < h + k; f++) set(c + k, f, 1);
  }
}
// 오른쪽으로 내려가는 경사 n칸. 첫 칸(가장 높은 칸)은 (c, h).
function down(c, h, n, { to, depth = 0 } = {}) {
  for (let k = 0; k < n; k++) {
    set(c + k, h - k, 3);
    for (let f = to ?? h - k - depth; f < h - k; f++) set(c + k, f, 1);
  }
}

// 발판 장치 세 가지. 모두 윗면 높이 h, 칸 c0~c1 이고 평소에는 꽉 찬 지형처럼 딛는다.
//  nod   꾸벅 발판: 2초 서 있으면 푹 꺼져 사라지고, 3초 뒤 돌아온다.
//  hot   달궈진 판: 1.8초 서 있으면 위로 튕겨 낸다(판은 그대로 있다).
//  boost 분사 판: 여기서 뛰면 점프 속도가 1.4배(높이 약 2배).
export const NOD_STAND = 2;
export const NOD_AWAY = 3;
export const HOT_STAND = 1.8; // 1.5 에서 늘림(2026-11-12): 사람 반응 0.25초를 넣어도 판 1·5의 느린 쪽(1.6초)이 들어오게
export const BOOST = 1.4;
export const nods = [];
const nodAt = new Int16Array(COLS * HEIGHT).fill(-1);
function pad(kind, c0, c1, h) {
  for (let c = c0; c <= c1; c++) { set(c, h - 1, 4); nodAt[(HEIGHT - 1 - h) * COLS + c] = nods.length; }
  nods.push({ kind, c0, c1, h, t: 0, away: 0 });
}
// 바람 구역: 칸 c0~c1, 높이 h0~h1 안의 공중에서 가로로 민다. ax = 가속(px/s², +는 오른쪽). 땅에 서 있을 때는 밀지 않는다.
export const winds = [];

// 지형은 구간별 파일에 있다(여러 사람이 동시에 고쳐도 겹치지 않게).
for (const z of ZONES) {
  const d = z.shift;
  const lift = (o = {}) => (o.to === undefined ? o : { ...o, to: o.to + d });
  z.mod.default({
    rock: (c0, c1, h0, h1) => rock(c0, c1, h0 + d, h1 + d),
    up: (c, h, n, o) => up(c, h + d, n, lift(o)),
    down: (c, h, n, o) => down(c, h + d, n, lift(o)),
    nod: (c0, c1, h) => pad('nod', c0, c1, h + d),
    hot: (c0, c1, h) => pad('hot', c0, c1, h + d),
    boost: (c0, c1, h) => pad('boost', c0, c1, h + d),
    wind: (c0, c1, h0, h1, ax) => winds.push({ c0, c1, h0: h0 + d, h1: h1 + d, ax }),
  });
}

// 장식 소품. c = 가로 중심(칸), h = 놓이는 윗면 높이(hang 은 매달리는 밑면 높이), s = 배율, flip = 좌우 뒤집기
// 화살표 표지는 오른쪽 위를 가리킨다. 돌아가는 길에서 다음 발판 방향을 알려 준다.
export const DECOR = ZONES.flatMap((z) => (z.mod.decor ?? []).map((d) => ({ ...d, h: d.h + z.shift })));
// 안내 표지판(충돌 없음): 새 장치가 처음 나오는 자리에서 그것이 무엇인지 알려 준다. zone = 구간 번호(0부터)
export const SIGNS = ZONES.flatMap((z, zone) => (z.mod.signs ?? []).map((s) => ({ ...s, h: s.h + z.shift, zone })));

export const level = {
  rows: HEIGHT,
  // 좌우 벽과 맨 아래 바닥은 항상 막혀 있다. 위는 뚫려 있다.
  tile(c, r) {
    if (c <= 0 || c >= COLS - 1 || r >= HEIGHT - 1) return 1;
    if (r < 0) return 0;
    const t = grid[r * COLS + c];
    if (t !== 4) return t;
    return nods[nodAt[r * COLS + c]].away > 0 ? 0 : 1; // 꺼진 꾸벅 발판은 빈 곳
  },
  // 그리기용: 꾸벅 발판을 4 로 돌려준다
  raw(c, r) {
    const t = this.tile(c, r);
    return r >= 0 && r < HEIGHT - 1 && c > 0 && c < COLS - 1 && grid[r * COLS + c] === 4 ? 4 : t;
  },
  // 이 점(px)의 바람 가속. 없으면 0.
  wind(x, y) {
    const c = x / TILE;
    const h = (groundY - y) / TILE;
    let ax = 0;
    for (const w of winds) if (c >= w.c0 && c < w.c1 + 1 && h >= w.h0 && h < w.h1) ax += w.ax;
    return ax;
  },
  // 발밑(px 상자)에 있는 발판 장치. 없으면 null.
  padUnder(x, y, P) {
    const r = Math.floor((y + P.h + 1) / TILE);
    if (r < 0 || r >= HEIGHT - 1) return null;
    for (let c = Math.max(1, Math.floor(x / TILE)); c <= Math.min(COLS - 2, Math.floor((x + P.w - 0.001) / TILE)); c++) {
      const i = nodAt[r * COLS + c];
      if (i >= 0 && nods[i].away <= 0) return nods[i];
    }
    return null;
  },
};

// 꾸벅 발판 시간 흐름. 게임에서만 부른다(검증기는 부르지 않아서, 발판이 늘 있는 것으로 계산한다:
// 착지할 때마다 2초가 새로 주어지고 최대 차지가 0.8초라서 그 가정이 성립한다).
export function updateNods(p, P, dt) {
  for (const n of nods) {
    const x0 = n.c0 * TILE;
    const x1 = (n.c1 + 1) * TILE;
    const top = groundY - n.h * TILE;
    const over = p.x < x1 && p.x + P.w > x0;
    if (n.away > 0) {
      n.away -= dt;
      // 돌아올 자리에 캐릭터가 겹쳐 있으면 비킬 때까지 기다린다
      if (n.away <= 0 && over && p.y < top + TILE && p.y + P.h > top) n.away = 0.01;
      continue;
    }
    if (n.kind === 'boost') continue;
    if (p.ground && over && Math.abs(p.y + P.h - top) < 0.5) {
      n.t += dt;
      if (n.kind === 'hot') {
        if (n.t >= HOT_STAND) { // 앗 뜨거: 위로 튕겨 낸다. 모으던 힘은 사라진다
          n.t = 0;
          p.ground = false; p.charging = false; p.charge = 0; p.flailed = false;
          p.vy = -760; p.vx = p.facing * 90;
          p.peakY = p.takeoffY = p.y;
          p.ev = 'hot';
        }
      } else if (n.t >= NOD_STAND) { n.t = 0; n.away = NOD_AWAY; }
    } else n.t = 0;
  }
}

export const yOf = (h) => groundY - h * TILE; // 높이(칸) -> 화면 y
export const hOf = (y) => (groundY - y) / TILE;
