import { direction } from './rules.js';
import { FACE } from '../ui.js';

export async function createScene(canvas, ids) {
  const load = (src) => new Promise((resolve, reject) => {
    const image = new Image(); image.onload = () => resolve(image); image.onerror = reject; image.src = src;
  });
  const [step, ...faces] = await Promise.all([load('/assets/balloon/nod.webp'), ...ids.map(id => load(FACE(id)))]);
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
      const dx = 48 * scale, dy = 54 * scale, floor = height * .74;
      const ahead = Math.max(3, Math.min(7, Math.floor((floor - 125) / dy)));
      const points = [];
      let x = game.x;
      for (let h = game.height; h <= game.height + ahead; h++) {
        if (h > game.height) x += direction(game.seed, h - 1) === 'L' ? -1 : 1;
        points.push([h, x]);
      }
      const xs = points.map(p => p[1]);
      const targetX = (Math.min(...xs) + Math.max(...xs)) / 2;
      cameraX += (targetX - cameraX) * .25; cameraY += (game.height - cameraY) * .3;
      actorX += (game.x - actorX) * .5; actorY += (game.height - actorY) * .5;
      for (let h = Math.max(0, game.height - 4); h < game.height; h++) points.unshift([h, game.positions[h]]);
      // 발판 원본의 512:160 비율을 그대로 쓴다.
      for (const [h, px] of points) {
        const sx = width / 2 + (px - cameraX) * dx, sy = floor - (h - cameraY) * dy;
        ctx.globalAlpha = h < game.height ? .38 : h === game.height + 1 ? 1 : .78;
        ctx.drawImage(step, sx - 43 * scale, sy, 86 * scale, 86 * scale * 160 / 512);
        if (h === game.height + 1) {
          ctx.globalAlpha = 1; ctx.fillStyle = '#ffd35a'; ctx.font = `${14 * scale}px Jua, sans-serif`;
          ctx.textAlign = 'center'; ctx.fillText('다음', sx, sy + 40 * scale);
        }
      }
      ctx.globalAlpha = 1;
      const jump = reduced ? 0 : Math.sin(Math.min(1, Math.max(0, (now - hopAt) / 150)) * Math.PI) * 20 * scale;
      const fall = reduced ? Math.min(fallMs, 280) / 3 : fallMs * fallMs / 650;
      const sx = width / 2 + (actorX - cameraX) * dx, sy = floor - (actorY - cameraY) * dy - jump + fall;
      ctx.save(); ctx.translate(sx, sy); ctx.rotate(reduced ? 0 : fallMs / 600);
      // 캐릭터 원본 캔버스를 정사각형으로 유지. 발바닥(약 88%)을 발판 상단에 맞춘다.
      ctx.drawImage(faces[ids.indexOf(character)], -61 * scale, -107 * scale, 122 * scale, 122 * scale);
      ctx.restore();
    },
  };
}
