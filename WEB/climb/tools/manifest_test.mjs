// 그림 목록표(public/assets/manifest.json) 시험. 목록과 실제 폴더가 어긋나면 실패한다.
// 그림을 더하거나 뺐다면: node tools/build_manifest.mjs
// 사용: node tools/manifest_test.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildManifest, imageSize } from './build_manifest.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC = path.join(HERE, '..', 'public');
const ASSETS = path.join(PUBLIC, 'assets');
const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const eq = (a, b, what) => {
  const A = JSON.stringify(a);
  const B = JSON.stringify(b);
  if (A !== B) throw new Error(`${what}: ${A.slice(0, 300)} !== ${B.slice(0, 300)}`);
};

// 1×1 PNG(IHDR 폭 3, 높이 2)와 WebP 세 형식의 머리만 만든다
const png = Buffer.alloc(33);
png.write('\x89PNG\r\n\x1a\n', 0, 'latin1');
png.writeUInt32BE(3, 16);
png.writeUInt32BE(2, 20);
const riff = (chunk, body) => Buffer.concat([Buffer.from('RIFF'), Buffer.alloc(4), Buffer.from('WEBP'), Buffer.from(chunk), Buffer.alloc(4), body]);
const vp8x = Buffer.alloc(10);
vp8x.writeUIntLE(1024 - 1, 4, 3);
vp8x.writeUIntLE(1536 - 1, 7, 3);
const vp8l = Buffer.alloc(5);
vp8l[0] = 0x2f;
vp8l.writeUInt32LE(((384 - 1) | ((200 - 1) << 14)) >>> 0, 1);
const vp8 = Buffer.alloc(10);
vp8.writeUInt16LE(512, 6);
vp8.writeUInt16LE(160, 8);

test('PNG·WebP(VP8X, VP8L, VP8) 머리에서 픽셀 크기를 읽는다', () => {
  eq(imageSize(png), { w: 3, h: 2 }, 'PNG');
  eq(imageSize(riff('VP8X', vp8x)), { w: 1024, h: 1536 }, 'VP8X');
  eq(imageSize(riff('VP8L', vp8l)), { w: 384, h: 200 }, 'VP8L');
  eq(imageSize(riff('VP8 ', vp8)), { w: 512, h: 160 }, 'VP8');
});

const committed = JSON.parse(fs.readFileSync(path.join(ASSETS, 'manifest.json'), 'utf8'));

test('커밋한 목록표가 지금 폴더에서 만든 것과 같다(다르면 node tools/build_manifest.mjs)', () => {
  eq(committed, buildManifest(ASSETS), 'manifest.json');
});

test('목록의 모든 파일이 실제로 있고, 주소에 내용 버전(?v=)이 붙어 있다', () => {
  const entries = [];
  for (const z of Object.values(committed.zones)) {
    for (const [k, v] of Object.entries(z)) if (k === 'kit') for (const list of Object.values(v)) entries.push(...list); else entries.push(v);
  }
  for (const c of Object.values(committed.characters)) entries.push(...Object.values(c));
  if (entries.length < 100) throw new Error(`목록이 너무 적다: ${entries.length}`);
  for (const e of entries) {
    const [file, v] = e.src.split('?v=');
    if (!fs.existsSync(path.join(ASSETS, file))) throw new Error(`없는 파일: ${file}`);
    if (!/^[0-9a-f]{8}$/.test(v ?? '')) throw new Error(`버전 없음: ${e.src}`);
  }
});

test('게임이 쓰는 구간 폴더와 감정이가 모두 목록에 있다', async () => {
  const { ZONES } = await import('../public/level.js');
  const { IDS } = await import('../public/physics.js');
  const PARTS = JSON.parse(fs.readFileSync(path.join(PUBLIC, 'rig.js'), 'utf8').match(/export const PARTS = (\[[^\]]+\])/)[1].replace(/'/g, '"')); // rig.js 는 pixi 를 읽어 Node 에서 import 하지 않는다
  for (const z of ZONES) for (const k of ['bg', 'rock', 'fabric', 'trim', 'arrow_sign']) if (!committed.zones[z.art]?.[k]) throw new Error(`${z.art}/${k} 없음`);
  for (const id of IDS) for (const p of ['front', ...PARTS]) if (!committed.characters[id]?.[p]) throw new Error(`${id}/${p} 없음`);
});

test('index.html 이 미리 받는 파일과 ui.js 의 얼굴 그림이 실제로 있다', () => {
  const html = fs.readFileSync(path.join(PUBLIC, 'index.html'), 'utf8');
  const hrefs = [...html.matchAll(/rel="preload"[^>]*href="([^"]+)"/g)].map((m) => m[1].split('?')[0]);
  for (const h of hrefs) if (!fs.existsSync(path.join(PUBLIC, h))) throw new Error(`preload 파일 없음: ${h}`);
  const ui = fs.readFileSync(path.join(PUBLIC, 'ui.js'), 'utf8');
  const face = ui.match(/\/assets\/characters\/\$\{id\}\/(front\.[a-z]+)/)[1];
  for (const id of Object.keys(committed.characters)) if (!fs.existsSync(path.join(ASSETS, 'characters', id, face))) throw new Error(`얼굴 없음: ${id}/${face}`);
});

let fail = 0;
for (const t of tests) {
  try { await t.fn(); console.log(`ok   ${t.name}`); } catch (e) { fail++; console.log(`FAIL ${t.name}\n     ${e.message}`); }
}
console.log(fail ? `${fail}개 실패` : `${tests.length}개 모두 통과`);
if (fail) process.exit(1);
