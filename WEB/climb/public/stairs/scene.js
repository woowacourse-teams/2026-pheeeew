import { direction } from './rules.js';
import { FACE } from '../ui.js';

export async function createScene(canvas, ids) {
  const load = (src) => new Promise((resolve, reject) => {
    const image = new Image(); image.onload = () => resolve(image); image.onerror = reject; image.src = src;
  });
  const [step, ...faces] = await Promise.all([load('/assets/stairs/step.webp'), ...ids.map(id => load(FACE(id)))]);
  const ctx = canvas.getContext('2d');
  let width = 0, height = 0, cameraX = 0, cameraY = 0, actorX = 0, actorY = 0, hopAt = -1000;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  function resize() {
    width = innerWidth; height = innerHeight;
    const dpr = Math.min(devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * dpr); canvas.height = Math.round(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  resize(); addEventListener('resize', resize);
  return {
    reset() { cameraX = cameraY = actorX = actorY = 0; hopAt = -1000; },
    hop(now) { hopAt = now; },
    draw(game, character, now, fallMs = 0) {
      ctx.clearRect(0, 0, width, height);
      const scale = Math.min(width / 420, height / 570, 1.4);
      // 계단 칸이 모서리로 맞닿아 한 줄로 이어지게: 가로 간격 = 발판 폭, 세로 간격 = 폭의 절반
      const W = 60 * scale, dx = W, dy = W / 1.6, floor = height * .74;
      const ahead = Math.max(6, Math.min(18, Math.floor((floor - 40) / dy)));
      const points = [];
      let x = game.x;
      for (let h = game.height; h <= game.height + ahead; h++) {
        if (h > game.height) x += direction(game.seed, h - 1) === 'L' ? -1 : 1;
        points.push([h, x]);
      }
      const xs = points.slice(0, 4).map(p => p[1]); // 가까운 몇 칸 위주로 따라간다(긴 묶음은 화면 밖으로 이어져도 된다)
      const targetX = xs.reduce((a, b) => a + b, 0) / xs.length;
      cameraX += (targetX - cameraX) * .25; cameraY += (game.height - cameraY) * .3;
      actorX += (game.x - actorX) * .5; actorY += (game.height - actorY) * .5;
      for (let h = Math.max(0, game.height - 8); h < game.height; h++) points.unshift([h, game.positions[h]]);
      // 발판 원본의 512:320(1.6:1) 비율을 그대로 쓴다.
      for (const [h, px] of points) {
        const sx = width / 2 + (px - cameraX) * dx, sy = floor - (h - cameraY) * dy;
        ctx.globalAlpha = h < game.height ? .5 : 1; // 다음 칸을 따로 표시하지 않는다(헷갈려야 재미)
        ctx.drawImage(step, sx - W / 2, sy, W, W / 1.6); // 네모 발판(1.6:1)이 모서리로 맞닿는다
      }
      ctx.globalAlpha = 1;
      const jump = reduced ? 0 : Math.sin(Math.min(1, Math.max(0, (now - hopAt) / 150)) * Math.PI) * 20 * scale;
      const fall = reduced ? Math.min(fallMs, 280) / 3 : fallMs * fallMs / 650;
      const sx = width / 2 + (actorX - cameraX) * dx, sy = floor - (actorY - cameraY) * dy - jump + fall;
      ctx.save(); ctx.translate(sx, sy); ctx.rotate(reduced ? 0 : fallMs / 600);
      // 캐릭터 원본 캔버스를 정사각형으로 유지. 발바닥(약 88%)을 발판 상단에 맞춘다.
      ctx.drawImage(faces[ids.indexOf(character)], -50 * scale, -88 * scale, 100 * scale, 100 * scale);
      ctx.restore();
    },
  };
}
