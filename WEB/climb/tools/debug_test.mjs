// 로컬 디버그 허용 조건과 실제 진입 모듈의 연결을 확인한다.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { isDebugEnabled } from '../public/debug.js';

let checks = 0;
const main = fs.readFileSync(new URL('../public/main.js', import.meta.url), 'utf8');
const initialization = main.match(/const DEBUG = ([^\n;]+);/)[1];
for (const [hostname, local] of [
  ['localhost', true], ['127.0.0.1', true], ['[::1]', true],
  ['climb.pheeeew.com', false], ['climb-dev.pheeeew.com', false],
  ['pheeeew-climb.pages.dev', false], ['preview.pages.dev', false],
  ['localhost.example.com', false], ['127.0.0.1.example.com', false],
  ['example.com', false],
]) {
  for (const search of ['', '?debug', '?debug=false']) {
    assert.equal(isDebugEnabled({ hostname, search }), local && search !== '', hostname + search);
    assert.equal(vm.runInNewContext(initialization, {
      location: { hostname, search }, isDebugEnabled, URLSearchParams,
    }), local && search !== '', 'main.js: ' + hostname + search);
    checks++;
  }
}

// PixiJS 초기화 없이 실제 main.js의 모드·노출·일시정지 조건을 실행한다.
const exposed = main.match(/if \(([^\n]+)\) window\.game =/)[1];
const running = main.match(/if \(([^\n]+)\) frame\(Math\.min/)[1];
for (const DEBUG of [false, true]) {
  for (const ADMIN of [false, true]) {
    assert.equal(vm.runInNewContext(exposed, { DEBUG, ADMIN }), DEBUG || ADMIN);
    for (const paused of [false, true]) {
      assert.equal(vm.runInNewContext(running, { DEBUG, ADMIN, window: { paused } }),
        !((DEBUG || ADMIN) && paused));
      checks++;
    }
  }
}
console.log('debug checks passed:', checks);
