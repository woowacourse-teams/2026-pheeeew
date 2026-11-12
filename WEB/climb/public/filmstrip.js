// 시험용: 연속 프레임을 한 장에 이어 붙여 움직임을 눈으로 확인한다. ?debug 화면에서 콘솔로 부른다.
// film({ n: 장수, every: 몇 틱마다(1틱 = 1/120초), zoom, before(i): 각 장을 찍기 전에 입력을 바꾸는 함수 })
window.film = async ({ n = 16, every = 4, zoom = 5, cols = 8, before = () => {} } = {}) => {
  const g = window.game;
  const cell = 150;
  const sheet = document.createElement('canvas');
  sheet.width = cols * cell;
  sheet.height = Math.ceil(n / cols) * cell;
  const ctx = sheet.getContext('2d');
  const dpr = g.app.canvas.width / innerWidth;
  for (let i = 0; i < n; i++) {
    before(i);
    for (let k = 0; k < every; k++) g.frame(1 / 120);
    const p = g.p;
    const s0 = Math.min(innerWidth / 1280, innerHeight / 720);
    g.view.scale.set(s0 * zoom);
    g.view.position.set(innerWidth / 2 - (p.x + 30) * s0 * zoom, innerHeight / 2 - (p.y + 14 - g.camY) * s0 * zoom);
    g.render();
    const half = (cell / 2) * dpr * (zoom * s0 * 0.68);
    ctx.drawImage(g.app.canvas, (innerWidth / 2) * dpr - half, (innerHeight / 2) * dpr - half, half * 2, half * 2, (i % cols) * cell, Math.floor(i / cols) * cell, cell, cell);
    ctx.fillStyle = '#000';
    ctx.font = '12px sans-serif';
    ctx.fillText(String(i), (i % cols) * cell + 4, Math.floor(i / cols) * cell + 13);
  }
  let img = document.getElementById('film');
  if (!img) { img = document.createElement('img'); img.id = 'film'; img.style.cssText = 'position:fixed;left:0;top:0;width:100%;z-index:99;background:#fff'; document.body.appendChild(img); }
  img.src = sheet.toDataURL();
  await new Promise((r) => setTimeout(r, 1500));
  return 'film ' + n;
};
