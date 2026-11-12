// 그림 목록표를 만든다: public/assets 를 읽어 public/assets/manifest.json 을 쓴다.
// 게임은 이 목록으로 받을 그림을 정한다(있는지 서버에 묻지 않는다). 그림을 더하거나 빼면 다시 돌린다.
// 주소에는 내용 해시(?v=)를 붙여, 같은 이름으로 그림만 바뀌어도 7일 캐시가 옛 그림을 보이지 않게 한다.
// 사용: node tools/build_manifest.mjs   시험: node tools/manifest_test.mjs
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const IMAGE = /\.(png|webp)$/;
const NOT_ZONES = ['characters', 'fonts', 'bgm'];

// PNG(IHDR)·WebP(VP8X, VP8L, VP8) 머리에서 픽셀 크기
export function imageSize(buf) {
  if (buf.toString('latin1', 1, 4) === 'PNG') return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  const chunk = buf.toString('latin1', 12, 16);
  if (chunk === 'VP8X') return { w: buf.readUIntLE(24, 3) + 1, h: buf.readUIntLE(27, 3) + 1 };
  if (chunk === 'VP8L') { const b = buf.readUInt32LE(21); return { w: (b & 0x3fff) + 1, h: ((b >>> 14) & 0x3fff) + 1 }; }
  if (chunk === 'VP8 ') return { w: buf.readUInt16LE(26) & 0x3fff, h: buf.readUInt16LE(28) & 0x3fff };
  throw new Error('모르는 그림 형식');
}

function entry(assets, rel) {
  const buf = fs.readFileSync(path.join(assets, rel));
  return { src: `${rel}?v=${crypto.createHash('sha1').update(buf).digest('hex').slice(0, 8)}`, ...imageSize(buf) };
}
const images = (dir) => (fs.existsSync(dir) ? fs.readdirSync(dir).filter((f) => IMAGE.test(f)).sort() : []);
const stem = (f) => f.replace(IMAGE, '');
const dirs = (dir) => fs.readdirSync(dir, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => d.name).sort();

// zones[폴더] = { 이름: {src, w, h}, kit: { top: [...번호순] } }, characters[감정이] = { 부위: {src, w, h} }
export function buildManifest(assets) {
  const zones = {};
  for (const z of dirs(assets).filter((d) => !NOT_ZONES.includes(d))) {
    const art = {};
    for (const f of images(path.join(assets, z))) art[stem(f)] = entry(assets, `${z}/${f}`);
    const kit = {};
    for (const f of images(path.join(assets, z, 'kit'))) {
      const [, kind, n] = stem(f).match(/^(.+)_(\d+)$/);
      (kit[kind] ??= [])[Number(n) - 1] = entry(assets, `${z}/kit/${f}`);
    }
    for (const [kind, list] of Object.entries(kit)) if (list.includes(undefined) || list.length !== Object.keys(list).length) throw new Error(`${z}/kit/${kind}_N 번호가 비었다`);
    art.kit = kit;
    zones[z] = art;
  }
  const characters = {};
  for (const id of dirs(path.join(assets, 'characters'))) {
    characters[id] = {};
    for (const f of images(path.join(assets, 'characters', id))) characters[id][stem(f)] = entry(assets, `characters/${id}/${f}`);
  }
  return { zones, characters };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const assets = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public', 'assets');
  const m = buildManifest(assets);
  fs.writeFileSync(path.join(assets, 'manifest.json'), `${JSON.stringify(m)}\n`);
  console.log(`manifest.json: 구간 ${Object.keys(m.zones).length}개, 감정이 ${Object.keys(m.characters).length}명`);
}
