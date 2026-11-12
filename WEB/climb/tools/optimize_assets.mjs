// 원본 그림(assets_src, PNG)을 게임용 그림(public/assets, WebP)으로 만든다. 끝나면 목록표도 다시 만든다.
// 필요한 것: macOS sips, cwebp(brew install webp). 사용: node tools/optimize_assets.mjs
//  - 감정이 그림: 1024×1024 → 384×384(화면 폭 약 75px, 고해상도 화면 2배를 넉넉히), 품질 95, 투명도는 무손실.
//    코드는 1024 논리 좌표를 그대로 쓴다(art.js 의 charFiles 가 해상도 384/1024 를 준다).
//  - 구간 그림: 픽셀 크기 그대로. 배경(bg)은 품질 82, 나머지(재질·장식·표지·발판)는 품질 90에 투명도 무손실.
//  - 게임이 쓰는 구간 폴더(level.js 의 ZONES[].art)만 만든다. 예전 폴더(zone1, zone2, props)는 원본에만 남긴다.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { ZONES } from '../public/level.js';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = path.join(ROOT, 'assets_src');
const OUT = path.join(ROOT, 'public', 'assets');
const CHAR_SIZE = 384;
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'climb-art-'));

const pngs = (dir) => fs.readdirSync(dir, { recursive: true }).filter((f) => f.endsWith('.png')).sort();
const webp = (src, out, args) => {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  execFileSync('cwebp', ['-quiet', '-metadata', 'none', ...args, src, '-o', out]);
};

let n = 0;
for (const dir of ['characters', ...ZONES.map((z) => z.art)]) {
  fs.rmSync(path.join(OUT, dir), { recursive: true, force: true }); // 지운 원본이 남지 않게 통째로 다시 만든다
  for (const f of pngs(path.join(SRC, dir))) {
    const src = path.join(SRC, dir, f);
    const out = path.join(OUT, dir, f.replace(/\.png$/, '.webp'));
    if (dir === 'characters') {
      const small = path.join(TMP, `${n}.png`);
      execFileSync('sips', ['-z', String(CHAR_SIZE), String(CHAR_SIZE), src, '--out', small], { stdio: 'ignore' });
      webp(small, out, ['-q', '95', '-alpha_q', '100']);
    } else webp(src, out, path.basename(f) === 'bg.png' ? ['-q', '82'] : ['-q', '90', '-alpha_q', '100']);
    n++;
  }
}
fs.rmSync(TMP, { recursive: true, force: true });
console.log(`그림 ${n}장을 만들었다`);
execFileSync(process.execPath, [path.join(ROOT, 'tools', 'build_manifest.mjs')], { stdio: 'inherit' });
