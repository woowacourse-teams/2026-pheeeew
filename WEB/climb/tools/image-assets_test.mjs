import assert from 'node:assert/strict';
import { hasImage } from '../public/image-assets.js';
const cases = [
  ['PNG', true, 'image/png', true],
  ['WebP', true, 'image/webp', true],
  ['404 이미지', false, 'image/png', false],
  ['Pages HTML 200', true, 'text/html; charset=utf-8', false],
  ['형식 없는 응답', true, null, false],
];
for (const [name, ok, type, expected] of cases) {
  const result = await hasImage('/optional.png', async (url, options) => {
    assert.equal(url, '/optional.png');
    assert.equal(options.method, 'HEAD');
    assert.equal(options.cache, 'no-cache');
    return { ok, headers: { get: () => type } };
  });
  assert.equal(result, expected, name);
  console.log(`통과  ${name}`);
}
assert.equal(await hasImage('/optional.png', async () => { throw new TypeError('network'); }), false);
console.log('통과  네트워크 실패');
console.log('모두 통과 (6개)');
