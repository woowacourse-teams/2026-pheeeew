// 효과음. 소리 파일 없이 브라우저의 Web Audio 로 그때그때 만든다.
// 브라우저 규칙 때문에 첫 키 입력 뒤에야 소리가 난다(unlock).
let ctx = null;
let master = null;
let muted = localStorage.getItem('pheeeew-climb-mute') === '1';
let hum = null; // 힘을 모으는 동안 이어지는 소리
const VOLUME = 0.4; // 효과음 전체 음량(음소거를 풀면 이 값으로 돌아간다)

export function unlock() {
  if (!ctx) {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    ctx = new AC();
    master = ctx.createGain();
    master.gain.value = muted ? 0 : VOLUME;
    master.connect(ctx.destination);
  }
  if (ctx.state === 'suspended') ctx.resume();
}

export const isMuted = () => muted;
export const audioCtx = () => ctx; // 배경음악(bgm.js)도 같은 AudioContext 를 쓴다
export function toggleMute() {
  muted = !muted;
  localStorage.setItem('pheeeew-climb-mute', muted ? '1' : '0');
  if (master) master.gain.value = muted ? 0 : VOLUME;
  return muted;
}

// 높이가 f0 에서 f1 로 미끄러지는 짧은 음
function tone(f0, f1, dur, { type = 'sine', vol = 0.3, delay = 0 } = {}) {
  if (!ctx) return;
  const t = ctx.currentTime + delay;
  const o = ctx.createOscillator();
  const v = ctx.createGain();
  o.type = type;
  o.frequency.setValueAtTime(f0, t);
  o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t + dur);
  v.gain.setValueAtTime(0.0001, t);
  v.gain.exponentialRampToValueAtTime(vol, t + 0.008);
  v.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(v).connect(master);
  o.start(t);
  o.stop(t + dur + 0.02);
}

// 걸러 낸 잡음: 쿵, 철퍼덕, 푸슉
function noise(dur, cutoff, vol = 0.3) {
  if (!ctx) return;
  const t = ctx.currentTime;
  const buf = ctx.createBuffer(1, Math.ceil(ctx.sampleRate * dur), ctx.sampleRate);
  const d = buf.getChannelData(0);
  for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / d.length);
  const s = ctx.createBufferSource();
  s.buffer = buf;
  const f = ctx.createBiquadFilter();
  f.type = 'lowpass';
  f.frequency.value = cutoff;
  const v = ctx.createGain();
  v.gain.value = vol;
  s.connect(f).connect(v).connect(master);
  s.start(t);
}

export const sfx = {
  // c = 모은 정도 0~1
  jump(c) { tone(240 + c * 160, 520 + c * 620, 0.17, { type: 'triangle', vol: 0.3 }); },
  // v = 떨어진 세기 0~1
  land(v) { noise(0.07 + v * 0.06, 260 + v * 300, 0.18 + v * 0.3); tone(130, 70, 0.09, { vol: 0.12 + v * 0.2 }); },
  // 분사 판에서 뛸 때: 보통 점프보다 길게 치솟는 소리와 푸슉. c = 모은 정도 0~1
  boost(c) {
    tone(200 + c * 120, 1300 + c * 700, 0.45, { type: 'triangle', vol: 0.28 });
    tone(400, 2400, 0.38, { type: 'sine', vol: 0.08, delay: 0.04 });
    noise(0.32, 2600, 0.2);
  },
  bounce() { tone(330, 170, 0.1, { type: 'square', vol: 0.12 }); },
  bump() { tone(210, 110, 0.09, { type: 'square', vol: 0.12 }); },
  splat() { noise(0.28, 700, 0.5); tone(170, 45, 0.32, { type: 'triangle', vol: 0.35 }); },
  slide() { noise(0.09, 1800, 0.07); },
  nod() { tone(520, 150, 0.24, { type: 'sine', vol: 0.25 }); noise(0.12, 900, 0.12); },
  full() { tone(880, 1320, 0.09, { type: 'triangle', vol: 0.16 }); },
  blip() { tone(620, 760, 0.05, { type: 'triangle', vol: 0.16 }); },
  start() { [523, 659, 784].forEach((f, i) => tone(f, f, 0.12, { type: 'triangle', vol: 0.2, delay: i * 0.07 })); },
  clear() { [523, 659, 784, 1047, 1319].forEach((f, i) => tone(f, f, 0.2, { type: 'triangle', vol: 0.24, delay: i * 0.1 })); },
  // 힘을 모으는 동안: 모을수록 높아지는 소리. c < 0 이면 끈다
  charge(c) {
    if (!ctx) return;
    if (c < 0) {
      if (hum) { hum.v.gain.setTargetAtTime(0.0001, ctx.currentTime, 0.02); hum.o.stop(ctx.currentTime + 0.1); hum = null; }
      return;
    }
    if (!hum) {
      const o = ctx.createOscillator();
      const v = ctx.createGain();
      o.type = 'triangle';
      v.gain.value = 0.0001;
      o.connect(v).connect(master);
      o.start();
      hum = { o, v };
    }
    hum.o.frequency.setTargetAtTime(170 + c * 430, ctx.currentTime, 0.02);
    hum.v.gain.setTargetAtTime(0.05 + c * 0.07, ctx.currentTime, 0.03);
  },
};
