// 감정이 리그. 부위 PNG(1024 캔버스, 발 기준점 512,900)를 코드로 움직인다.
// 점프 전용 그림이 오기 전까지는 기존 정면·옆면 부위를 늘리고 눌러서 자세를 만든다.
import { Container, Sprite } from '/vendor/pixi.mjs';

const BASE_X = 512;
const BASE_Y = 900;
// 점프 자세 그림(통짜 1장씩). 있으면 리그 대신 보여 준다. 옆모습 자세는 왼쪽을 본다.
export const JUMPS = ['jump_charge', 'jump_rise', 'jump_apex', 'jump_fall', 'jump_land', 'jump_wall', 'jump_splat'];
export const PARTS = ['front_body', 'front_hand_l', 'front_hand_r', 'front_foot_l', 'front_foot_r', 'side_body', 'side_hand', 'side_foot_near', 'side_foot_far'];

// 투명 PNG에서 실제 그림이 있는 영역. 부위를 제자리에서 돌리고 늘리는 기준점이 된다.
export function bounds(texture) {
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

function part(texture, pivot) {
  const b = bounds(texture);
  const s = new Sprite(texture);
  const py = pivot === 'bottom' ? b.bottom : b.cy;
  s.pivot.set(b.cx, py);
  s.base = { x: b.cx - BASE_X, y: py - BASE_Y };
  s.box = b;
  return s;
}

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

export class Rig {
  // tex: { front_body: Texture, ... }, width: 화면에서 몸통이 차지할 폭(px)
  constructor(tex, width = 74) {
    this.root = new Container();
    this.f = { footL: part(tex.front_foot_l), footR: part(tex.front_foot_r), body: part(tex.front_body, 'bottom'), handL: part(tex.front_hand_l), handR: part(tex.front_hand_r) };
    this.s = { footFar: part(tex.side_foot_far), body: part(tex.side_body, 'bottom'), footNear: part(tex.side_foot_near), hand: part(tex.side_hand) };
    this.front = new Container();
    this.front.addChild(this.f.footL, this.f.footR, this.f.body, this.f.handL, this.f.handR);
    this.side = new Container();
    this.side.addChild(this.s.footFar, this.s.body, this.s.footNear, this.s.hand);
    this.s.footFar.tint = 0xb9c0cf; // 뒷발은 그늘진 색
    this.root.addChild(this.front, this.side);
    this.frames = null;
    if (tex.jump_charge) {
      this.frames = {};
      for (const k of JUMPS) {
        const sp = new Sprite(tex[k]);
        sp.pivot.set(BASE_X, BASE_Y);
        sp.visible = false;
        this.frames[k] = sp;
        this.root.addChild(sp);
      }
    }
    this.wallT = 0; // 벽에 부딪힌 직후 자세를 보여 줄 시간
    this.scale = width / this.f.body.box.width;
    this.t = Math.random() * 10;
    this.landT = 0;
  }

  // st: { ground, charging, charge(0~1), splat, vx, vy, facing, walking }
  pose(st, dt) {
    const { f, s } = this;
    this.t += dt;
    const t = this.t;
    for (const p of [f.footL, f.footR, f.body, f.handL, f.handR, s.footFar, s.body, s.footNear, s.hand]) {
      p.position.set(p.base.x, p.base.y);
      p.rotation = 0;
      p.scale.set(1);
    }
    const side = !st.splat && (st.walking || (!st.ground && Math.abs(st.vx) > 1));
    this.front.visible = !side;
    this.side.visible = side;
    this.front.x = 0;
    this.root.scale.set(side ? -st.facing * this.scale : this.scale, this.scale); // 옆모습 원본은 왼쪽을 본다
    this.landT = Math.max(0, this.landT - dt);
    this.wallT = Math.max(0, this.wallT - dt);

    if (this.frames) {
      // 그림은 겹쳐 섞지 않고 바로 바꾼다(반투명으로 섞으면 몸이 비쳐 깜빡인다).
      let key = null;
      if (st.splat) key = 'jump_splat';
      else if (st.charging && st.charge > 0.3) key = 'jump_charge';
      else if (!st.ground) key = this.wallT > 0 ? 'jump_wall' : st.vy < -160 ? 'jump_rise' : st.vy > 160 ? 'jump_fall' : 'jump_apex';
      else if (this.landT > 0 && !st.walking) key = 'jump_land';
      for (const k of JUMPS) this.frames[k].visible = k === key;
      if (key) {
        this.front.visible = this.side.visible = false;
        const f = this.frames[key];
        const sideways = key === 'jump_rise' || key === 'jump_apex' || key === 'jump_fall' || key === 'jump_wall';
        this.root.scale.set(sideways ? -st.facing * this.scale : this.scale, this.scale);
        // 차지 그림은 다 모았을 때의 모습. 덜 모았을 때는 조금 덜 눌린 것처럼 세운다.
        if (key === 'jump_charge') f.scale.set(1 - (1 - st.charge) * 0.07, 1 + (1 - st.charge) * 0.3);
        f.x = key === 'jump_charge' ? Math.sin(t * 70) * 5 * st.charge * st.charge : 0;
        return;
      }
    }

    if (st.splat) { // 엎어짐: 납작하게 퍼진다
      f.body.scale.set(1.28, 0.46);
      f.handL.position.set(f.handL.base.x - 90, f.handL.base.y + 70);
      f.handR.position.set(f.handR.base.x + 90, f.handR.base.y + 70);
      f.footL.x -= 60;
      f.footR.x += 60;
    } else if (st.charging) { // 웅크려 모으기
      const k = st.charge;
      f.body.scale.set(1 + 0.16 * k, 1 - 0.32 * k);
      f.handL.y += 70 * k;
      f.handR.y += 70 * k;
      f.handL.x -= 26 * k;
      f.handR.x += 26 * k;
      this.front.x = Math.sin(t * 70) * 5 * k * k;
    } else if (!st.ground) {
      const up = clamp(-st.vy / 1000, -1, 1); // 1 = 빠르게 상승, -1 = 빠르게 낙하
      if (side) {
        s.body.scale.set(1 - 0.07 * up, 1 + 0.1 * up);
        s.body.rotation = -0.1 - up * 0.12;
        s.hand.y -= 40 - up * 50;
        s.hand.rotation = up * 0.5;
        s.footNear.x += 30 * up;
        s.footNear.y += up > 0 ? 20 : -26;
        s.footFar.x -= 34 * up;
        s.footFar.y += up > 0 ? 26 : -18;
      } else {
        f.body.scale.set(1 - 0.07 * up, 1 + 0.1 * up);
        const hand = up > 0 ? 24 : -90; // 올라갈 땐 아래로, 떨어질 땐 번쩍
        f.handL.y += hand;
        f.handR.y += hand;
        f.handL.rotation = up > 0 ? 0 : 0.6;
        f.handR.rotation = up > 0 ? 0 : -0.6;
        f.footL.y += up > 0 ? 26 : -14;
        f.footR.y += up > 0 ? 26 : -14;
      }
    } else if (st.walking) {
      // 한 걸음 주기 약 0.63초. 앞발은 크게, 뒷발은 작고 어둡게 움직여 다리가 둘로 또렷이 읽히게 한다.
      const ph = t * 10;
      const bob = Math.abs(Math.sin(ph));
      const q = 0.028 * Math.cos(ph * 2);
      s.footNear.x += Math.sin(ph) * 44;
      s.footNear.y -= Math.max(0, Math.cos(ph)) * 30; // 앞으로 나갈 때만 든다
      s.footNear.rotation = -Math.max(0, Math.cos(ph)) * 0.25;
      s.footFar.x -= Math.sin(ph) * 30;
      s.footFar.y -= Math.max(0, -Math.cos(ph)) * 20;
      s.body.y -= bob * 12;
      s.body.scale.set(1 + q, 1 - q);
      s.body.rotation = -0.05 + Math.sin(ph) * 0.015;
      s.hand.x -= Math.sin(ph) * 22;
      s.hand.y -= bob * 12;
      s.hand.rotation = -Math.sin(ph) * 0.3;
    } else { // 가만히: 숨쉬기 + 착지 직후 꾹 눌림
      const br = Math.sin(t * 3.2);
      const sq = Math.sin((this.landT / 0.18) * Math.PI) * 0.16;
      f.body.scale.set(1 - br * 0.012 + sq, 1 + br * 0.022 - sq);
      f.handL.y -= br * 7 - sq * 120;
      f.handR.y -= br * 7 - sq * 120;
    }
  }
}
