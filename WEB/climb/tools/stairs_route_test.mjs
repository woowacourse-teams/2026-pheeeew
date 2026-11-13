// 포트를 열지 않고 실제 server.mjs의 요청 핸들러에 정적 경로를 전달한다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
const serverURL = new URL('../server.mjs', import.meta.url);
let handler;
const source = fs.readFileSync(serverURL, 'utf8').replace(/^import .*;\n/gm, '').replaceAll('import.meta.url', JSON.stringify(serverURL.href));
vm.runInNewContext(source, {
  http: { createServer(fn) { handler = fn; return { on() {}, listen() {} }; } },
  net: {}, fs, path, fileURLToPath, URL, process: { env: {} }, console: { log() {} },
});
for (const url of ['/stairs', '/stairs/', '/stairs.html', '/stairs?event=today']) {
  const response = await new Promise(resolve => {
    let status, headers;
    handler({ url }, {
      writeHead(s, h) { status = s; headers = h; return this; },
      end(body) { resolve({ status, headers, body: String(body) }); },
    });
  });
  assert.equal(response.status, 200, url);
  assert.match(response.headers['Content-Type'], /text\/html/);
  assert.match(response.body, /감정이 계단 러시/);
  assert.match(response.body, /user-scalable=no/);
}
const headers = fs.readFileSync(new URL('../public/_headers', import.meta.url), 'utf8');
for (const route of ['/stairs', '/stairs/', '/stairs.html', '/stairs/*']) assert.ok(headers.includes(`${route}\n  Cache-Control: private, no-cache`));
const redirects = fs.readFileSync(new URL('../public/_redirects', import.meta.url), 'utf8');
assert.match(redirects, /\/stairs\s+\/stairs.html\s+200/);
console.log('stairs routes: PASS /stairs, /stairs/, query, HTML and Pages cache/rewrite');
