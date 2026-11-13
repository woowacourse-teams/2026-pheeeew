// 정수 규칙 v1. BE/climb/README.md 표 및 Java StairsRules와 함께 변경한다.
export const MAX_INPUTS = 5000;
export const MAX_TIME_MS = 600000;
export const GAUGE_MAX = 6000000;
export const GAUGE_GAIN = 400000;
export const MIN_INTERVAL_MS = 45;
export const MAX_FAST_STREAK = 3;
export const drainRate = (height) => 1000 + height * 6;
export const remaining = (gauge, height, elapsed) => gauge - elapsed * drainRate(height);
export const replenish = (gauge) => Math.min(GAUGE_MAX, gauge + GAUGE_GAIN);

// 32비트 xorshift. JS >>>와 Java >>>, Math.imul과 Java int 곱셈의 오버플로가 같다.
export function direction(seed, index) {
  if (!Number.isInteger(seed) || !Number.isInteger(index) || index < 0) throw new Error('INVALID_INDEX');
  if (index < 10) return ((Math.floor(index / 5) ^ seed) & 1) ? 'R' : 'L';
  const block = Math.floor((index - 10) / 16), offset = (index - 10) % 16;
  let x = (seed ^ Math.imul(block + 1, 0x9e3779b9)) | 0;
  x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
  const u = x >>> 0, level = Math.min(3, Math.floor((10 + block * 16) / 160));
  const mode = u % 100, base = (u >>> 7) & 1;
  let flip;
  if (mode < 35) flip = offset & 1; // 왼오왼오
  else if (mode < 80 - level * 5) {
    const length = Math.max(1, 4 - level - ((u >>> 9) & 1));
    flip = Math.floor(offset / length) & 1; // 불규칙 묶음
  } else {
    flip = offset === 7 || (level >= 1 && offset === 12) || (level >= 3 && offset === 3) ? 1 : 0;
  }
  return (base ^ flip) ? 'R' : 'L';
}

export function replay(seed, inputs) {
  if (!Array.isArray(inputs) || !inputs.length || inputs.length > MAX_INPUTS) throw new Error('INVALID_INPUTS');
  let height = 0, timeMs = 0, previous = 0, gauge = GAUGE_MAX, fast = 0;
  for (let i = 0; i < inputs.length; i++) {
    const input = inputs[i];
    if (!input || !['L', 'R'].includes(input.d) || !Number.isInteger(input.t) || input.t < 0 || input.t > MAX_TIME_MS
      || (i === 0 ? input.t !== 0 : input.t <= previous)) throw new Error('INVALID_INPUTS');
    const delta = input.t - previous;
    fast = i > 0 && delta < MIN_INTERVAL_MS ? fast + 1 : 0;
    if (fast >= MAX_FAST_STREAK) throw new Error('TOO_FAST');
    gauge = remaining(gauge, height, delta);
    if (gauge <= 0) throw new Error('GAUGE_EXPIRED');
    if (input.d !== direction(seed, height)) break;
    height++; timeMs = input.t; previous = input.t; gauge = replenish(gauge);
  }
  return { height, timeMs };
}
