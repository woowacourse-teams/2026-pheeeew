// 맵 데이터. 단위는 칸(40px). 높이는 맨 아래 바닥에서 위로 센다. 화면 1개 = 높이 18칸.
// 칸 종류: 0 빈 곳, 1 꽉 찬 지형, 2 경사 '/'(오른쪽이 높음), 3 경사 '\'(왼쪽이 높음), 4 꾸벅 발판
import { TILE, COLS } from './physics.js';
import * as zone1 from './zones/zone1.js';
import * as zone2 from './zones/zone2.js';

export const SCREEN = 18;
// 구간 파일이 자기 꼭대기 높이(top)를 정한다. 구간 2는 "구간 1이 117에서 끝난다"고 보고 쓴 높이라서,
// 구간 1이 길어지면 그만큼(SHIFT) 통째로 위로 옮긴다.
const TOP1 = zone1.top ?? 117;
const SHIFT = TOP1 - 117;
export const GOAL = (zone2.top ?? 225) + SHIFT; // 지금 만들어진 맵의 끝. 이 높이에 서면 완료
export const HEIGHT = GOAL + 11; // 전체 칸 수(끝 쉼터 위 여유 포함)
export const START = [3, 0]; // 시작 칸, 높이

// to = 이 높이에 서면 그 구간 완료.
export const ZONES = [
  // art = 그림 폴더, old = 그 그림이 아직 없을 때 쓰는 예전 폴더
  // cap = 윗면 전체를 덮는 띠(옥상 난간, 쌓인 눈), windows = 벽에 창문을 줄 맞춰 찍는다, snow = 눈이 내린다
  { name: '뉴욕 빌딩 숲', from: 0, to: TOP1, art: 'nyc', old: 'zone1', cap: true, windows: true },
  { name: '크리스마스 마을', from: TOP1, to: GOAL, art: 'xmas', old: 'zone2', cap: true, snow: true },
];

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

// 꾸벅 발판: 2초 서 있으면 푹 꺼져 사라지고, 3초 뒤 돌아온다. 윗면 높이 h, 칸 c0~c1.
export const NOD_STAND = 2;
export const NOD_AWAY = 3;
export const nods = [];
const nodAt = new Int16Array(COLS * HEIGHT).fill(-1);
function nod(c0, c1, h) {
  for (let c = c0; c <= c1; c++) { set(c, h - 1, 4); nodAt[(HEIGHT - 1 - h) * COLS + c] = nods.length; }
  nods.push({ c0, c1, h, t: 0, away: 0 });
}

// 지형은 구간별 파일에 있다(두 사람이 동시에 고쳐도 겹치지 않게).
zone1.default({ rock, up, down, nod });
const lift = (o = {}) => (o.to === undefined ? o : { ...o, to: o.to + SHIFT });
zone2.default({
  rock: (c0, c1, h0, h1) => rock(c0, c1, h0 + SHIFT, h1 + SHIFT),
  up: (c, h, n, o) => up(c, h + SHIFT, n, lift(o)),
  down: (c, h, n, o) => down(c, h + SHIFT, n, lift(o)),
  nod: (c0, c1, h) => nod(c0, c1, h + SHIFT),
});


// 장식 소품. c = 가로 중심(칸), h = 놓이는 윗면 높이(hang 은 매달리는 밑면 높이), s = 배율, flip = 좌우 뒤집기
// 화살표 표지는 오른쪽 위를 가리킨다. 돌아가는 길에서 다음 발판 방향을 알려 준다.
export const DECOR = [...zone1.decor, ...zone2.decor.map((d) => ({ ...d, h: d.h + SHIFT }))];

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
    if (p.ground && over && Math.abs(p.y + P.h - top) < 0.5) {
      n.t += dt;
      if (n.t >= NOD_STAND) { n.t = 0; n.away = NOD_AWAY; }
    } else n.t = 0;
  }
}

export const yOf = (h) => groundY - h * TILE; // 높이(칸) -> 화면 y
export const hOf = (y) => (groundY - y) / TILE;
