// 함께 오르기: 다른 사람 목록과 보간. 화면을 모른다(그리기는 main.js, 시험은 tools/ghosts_test.mjs).
// 서버는 100ms마다 새 상태를 보낸다. 받은 상태를 DELAY 만큼 늦춰 보여 주면 늘 두 상태 사이에 있게 되어 부드럽게 이어진다.
export const DELAY = 150; // ms
export const STALE = 1000; // 이만큼 새 소식이 없으면 지우기 시작한다(가만히 있는 사람도 1초마다 보낸다)
export const FADE = 500; // 다 지워지는 데 걸리는 시간

const lerp = (a, b, k) => a + (b - a) * k;

export function createGhosts({ max = 20 } = {}) {
  const people = new Map(); // n -> { n, nickname, tag, character, buf: [{ t, x, h, vx, vy, flags, charge }] }

  return {
    // in.p: [{ n, nickname, tag, character }]
    enter(list) {
      for (const { n, nickname, tag, character } of list) {
        if (!people.has(n) && people.size >= max) continue;
        people.set(n, { n, nickname, tag, character, buf: people.get(n)?.buf ?? [] });
      }
    },
    // out.n: [n, ...]
    leave(ns) {
      for (const n of ns) people.delete(n);
    },
    // sts.s: [[n, x, h, vx, vy, flags, charge], ...], t = 받은 시각(ms)
    states(list, t) {
      for (const [n, x, h, vx, vy, flags, charge] of list) {
        people.get(n)?.buf.push({ t, x, h, vx, vy, flags, charge });
      }
    },
    clear() {
      people.clear();
    },
    // t 시각에 그릴 사람들. charge 는 0~100, alpha 는 0~1(지워지는 중이면 1보다 작다).
    sample(t) {
      const at = t - DELAY;
      const out = [];
      for (const p of people.values()) {
        const buf = p.buf;
        if (!buf.length) continue;
        let i = 0;
        while (i + 1 < buf.length && buf[i + 1].t <= at) i++;
        if (i > 0) buf.splice(0, i); // 지나간 상태는 버린다(앞의 한 개만 남긴다)
        const a = buf[0];
        const b = buf[1];
        const last = buf.at(-1);
        const alpha = Math.min(1, Math.max(0, 1 - (at - last.t - STALE) / FADE));
        if (alpha <= 0) { people.delete(p.n); continue; }
        const s = b ?? a;
        const k = at > a.t && s.t > a.t ? (at - a.t) / (s.t - a.t) : 0; // 0~1(마지막 상태 뒤면 a 에 멈춘다)
        out.push({
          n: p.n, nickname: p.nickname, tag: p.tag, character: p.character,
          x: lerp(a.x, s.x, k), h: lerp(a.h, s.h, k), vx: lerp(a.vx, s.vx, k), vy: lerp(a.vy, s.vy, k),
          flags: a.flags, charge: lerp(a.charge, s.charge, k), alpha,
        });
      }
      return out;
    },
  };
}
