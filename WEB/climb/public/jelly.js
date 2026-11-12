// 젤리 리그: 그림 장수를 늘리는 대신, 몸 그림을 그물로 나눠 매 프레임 모양을 계산한다.
// 원리 세 가지
//  1. 머리 끝이 몸을 늦게 따라온다(용수철). 멈추면 출렁이고, 착지하면 저절로 눌렸다 튄다.
//  2. 눌리면 옆으로 퍼지고 늘어나면 가늘어진다(부피 유지).
//  3. 발은 땅의 한 자리를 딛고 있다가, 몸이 멀어지면 한 걸음 옮긴다.
// 그림은 부위 그림 그대로다(1024 캔버스 좌표, 발 기준점 512,900). 표정은 바뀌지 않는다.
import { Container, MeshPlane, Sprite } from '/vendor/pixi.mjs';

// 감정이 부위 그림 9장(assets/characters/<감정이>/<이름>.webp)
export const PARTS = ['front_body', 'front_hand_l', 'front_hand_r', 'front_foot_l', 'front_foot_r', 'side_body', 'side_hand', 'side_foot_near', 'side_foot_far'];

const BASE_X = 512;
const BASE_Y = 900;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

// 투명 그림에서 실제 그림이 있는 영역(1024 좌표). 부위를 제자리에서 돌리고 늘리는 기준점이 된다.
function bounds(texture) {
  const S = 256;
  const k = 1024 / S;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = S;
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(texture.source.resource, 0, 0, S, S);
  const { data } = ctx.getImageData(0, 0, S, S);
  let x0 = S, y0 = S, x1 = -1, y1 = -1;
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) if (data[(y * S + x) * 4 + 3] > 24) {
    if (x < x0) x0 = x;
    if (x > x1) x1 = x;
    if (y < y0) y0 = y;
    if (y > y1) y1 = y;
  }
  return { cx: ((x0 + x1 + 1) / 2) * k, cy: ((y0 + y1 + 1) / 2) * k, bottom: (y1 + 1) * k, width: (x1 - x0 + 1) * k };
}

// 용수철: x 가 target 을 따라가되 지나쳤다가 돌아온다
class Spring {
  constructor(k, c, x = 0) { this.k = k; this.c = c; this.x = x; this.v = 0; }
  step(target, dt) {
    for (let n = Math.max(1, Math.ceil(dt / 0.008)), h = dt / n; n > 0; n--) { // 잘게 나눠야 튀지 않는다
      this.v += (this.k * (target - this.x) - this.c * this.v) * h;
      this.x += this.v * h;
    }
    return this.x;
  }
  snap(x) { this.x = x; this.v = 0; }
}

// 몸통: 그물로 나눈 그림. 발 기준점이 원점이다.
function body(texture) {
  const mesh = new MeshPlane({ texture, verticesX: 14, verticesY: 14 });
  mesh.pivot.set(BASE_X, BASE_Y);
  const buf = mesh.geometry.getBuffer('aPosition');
  return { mesh, buf, rest: Float32Array.from(buf.data) };
}

function limb(texture) {
  const b = bounds(texture);
  const s = new Sprite(texture);
  s.pivot.set(b.cx, b.cy);
  s.rest = { x: b.cx - BASE_X, y: b.cy - BASE_Y };
  return s;
}

export class JellyRig {
  constructor(tex, width = 74) {
    this.root = new Container();
    const fb = bounds(tex.front_body);
    this.scale = width / fb.width;
    this.H = fb.bottom - (fb.cy * 2 - fb.bottom); // 몸통 높이(캔버스 px)

    this.front = { c: new Container(), body: body(tex.front_body), feet: [limb(tex.front_foot_l), limb(tex.front_foot_r)], hands: [limb(tex.front_hand_l), limb(tex.front_hand_r)] };
    this.front.c.addChild(...this.front.feet, this.front.body.mesh, ...this.front.hands);
    this.side = { c: new Container(), body: body(tex.side_body), feet: [limb(tex.side_foot_far), limb(tex.side_foot_near)], hands: [limb(tex.side_hand)] };
    this.side.feet[0].tint = 0xb9c0cf; // 뒷발은 그늘진 색
    this.side.c.addChild(this.side.feet[0], this.side.body.mesh, this.side.feet[1], ...this.side.hands);
    this.root.addChild(this.front.c, this.side.c);
    this.root.scale.set(this.scale); // 첫 pose 전에 그려져도 제 크기로 보이게
    this.side.c.visible = false;

    this.view = 'front'; // 지금 보이는 쪽
    this.face = 1; // 옆모습일 때 보는 방향
    this.turn = 1; // 돌아선 직후 가로로 살짝 눌린 정도(1 = 다 펴짐)

    this.q = new Spring(420, 33); // 눌림(+) / 늘어남(-). 살짝만 지나쳤다가 자리 잡는다
    this.lean = new Spring(160, 16); // 진행 방향으로 기울기
    // 머리 끝의 출렁임(화면 px). 속도가 바뀌는 순간에만 밀리고, 일정하게 움직일 때는 0 으로 돌아온다.
    this.topX = new Spring(260, 16);
    this.topY = new Spring(300, 18);
    this.lastV = { x: 0, y: 0 };
    // 손발은 몸에 붙어 있다. 자세만 부드럽게 바뀐다(지나치지 않는 용수철).
    this.pose1 = new Spring(300, 22); // 공중에서의 손: -1 = 오를 때 아래로 끌림, +1 = 떨어질 때 위로 뜸. 살짝 늦게 따라온다
    this.pose2 = new Spring(520, 46); // 엎어져 손발을 뻗는 정도 0~1
    this.walk = new Spring(300, 35); // 걷는 정도 0~1
    this.sit = new Spring(420, 26); // 힘을 모을 때 발 위로 내려앉는 정도 0~1. 놓으면 살짝 튀어 오른다
    this.stride = 0; // 걸음 박자
    this.t = Math.random() * 10;
    this.last = null; // 지난 프레임의 화면 위치
    this.wasGround = true;
    this.prevVx = 0;
    this.prevVy = 0;
    this.squeeze = new Spring(380, 20); // 벽에 부딪혀 가로로 눌린 정도
  }

  // st: { ground, charging, charge(0~1), splat, vx, vy, facing, walking }
  // root.position 은 main 이 pose 뒤에 넣는다. 그래서 여기서 보는 위치는 지난 프레임 것이다.
  pose(st, dt) {
    dt = Math.min(dt, 0.05);
    this.t += dt;
    const x = this.root.x;
    const y = this.root.y;
    if (!this.last || Math.hypot(x - this.last.x, y - this.last.y) > 300) { // 처음이거나 순간 이동: 따라오던 것들을 제자리에
      this.last = { x, y };
      this.topX.snap(0);
      this.topY.snap(0);
      this.lastV = { x: 0, y: 0 };
      this.last = { x, y };
      this.fresh = true;
    }
    const vx = (x - this.last.x) / dt;
    const vy = (y - this.last.y) / dt;
    this.last = { x, y };
    // 속도 변화(가속)만큼 머리 끝을 반대로 민다
    if (!this.fresh) {
      this.topX.v -= clamp(vx - this.lastV.x, -400, 400) * 0.28;
      this.topY.v -= clamp(vy - this.lastV.y, -1200, 1200) * 0.2;
    }
    this.fresh = false;
    this.lastV = { x: vx, y: vy };

    // ---- 어느 쪽을 보일지: 옆으로 움직이면 옆모습 ----
    const sideways = !st.splat && (st.walking || (!st.ground && Math.abs(st.vx) > 1));
    const want = sideways ? 'side' : 'front';
    const wantFace = sideways ? st.facing : this.face;
    if (want !== this.view || wantFace !== this.face) { // 바로 바꾸고, 가로로 살짝 눌렸다가 펴지며 자리 잡는다
      this.view = want;
      this.face = wantFace;
      this.turn = 0.86;
      this.pose1.snap(0);
      this.pose2.snap(0);
    }
    this.turn = Math.min(1, this.turn + dt / 0.5);
    const V = this[this.view];
    this.front.c.visible = this.view === 'front';
    this.side.c.visible = this.view === 'side';
    const flip = this.view === 'side' ? -this.face : 1; // 옆모습 원본은 왼쪽을 본다
    const S = this.scale;
    this.root.scale.set(flip * S * this.turn, S);

    // ---- 충격 ----
    if (st.ground && !this.wasGround) { // 착지: 살짝 눌리며 발 위로 내려앉았다가 돌아온다. 세게 떨어질수록 크다
      const hit = 0.35 + 0.65 * clamp(this.prevVy / 1200, 0, 1);
      this.q.v += hit * 7;
      this.sit.v += hit * 9;
    }
    if (!st.ground && Math.sign(st.vx) !== Math.sign(this.prevVx) && Math.abs(this.prevVx) > 50 && Math.abs(st.vx) > 1) this.squeeze.v += 9; // 벽: 가로로 눌린다
    this.wasGround = st.ground;
    this.prevVx = st.vx;
    this.prevVy = st.vy;

    // ---- 몸 모양 ----
    let target = Math.sin(this.t * 3.1) * 0.014 + Math.cos(this.stride * 2) * 0.04 * this.walk.x; // 숨쉬기 + 디딜 때마다 눌렸다 펴짐
    if (st.splat) target = 0.5;
    else if (st.charging) target = st.charge * 0.03; // 모을 때는 거의 눌리지 않는다. 대신 발 위로 앉는다(아래 sit)
    else if (!st.ground) target = -0.03 * clamp(Math.abs(st.vy) / 900, 0, 1); // 공중에서는 거의 원래 모양
    const q0 = this.q.step(target, dt);
    const sq = this.squeeze.step(0, dt);
    const lean = this.lean.step(clamp(vx / 420, -1, 1) * 0.035, dt);
    // 머리 끝이 늦게 따라온다. 화면 px -> 캔버스 px
    const lagX = clamp(this.topX.step(0, dt) / (S * flip), -0.05 * this.H, 0.05 * this.H);
    const lagY = clamp(this.topY.step(0, dt) / S, -0.06 * this.H, 0.2 * this.H);
    const q = clamp(q0 + lagY / this.H, -0.09, 0.56);
    const wide = 1 + 0.55 * q - sq * 0.22; // 눌리면 퍼진다
    const tall = (1 - q) * (1 + sq * 0.14);
    const shake = st.charging ? Math.sin(this.t * 75) * 7 * st.charge * st.charge : 0;
    // 쉬는 자세의 점 (px, py) -> 지금 모양의 점. py 는 위가 음수.
    const side = this.view === 'side';
    const bend = (px, py) => {
      const h = clamp(-py / this.H, 0, 1.2); // 0 = 발밑, 1 = 머리 끝
      // 원본 시트의 옆모습은 가로:세로가 약 1.12 인데 부위 그림은 1.02 다. 전체를 고르게 맞춘다(위만 넓히지 않는다).
      const round = side ? 1.06 : 1;
      return [px * round * wide * (1 - 0.28 * q * h) + (lagX + shake) * h * h + lean * flip * -py, py * tall * (side ? 0.97 : 1)];
    };
    const { buf, rest } = V.body;
    for (let i = 0; i < rest.length; i += 2) {
      const [bx, by] = bend(rest[i] - BASE_X, rest[i + 1] - BASE_Y);
      buf.data[i] = bx + BASE_X;
      buf.data[i + 1] = by + BASE_Y;
    }
    buf.update();

    // ---- 손발: 몸이 변형된 자리에 그대로 붙는다 ----
    const arm = this.pose1.step(st.ground || st.splat ? 0 : clamp(st.vy / 600, -1, 1), dt);
    const up = Math.max(0, arm); // 떨어질 때 손이 위로 뜬다
    const dn = Math.max(0, -arm); // 오를 때 손이 아래·뒤로 끌린다
    const flat = this.pose2.step(st.splat ? 1 : 0, dt);
    const w = this.walk.step(st.walking && st.ground ? 1 : 0, dt);
    this.stride += dt * 11 * w; // 한 걸음 주기 약 0.57초(초당 3.5걸음)
    const rock = Math.sin(this.stride) * 0.075 * w; // 걸을 때 몸이 좌우로 뒤뚱
    V.c.rotation = rock;
    const sit = this.sit.step(st.charging && !st.splat ? 0.25 + st.charge * 0.75 : 0, dt); // 무릎 대신 발: 몸이 발 위로 내려앉는다
    V.c.y = -Math.abs(Math.sin(this.stride)) * 30 * w + sit * 46; // 걸음마다 통통 뜬다(화면에서 약 3px) + 앉기

    V.hands.forEach((hand, i) => {
      const [hx, hy] = bend(hand.rest.x, hand.rest.y);
      const out = this.view === 'front' ? (i ? 1 : -1) : 1; // 몸 바깥쪽
      // 걸을 때 손: 옆모습은 앞뒤로 작은 호를 그리며 휘젓고, 정면은 두 손이 번갈아 위아래로 총총거린다.
      const hp = this.stride + (side ? 0 : i * Math.PI);
      const swing = (side ? Math.sin(hp) * 26 : Math.sin(hp) * 8 * out) * w;
      const pump = (side ? -Math.abs(Math.sin(hp)) * 16 : -Math.max(0, Math.sin(hp)) * 22) * w; // 휘저을 때 살짝 들린다
      const puff = 1 + Math.sin(hp * 2) * 0.06 * w; // 말랑하게 커졌다 작아진다
      hand.position.set(hx + out * (up * 26 + flat * 80) + (side ? dn * 26 : -out * dn * 10) + swing, hy - up * (side ? 70 : 85) + dn * 24 + flat * 55 + pump);
      hand.rotation = out * (up * 0.5 - dn * 0.25) + (side ? -Math.sin(hp) * 0.16 : Math.sin(hp) * 0.1 * out) * w;
      hand.scale.set(puff, 2 - puff);
    });

    V.feet.forEach((foot, i) => {
      const [fx, fy] = bend(foot.rest.x, foot.rest.y);
      const ph = this.stride + i * Math.PI; // 두 발이 번갈아
      const near = this.view === 'side' && i === 1 ? 1 : 0.7; // 옆모습에서는 앞쪽 발이 더 크게 움직인다
      const lift = Math.max(0, Math.sin(ph)) * 80 * w * near; // 드는 높이(화면에서 약 7px)
      const reach = this.view === 'side' ? Math.cos(ph) * 50 * w * near : 0; // 옆모습: 앞으로 내딛고 뒤로 민다
      const air = st.ground ? 0 : clamp(st.vy / 900, -1, 0.6) * 18; // 올라갈 땐 늘어지고 떨어질 땐 당겨진다
      const spread = (flat * 70 + sit * 30) * (foot.rest.x < 0 ? -1 : 1); // 앉으면 발이 바깥으로 밀려 나온다
      foot.position.set(fx + reach + spread, fy - lift - air - V.c.y); // 땅을 딛는 발은 몸이 떠도 바닥에 남는다
      foot.rotation = this.view === 'side' ? -Math.sin(ph) * 0.16 * w : 0; // 들 때 발끝이 들린다
    });
  }
}
