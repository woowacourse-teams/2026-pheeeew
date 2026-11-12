// 맵 데이터. 단위는 칸(40px). 높이는 맨 아래 바닥에서 위로 센다. 화면 1개 = 높이 18칸.
// 칸 종류: 0 빈 곳, 1 꽉 찬 지형, 2 경사 '/'(오른쪽이 높음), 3 경사 '\'(왼쪽이 높음), 4 꾸벅 발판
import { TILE, COLS } from './physics.js';

export const SCREEN = 18;
export const HEIGHT = 236; // 전체 칸 수(구간 1~2 + 구간 3 쉼터)
export const GOAL = 225; // 지금 만들어진 맵의 끝. 이 높이에 서면 완료
export const START = [3, 0]; // 시작 칸, 높이

// to = 이 높이에 서면 그 구간 완료.
export const ZONES = [
  // art = 그림 폴더, old = 그 그림이 아직 없을 때 쓰는 예전 폴더
  // cap = 윗면 전체를 덮는 띠(옥상 난간, 쌓인 눈), windows = 벽에 창문을 줄 맞춰 찍는다, snow = 눈이 내린다
  { name: '뉴욕 빌딩 숲', from: 0, to: 117, art: 'nyc', old: 'zone1', cap: true, windows: true },
  { name: '크리스마스 마을', from: 117, to: 225, art: 'xmas', old: 'zone2', cap: true, snow: true },
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

// ── 화면 1 (0~18) 바닥 웅덩이: 계단, 머리 위 덩어리, 위에서 떨어지면 타는 미끄럼 ──
rock(7, 11, 0, 2);
rock(14, 19, 0, 5);
rock(23, 30, 0, 9);
rock(12, 16, 10, 13);
up(9, 10, 3, { to: 10 });
rock(21, 30, 16, 18);
rock(1, 4, 6, 17); // 시작 지점 위로 튀어나온 왼쪽 벽

// ── 화면 2 (18~36) 홈통: 낮은 천장 아래 건너뛰기, 내려갔다 올라가기, 문턱 경사 ──
rock(20, 30, 23, 27);
rock(17, 19, 26, 27); // 왼쪽으로 내민 턱: 구석에서 톡 뛰어 내려오기 쉽게
rock(1, 14, 17, 19); // 왼쪽 벽에서 뻗은 선반. 화면 1의 천장이 된다
rock(1, 5, 19, 25);
rock(1, 3, 25, 36);
rock(11, 13, 27, 29); // 천장 낮은 구석. 톡 뛰어 오른쪽 아래로 내려가야 한다
rock(10, 13, 33, 35);
rock(14, 18, 31, 33);
rock(14, 15, 33, 44); // 우산 기둥
rock(24, 30, 34, 36);
up(22, 34, 2, { to: 34 });

// ── 화면 3 (36~54) 뒤집힌 우산: 벽 튕김 통로, 오목한 우산 두 개 ──
rock(22, 23, 39, 50);
rock(28, 30, 41, 42);
rock(24, 25, 47, 48);
rock(13, 16, 44, 46);
down(11, 47, 2, { to: 44 });
up(17, 46, 2, { to: 44 });
rock(1, 2, 50, 54);
rock(3, 6, 50, 52);
up(7, 52, 1, { to: 50 });

// ── 화면 4 (54~72) 눈물 기둥: 낮은 천장 건너뛰기, 가장자리에서 뛰기 ──
rock(12, 17, 55, 58);
rock(23, 30, 56, 58);
rock(29, 30, 58, 63); // 동굴 안쪽 벽
rock(13, 17, 62, 63);
rock(26, 30, 63, 66);
rock(1, 7, 62, 68);
rock(13, 14, 63, 70);
rock(13, 19, 70, 72);
rock(1, 2, 54, 62); // 왼쪽 벽을 두껍게
rock(29, 30, 66, 79); // 오른쪽 벽을 두껍게

// ── 화면 5 (72~90) 젖은 창틀: 좁은 턱, 놓치면 긴 미끄럼틀을 타고 화면 4로 ──
rock(25, 30, 73, 75); // 오른쪽 벽에서 뻗은 팔. 좁은 턱을 넘기면 여기 떨어진다
rock(23, 24, 75, 77); // 팔 끝에 솟은 좁은 턱
rock(28, 30, 79, 82);
rock(30, 30, 82, 90);
rock(20, 21, 82, 88); // 공중에 매달린 기둥
up(15, 85, 4, { to: 83 }); // 기둥 옆 경사: 닿으면 왼쪽 긴 미끄럼틀로 넘겨진다
down(2, 92, 12, { to: 81 }); // 긴 미끄럼틀: 왼쪽으로 떨어지면 화면 4까지 쓸려 내려간다
rock(1, 1, 81, 115);
rock(29, 30, 90, 106);
rock(11, 13, 91, 93);

// ── 화면 6 (90~108) 관문 우산 계단: 양 끝이 미끄러운 우산 4개. 놓치면 화면 3~4까지 ──
rock(20, 23, 97, 98); up(19, 97, 1); down(24, 97, 1);
rock(9, 11, 102, 103); up(8, 102, 1); down(12, 102, 1);
rock(18, 19, 107, 108); up(17, 107, 1); down(20, 107, 1);
rock(27, 28, 112, 113); up(26, 112, 1); down(29, 112, 1);
down(24, 106, 3, { to: 101 }); // 매달린 덩어리. 윗면이 경사라 받아 주지 않는다
rock(5, 7, 108, 115); // 쉼터 밑에 매달린 덩어리

// ── 구간 2 쉼터 ──
rock(1, 3, 93, 115);
rock(1, 22, 115, 117);

// ════ 구간 2: 크리스마스 마을 (117~225) ════
// ── 화면 7 침대맡: 꾸벅 베개 소개(떨어져도 쉼터 바닥) ──
nod(8, 10, 121);
rock(1, 4, 123, 125);
rock(9, 13, 128, 130);
rock(18, 30, 131, 135);

// ── 화면 8 책 더미 ──
rock(24, 26, 135, 139);
nod(15, 18, 143);
rock(1, 9, 145, 149);
rock(14, 17, 152, 154);

// ── 화면 9 식은 커피잔: 테두리는 좁고, 잔 안에 빠지면 각설탕을 딛고 나와야 한다 ──
rock(20, 27, 152, 154);
rock(20, 20, 154, 160);
rock(27, 27, 154, 160);
rock(21, 22, 154, 156);
rock(28, 30, 163, 165);
nod(20, 22, 170);

// ── 화면 10 달 모빌: 꾸벅 발판 2연속, 오른쪽 끝이 미끄러운 초승달 ──
nod(11, 13, 175);
rock(1, 5, 178, 181);
rock(8, 12, 184, 186); down(13, 185, 1);
rock(20, 30, 188, 190);

// ── 화면 11 이불 동굴: 천장 밖으로 나와서 뛰어야 한다 ──
rock(21, 30, 197, 200);
nod(10, 12, 195);
rock(1, 4, 199, 201);
rock(9, 13, 205, 207);

// ── 화면 12 관문 졸음 절벽: 꾸벅 발판 3연속. 쉴 곳이 없고, 놓치면 화면 9까지 떨어진다 ──
nod(19, 21, 212);
nod(27, 29, 217);
nod(19, 21, 222);

// ── 구간 3 쉼터 ──
rock(1, 14, 223, 225);

// ── 구간 2 벽 두껍게 ──
rock(1, 2, 117, 123);
rock(29, 30, 135, 163);
rock(1, 3, 149, 178);
rock(30, 30, 165, 188);
rock(1, 1, 201, 223);

// 장식 소품. c = 가로 중심(칸), h = 놓이는 윗면 높이(hang 은 매달리는 밑면 높이), s = 배율, flip = 좌우 뒤집기
// 화살표 표지는 오른쪽 위를 가리킨다. 돌아가는 길에서 다음 발판 방향을 알려 준다.
export const DECOR = [
  { img: 'arrow_sign', c: 8, h: 19, s: 0.36, flip: true },
  { img: 'arrow_sign', c: 4.6, h: 25, s: 0.3 },
  { img: 'arrow_sign', c: 27, h: 27, s: 0.36, flip: true },
  { img: 'arrow_sign', c: 12.6, h: 93, s: 0.3 },
  // 구간 2: 꾸벅 발판 다음에 어디로 뛰는지 알려 준다
  { img: 'arrow_sign', c: 13, h: 117, s: 0.3, flip: true },
  { img: 'arrow_sign', c: 2.6, h: 125, s: 0.28 },
  { img: 'arrow_sign', c: 27.5, h: 135, s: 0.3, flip: true },
  { img: 'arrow_sign', c: 5, h: 149, s: 0.3 },
  { img: 'arrow_sign', c: 16.4, h: 154, s: 0.28 },
  { img: 'arrow_sign', c: 28.4, h: 165, s: 0.28, flip: true },
  { img: 'arrow_sign', c: 3, h: 181, s: 0.3 },
  { img: 'arrow_sign', c: 25, h: 190, s: 0.3, flip: true },
  { img: 'arrow_sign', c: 2.6, h: 201, s: 0.28 },
  { img: 'arrow_sign', c: 10, h: 207, s: 0.3 },
];

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
