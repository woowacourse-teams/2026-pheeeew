// 배경음악. 효과음(sfx.js)과 같은 AudioContext 를 쓰고, 첫 키 입력·클릭(unlock) 뒤에 시작한다.
// 화면 상태와 캐릭터 높이로 곡을 고르고, 바뀌면 2초에 걸쳐 서로 넘긴다. 시험: tools/bgm_test.mjs
// 출처 표시(CC BY 4.0)는 assets/bgm/CREDITS.md 와 제목 화면의 "음악" 창에 있다.

// vol = 곡마다 맞춘 음량(파일마다 크기가 달라 RMS 약 -21dB 로 맞춤).
// loop: 'trim' = 앞뒤 무음을 잘라 낸 구간을 그대로 반복(루프용으로 만든 곡),
//       'cross' = 끝나기 cross 초 전에 처음부터 다시 틀어 겹쳐 넘긴다(일반 완곡)
export const TRACKS = {
  title: { src: '/assets/bgm/title.mp3', vol: 0.48, loop: 'trim' },
  nyc: { src: '/assets/bgm/nyc.m4a', vol: 0.45, loop: 'trim' },
  castle: { src: '/assets/bgm/castle.mp3', vol: 1.58, loop: 'trim' },
  balloon: { src: '/assets/bgm/balloon.m4a', vol: 1.6, loop: 'cross', cross: 3 },
  space: { src: '/assets/bgm/space.mp3', vol: 0.48, loop: 'cross', cross: 3 },
  moon: { src: '/assets/bgm/moon.mp3', vol: 1.0, loop: 'cross', cross: 3 },
};
// 구간 순서대로. null = 아직 곡이 없음(효과음만). 구간 2 크리스마스 곡이 정해지면 TRACKS 에 넣고 여기 이름을 쓴다.
export const ZONE_TRACKS = ['nyc', null, 'castle', 'balloon', 'space', 'moon'];
export const VOLUME = 0.55; // 배경음악 전체 음량. 효과음 전체 음량은 sfx.js 의 VOLUME
const FADE = 2; // 곡이 바뀔 때 넘기는 시간(초)
const LAG = 3; // 경계에서 이만큼(칸) 넘어가야 곡을 바꾼다
const AHEAD = 6; // 다음 구간 경계까지 이만큼(칸) 남으면 다음 곡을 미리 받는다
const KEY = 'pheeeew-climb-bgm';

// 출처 표시(CC BY 4.0). 게임 안 "음악" 창과 CREDITS.md·README 에 같은 내용
const BY = { name: 'CC BY 4.0', url: 'https://creativecommons.org/licenses/by/4.0/' };
export const CREDITS = [
  { where: '제목·고르기 화면', title: 'Clear Blue Sky', by: 'Alexander Nakarada', byUrl: 'https://creatorchords.com', src: 'https://creatorchords.com/music/clear-blue-sky/', license: BY, changes: '파일은 원본 그대로. 게임에서 음량을 맞추고, 앞뒤 무음을 건너뛰어 반복 재생' },
  { where: '구간 1 뉴욕 빌딩 숲', title: 'Bending Air 78 BPM Loop', by: 'Josh Lim', credit: 'Music by Josh Lim', byUrl: 'https://www.joshjameslim.com', from: 'cozy cats - lofi beat loops to game to', src: 'https://joshjameslim.itch.io/cozy-cats-lofi-beat-loops-to-game-to', license: BY, changes: '웹용 AAC(192kbps)로 변환. 게임에서 음량을 맞추고, 앞뒤 무음을 건너뛰어 반복 재생' },
  { where: '구간 3 구름 위 성', title: 'Tranquil Fields (Peaceful - Loop Ready)', by: 'Alexander Nakarada', byUrl: 'https://creatorchords.com', src: 'https://creatorchords.com/music/tranquil-fields-peaceful-loop-ready/', license: BY, changes: '파일은 원본 그대로. 게임에서 음량을 맞추고, 앞뒤 무음을 건너뛰어 반복 재생' },
  { where: '구간 4 열기구 축제 하늘', title: 'With Me', by: 'Sabine Bloch x Fachhochschule Dortmund', from: 'OER Tracks', src: 'https://www.orca.nrw/oer/oer-erstellen/oer-tracks/', license: BY, changes: '웹용 AAC(192kbps)로 변환. 게임에서 음량을 맞추고, 끝 3초를 처음과 겹쳐 반복 재생' },
  { where: '구간 5 로켓 발사대와 우주', title: 'Space Ambience', by: 'Alexander Nakarada', byUrl: 'https://creatorchords.com', src: 'https://creatorchords.com/music/space-ambience/', license: BY, changes: '파일은 원본 그대로. 게임에서 음량을 맞추고, 앞뒤 무음을 건너뛰고 끝 3초를 처음과 겹쳐 반복 재생' },
  { where: '구간 6 달', title: 'Moon', by: 'Alexander Nakarada', byUrl: 'https://creatorchords.com', src: 'https://creatorchords.com/music/moon/', license: BY, changes: '파일은 원본 그대로. 게임에서 음량을 맞추고, 앞뒤 무음을 건너뛰고 끝 3초를 처음과 겹쳐 반복 재생' },
];

const zoneAt = (h, zones) => Math.max(0, zones.findIndex((z, i) => h < z.to || i === zones.length - 1));
// 지금 구간(prev)에서 위아래로 LAG 칸 안이면 그대로 둔다
export function zoneWithLag(h, zones, prev, lag = LAG) {
  if (prev != null && zones[prev] && h >= zones[prev].from - lag && h < zones[prev].to + lag) return prev;
  return zoneAt(h, zones);
}
export const pickTrack = (screen, zone) => (screen === 'play' || screen === 'confirm' ? ZONE_TRACKS[zone] ?? null : 'title');

// 모든 채널이 문턱 아래인 앞뒤를 잘라 낸 [start, end) (샘플 번호). 전부 무음이면 전체.
export function loopPoints(channels, threshold = 0.001) {
  const n = channels[0].length;
  const loud = (i) => channels.some((c) => Math.abs(c[i]) > threshold);
  let start = 0;
  while (start < n && !loud(start)) start++;
  if (start === n) return { start: 0, end: n };
  let end = n;
  while (end > start && !loud(end - 1)) end--;
  return { start, end };
}

// getCtx() = 지금의 AudioContext(없으면 null), load(url) = ArrayBuffer, muted() = 전체 음소거(M)
export function createBgm({ getCtx, load = (url) => fetch(url).then((r) => { if (!r.ok) throw new Error(r.status); return r.arrayBuffer(); }), muted, zones, storage = globalThis.localStorage }) {
  let off = storage?.getItem(KEY) === 'off';
  let ctx = null;
  let bus = null;
  let zone = null;
  let want = null; // 틀어야 할 곡
  let playing = null; // { key, gain, sources, nextAt, ... }
  const cache = new Map(); // key -> Promise<{ buf, start, end } | null>

  function get(key) {
    if (!cache.has(key)) {
      cache.set(key, (async () => {
        const buf = await ctx.decodeAudioData(await load(TRACKS[key].src));
        const chans = Array.from({ length: buf.numberOfChannels }, (_, i) => buf.getChannelData(i));
        const p = loopPoints(chans);
        return { buf, start: p.start / buf.sampleRate, end: p.end / buf.sampleRate };
      })().catch(() => null)); // 못 받거나 못 풀면 그 곡은 조용히 건너뛴다
    }
    return cache.get(key);
  }

  // 한 판 틀기. cross 곡은 판마다 앞뒤를 cross 초씩 줄이고 늘려 겹친다.
  function segment(t, at) {
    const { buf, start, end } = t.data;
    const s = ctx.createBufferSource();
    s.buffer = buf;
    const env = ctx.createGain();
    s.connect(env).connect(t.gain);
    const cfg = TRACKS[t.key];
    if (cfg.loop === 'trim') {
      s.loop = true;
      s.loopStart = start;
      s.loopEnd = end;
      s.start(at, start);
    } else {
      const x = cfg.cross;
      const len = end - start;
      env.gain.setValueAtTime(t.sources.length ? 0 : 1, at);
      env.gain.linearRampToValueAtTime(1, at + x);
      env.gain.setValueAtTime(1, at + len - x);
      env.gain.linearRampToValueAtTime(0, at + len);
      s.start(at, start, len);
      t.nextAt = at + len - x;
    }
    t.sources.push(s);
  }

  function fade(gain, to) {
    const now = ctx.currentTime;
    gain.gain.cancelScheduledValues(now);
    gain.gain.setValueAtTime(gain.gain.value, now);
    gain.gain.linearRampToValueAtTime(to, now + FADE);
  }

  function stop(t) {
    fade(t.gain, 0);
    const at = ctx.currentTime + FADE + 0.1;
    for (const s of t.sources) s.stop(at);
    // 다 줄어든 뒤 이음과 풀어 둔 소리(수십 MB)를 놓는다. 다시 그 곡이면 새로 받는다
    setTimeout(() => { t.gain.disconnect(); if (want !== t.key) cache.delete(t.key); }, (FADE + 0.2) * 1000);
  }

  async function start(key) {
    const data = await get(key);
    if (!data || want !== key || playing?.key === key) return;
    if (playing) stop(playing);
    const gain = ctx.createGain();
    gain.gain.value = 0;
    gain.connect(bus);
    playing = { key, gain, sources: [], data, nextAt: Infinity };
    segment(playing, ctx.currentTime);
    fade(gain, TRACKS[key].vol);
  }

  return {
    // 매 프레임 불러도 된다. screen = title | select | nickname | play | confirm, h = 발 높이(칸)
    update({ screen, h }) {
      if (!ctx) {
        ctx = getCtx();
        if (!ctx) return;
        bus = ctx.createGain();
        bus.connect(ctx.destination);
      }
      bus.gain.value = muted() || off ? 0 : VOLUME;
      const inPlay = screen === 'play' || screen === 'confirm';
      zone = inPlay ? zoneWithLag(h, zones, zone) : null;
      const key = pickTrack(screen, zone ?? 0);
      if (key !== want) {
        want = key;
        if (!key) { if (playing) stop(playing); playing = null; } else start(key);
      }
      if (inPlay && zones[zone + 1] && h > zones[zone].to - AHEAD) { const next = ZONE_TRACKS[zone + 1]; if (next) get(next); }
      if (playing && playing.nextAt - ctx.currentTime < 2) segment(playing, playing.nextAt);
    },
    // B: 배경음악만 끄고 켠다. true = 꺼짐
    toggle() {
      off = !off;
      storage?.setItem(KEY, off ? 'off' : 'on');
      return off;
    },
    // 탭이 숨겨지면 멈추고 돌아오면 이어서(효과음과 같은 AudioContext 라 함께 멈춘다)
    hidden(yes) {
      const c = ctx ?? getCtx();
      if (!c) return;
      (yes ? c.suspend() : c.resume())?.catch?.(() => {});
    },
    debug: () => ({ playing, bus, want, zone, cached: [...cache.keys()] }),
  };
}
