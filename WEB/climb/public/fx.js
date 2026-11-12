// 손맛 효과: 먼지, 별, 반짝이, 분사 판의 솟는 화살표와 불꽃, 색종이, 화면 흔들림. 캐릭터 그림이 아니라 효과용 도형이다.
import { Graphics } from '/vendor/pixi.mjs';
import { RISE_LIFE } from './boost.js';

const INK = 0x2b2433;
const PAPER = 0xfff8ea;

export function createFx() {
  const g = new Graphics();
  const parts = [];
  let shake = 0;

  const add = (p) => parts.push({ t: 0, vx: 0, vy: 0, g: 0, rot: 0, spin: 0, ...p });

  const api = {
    view: g,
    // 발밑 먼지: 양옆으로 퍼지는 동글동글한 뭉치. power 0~1
    dust(x, y, power = 0.5, dir = 0) {
      const n = 2 + Math.round(power * 5);
      for (let i = 0; i < n; i++) {
        const side = dir || (i % 2 ? 1 : -1);
        add({ kind: 'puff', x: x + side * (4 + Math.random() * 12), y: y - 2 - Math.random() * 4, vx: side * (30 + Math.random() * 110) * (0.5 + power), vy: -(10 + Math.random() * 50) * (0.4 + power), r: 3 + Math.random() * 4 + power * 4, life: 0.32 + Math.random() * 0.22 });
      }
    },
    // 부딪힌 자리에서 튀는 별
    stars(x, y, n = 3, color = 0xffd35a) {
      for (let i = 0; i < n; i++) {
        const a = Math.random() * Math.PI * 2;
        const v = 110 + Math.random() * 150;
        add({ kind: 'star', x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 70, g: 520, r: 5 + Math.random() * 4, life: 0.42 + Math.random() * 0.2, spin: (Math.random() - 0.5) * 14, color });
      }
    },
    // 힘을 모을 때 올라오는 반짝이
    sparkle(x, y, color) {
      add({ kind: 'spark', x: x + (Math.random() - 0.5) * 60, y: y - Math.random() * 12, vy: -(60 + Math.random() * 90), r: 2 + Math.random() * 2.5, life: 0.35 + Math.random() * 0.2, color });
    },
    // 퍼지는 고리(다 모았을 때, 발판이 꺼질 때)
    ring(x, y, color = PAPER, size = 46) {
      add({ kind: 'ring', x, y, r: size, life: 0.28, color });
    },
    // 분사 판 위로 솟아올라 사라지는 작은 위쪽 화살표
    rise(x, y, color = 0xc8fff2) {
      add({ kind: 'rise', x, y, vy: -(48 + Math.random() * 22), r: 6 + Math.random() * 2, life: RISE_LIFE, color });
    },
    // 분사 판에서 뛰는 순간: 판 밑으로 불꽃이 터지고 윗면에 고리가 퍼진다. x = 판 가운데, y = 판 윗면, w = 판 폭
    boost(x, y, w) {
      for (let i = 0; i < 16; i++) {
        add({ kind: 'flame', x: x + (Math.random() - 0.5) * (w - 10), y: y + 30, vx: (Math.random() - 0.5) * 70, vy: 220 + Math.random() * 260, r: 5 + Math.random() * 5, life: 0.32 + Math.random() * 0.2, color: [0xffffff, 0xbff4ff, 0x7fe7d4][i % 3] });
      }
      add({ kind: 'ring', x, y, r: Math.max(46, w * 0.7), life: 0.3, color: 0xbff4ff });
    },
    // 색종이: 구간을 끝냈을 때
    confetti(x, y, colors) {
      for (let i = 0; i < 46; i++) {
        const a = -Math.PI / 2 + (Math.random() - 0.5) * 1.9;
        const v = 260 + Math.random() * 420;
        add({ kind: 'paper', x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, g: 700, r: 5 + Math.random() * 4, life: 1.3 + Math.random() * 0.8, spin: (Math.random() - 0.5) * 16, color: colors[i % colors.length] });
      }
    },
    shake(amount) { shake = Math.max(shake, amount); },
    // 이번 프레임의 화면 흔들림(px)
    offset() { return shake > 0.2 ? [(Math.random() - 0.5) * 2 * shake, (Math.random() - 0.5) * 2 * shake] : [0, 0]; },
    update(dt) {
      shake *= Math.exp(-dt * 14);
      g.clear();
      for (let i = parts.length - 1; i >= 0; i--) {
        const p = parts[i];
        p.t += dt;
        if (p.t >= p.life) { parts.splice(i, 1); continue; }
        p.vy += p.g * dt;
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.rot += p.spin * dt;
        const k = p.t / p.life; // 0 -> 1
        if (p.kind === 'puff') {
          p.vx *= Math.exp(-dt * 5);
          g.circle(p.x, p.y, p.r * (0.7 + k * 0.9)).fill({ color: PAPER, alpha: 0.9 * (1 - k) }).stroke({ width: 2, color: INK, alpha: 0.45 * (1 - k) });
        } else if (p.kind === 'star') {
          const pts = [];
          for (let j = 0; j < 8; j++) { const a = p.rot + (j * Math.PI) / 4; const rr = (j % 2 ? 0.42 : 1) * p.r * (1 - k * 0.5); pts.push(p.x + Math.cos(a) * rr, p.y + Math.sin(a) * rr); }
          g.poly(pts).fill({ color: p.color, alpha: 1 - k * k }).stroke({ width: 2, color: INK, alpha: 0.8 * (1 - k) });
        } else if (p.kind === 'spark') {
          g.circle(p.x, p.y, p.r * (1 - k)).fill({ color: p.color, alpha: 1 - k });
        } else if (p.kind === 'rise') { // ^ 모양. 처음 15% 동안 나타나고 끝으로 갈수록 옅어진다
          const a = Math.min(1, k / 0.15) * (1 - k);
          const pts = [p.x - p.r, p.y + p.r * 0.6, p.x, p.y - p.r * 0.4, p.x + p.r, p.y + p.r * 0.6];
          g.poly(pts, false).stroke({ width: 5.5, color: INK, alpha: 0.55 * a, cap: 'round', join: 'round' });
          g.poly(pts, false).stroke({ width: 2.8, color: p.color, alpha: a, cap: 'round', join: 'round' });
        } else if (p.kind === 'flame') { // 아래로 길게 늘어진 불꽃
          const r = p.r * (1 - k * 0.6);
          g.ellipse(p.x, p.y, r * 0.7, r * 1.8).fill({ color: p.color, alpha: 0.95 * (1 - k) });
        } else if (p.kind === 'ring') {
          g.circle(p.x, p.y, p.r * (0.3 + k)).stroke({ width: 5 * (1 - k), color: p.color, alpha: 1 - k });
        } else { // 색종이
          const w = p.r * Math.abs(Math.cos(p.rot)) + 1.5;
          g.rect(p.x - w / 2, p.y - p.r / 2, w, p.r).fill({ color: p.color, alpha: Math.min(1, (1 - k) * 3) });
        }
      }
    },
  };
  return api;
}
