// 피유 등반 정적 서버. public/ 폴더만 127.0.0.1 에서 내보낸다.
// 개발용: /api/v1/climb(웹소켓 포함)은 백엔드(CLIMB_BACKEND, 기본 http://127.0.0.1:8080)로 넘겨 같은 출처로 만든다.
// 손님 쿠키가 Path=/api/v1/climb 이라 같은 출처여야 로컬에서 그대로 붙는다.
import http from 'node:http';
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), 'public');
const PORT = Number(process.env.PORT ?? 4320);
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript', '.png': 'image/png', '.json': 'application/json', '.ttf': 'font/ttf', '.css': 'text/css; charset=utf-8' };

const BACKEND = new URL(process.env.CLIMB_BACKEND ?? 'http://127.0.0.1:8080');
const API = '/api/v1/climb';
const isApi = (url) => url === API || url.startsWith(`${API}/`) || url.startsWith(`${API}?`);

const server = http.createServer((req, res) => {
  if (isApi(req.url)) {
    const up = http.request({ host: BACKEND.hostname, port: BACKEND.port, method: req.method, path: req.url, headers: req.headers }, (r) => {
      res.writeHead(r.statusCode, r.headers);
      r.pipe(res);
    });
    up.on('error', () => {
      if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ code: 'BACKEND_UNREACHABLE', message: `백엔드(${BACKEND.origin})에 닿지 않는다.` }));
    });
    req.pipe(up);
    return;
  }
  const p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const file = path.join(ROOT, p === '/' ? 'index.html' : p);
  if (!file.startsWith(ROOT)) { res.writeHead(403).end(); return; }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404).end('not found'); return; }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] ?? 'application/octet-stream', 'Cache-Control': 'no-store' });
    res.end(data);
  });
});
// 웹소켓 업그레이드: 요청 머리를 그대로 백엔드에 쓰고 양쪽을 잇는다
server.on('upgrade', (req, sock, head) => {
  if (!isApi(req.url)) { sock.destroy(); return; }
  const up = net.connect(Number(BACKEND.port || 80), BACKEND.hostname, () => {
    let text = `${req.method} ${req.url} HTTP/1.1\r\n`;
    for (let i = 0; i < req.rawHeaders.length; i += 2) text += `${req.rawHeaders[i]}: ${req.rawHeaders[i + 1]}\r\n`;
    up.write(`${text}\r\n`);
    if (head.length) up.write(head);
    up.pipe(sock);
    sock.pipe(up);
  });
  up.on('error', () => sock.destroy());
  sock.on('error', () => up.destroy());
});
server.listen(PORT, '127.0.0.1', () => console.log(`피유 등반: http://127.0.0.1:${PORT}`));
