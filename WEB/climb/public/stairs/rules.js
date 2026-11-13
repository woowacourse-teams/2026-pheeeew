// 정수 규칙 v2. BE/climb/README.md 표 및 Java StairsRules와 함께 변경한다.
export const MAX_INPUTS = 5000;
export const MAX_TIME_MS = 600000;
export const GAUGE_GAIN = 400000;
export const MIN_INTERVAL_MS = 45;
export const MAX_FAST_STREAK = 3;
export const drainRate = (height) => 1000 + height * 6;
// 높이 | 최대 버팀 시간(ms). 양수 가중합을 나눠 JS·Java 모두 정수 내림한다.
const CAP_MS_CURVE = [[0, 6000], [300, 2000], [600, 1200]];
export function capMs(height) {
  for (let i = 1; i < CAP_MS_CURVE.length; i++) {
    const [h0, ms0] = CAP_MS_CURVE[i - 1], [h1, ms1] = CAP_MS_CURVE[i];
    if (height <= h1) return Math.floor((ms0 * (h1 - height) + ms1 * (height - h0)) / (h1 - h0));
  }
  return CAP_MS_CURVE.at(-1)[1];
}
export const cap = (height) => capMs(height) * drainRate(height);
export const remaining = (gauge, height, elapsed) => gauge - elapsed * drainRate(height);
// height는 성공 후 오른 높이.
export const replenish = (gauge, height) => Math.min(cap(height), gauge + GAUGE_GAIN);

// 생성기 상수표 (Java와 동일)
// 연습 | 10칸, 5칸씩   일반 묶음 | 1~6칸
// 함정 선택률 | 10칸 10% → 50칸 40% → 800칸 60% (정수 내림)
// 함정 묶음 | (3~4,1,3~4), (2,1,3~6), (1,1,3~6)
// 제한 | 1칸 묶음 최대 2연속 (= 번갈아 최대 4칸), 같은 길이 최대 3연속
const PRACTICE = 10, RUN_MAX = 6, SINGLE_MAX = 2, RHYTHM_MAX = 3;
const HASH_MULTIPLIER = 0x9e3779b9;
const TRAP_EARLY = 10, TRAP_RAMP = 30, RAMP_STEPS = 40;
const TRAP_LATE = 20, LATE_STEPS = 750;

// 호출 순서와 무관한 32비트 정수 생성기. 묶음 경계에서도 제한을 적용한다.
export function direction(seed, index) {
  if (!Number.isInteger(seed) || !Number.isInteger(index) || index < 0) throw new Error('INVALID_INDEX');
  if (index < PRACTICE) return ((Math.floor(index / 5) ^ seed) & 1) ? 'R' : 'L';
  let position = PRACTICE, run = 0, previous = 5, repeated = 2, singles = 0;
  let pending = [], side = seed & 1;
  while (true) {
    let x = (seed ^ Math.imul(run + 1, HASH_MULTIPLIER)) | 0;
    x ^= x << 13; x ^= x >>> 17; x ^= x << 5;
    const u = x >>> 0;
    if (!pending.length) {
      const chance = TRAP_EARLY + Math.floor(Math.min(RAMP_STEPS, position - PRACTICE) * TRAP_RAMP / RAMP_STEPS)
        + Math.floor(Math.min(LATE_STEPS, Math.max(0, position - 50)) * TRAP_LATE / LATE_STEPS);
      if (u % 100 < chance) {
        const kind = (u >>> 8) % 3;
        pending = kind === 0 ? [3 + ((u >>> 12) % 2), 1, 3 + ((u >>> 16) % 2)]
          : kind === 1 ? [2, 1, 3 + ((u >>> 16) % 4)] : [1, 1, 3 + ((u >>> 16) % 4)];
      }
    }
    let length = pending.length ? pending.shift() : 1 + ((u >>> 16) % RUN_MAX);
    if (length === 1 && singles >= SINGLE_MAX) length = 2 + ((u >>> 20) % (RUN_MAX - 1));
    if (length === previous && repeated >= RHYTHM_MAX) length = 1 + length % RUN_MAX;
    repeated = length === previous ? repeated + 1 : 1;
    singles = length === 1 ? singles + 1 : 0;
    previous = length;
    if (index < position + length) return side ? 'R' : 'L';
    position += length; side ^= 1; run++;
  }
}

export function replay(seed, inputs) {
  if (!Array.isArray(inputs) || !inputs.length || inputs.length > MAX_INPUTS) throw new Error('INVALID_INPUTS');
  let height = 0, timeMs = 0, previous = 0, gauge = cap(0), fast = 0;
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
    height++; timeMs = input.t; previous = input.t; gauge = replenish(gauge, height);
  }
  return { height, timeMs };
}
