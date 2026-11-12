// 그림 받기 규칙(화면 없이 시험한다: tools/art_test.mjs). 그림 목록은 assets/manifest.json(tools/build_manifest.mjs).
// 처음에는 지금 구간과 바로 위 구간만 받고, 경계 AHEAD 칸 앞에서 그다음 구간을 미리 받는다.

export const AHEAD = 18; // 칸. 화면 하나 높이

// 칸의 윗면 높이로 어느 구간인지 정한다(경계 높이는 아래 구간)
export const zoneOf = (h, zones) => Math.max(0, zones.findIndex((z, i) => h <= z.to || i === zones.length - 1));

// 높이 h 에서 그림이 있어야 할 구간 번호들(오름차순)
export function zonesAround(h, zones, ahead = AHEAD) {
  const z = zoneOf(h, zones);
  const out = new Set([z, z + 1]);
  if (h >= zones[z].to - ahead) out.add(z + 2);
  if (h < zones[z].from + ahead) out.add(z - 1);
  return [...out].filter((i) => i >= 0 && i < zones.length).sort((a, b) => a - b);
}

const url = (e) => `/assets/${e.src}`;

// 구간 폴더의 그림 주소: { bg, rock, ..., kit: { top: [번호순] } }. 목록에 없는 그림은 키가 없다
export function zoneFiles(manifest, dir) {
  const { kit = {}, ...files } = manifest.zones[dir] ?? {};
  const urls = (o, f) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, f(v)]));
  return { ...urls(files, url), kit: urls(kit, (list) => list.map(url)) };
}

// 감정이 그림은 코드가 1024 캔버스(발 기준점 512,900) 좌표로 다룬다. 줄인 그림도 같은 크기로 보이게
// 해상도(실제 폭 / 1024)를 주면 텍스처 크기가 1024 로 잡힌다.
export const LOGICAL = 1024;
export function charFiles(manifest, id, names) {
  const files = manifest.characters[id] ?? {};
  return Object.fromEntries(names.filter((n) => files[n]).map((n) => [n, { src: url(files[n]), data: { resolution: files[n].w / LOGICAL } }]));
}

// 받기 관리. load(asset) = 텍스처(Pixi 의 Assets.load). 같은 주소는 한 번만 받고, 실패하면 null 로 끝난다(다음에 부르면 다시 받는다)
// (깨진 캐시 응답 한 장 때문에 나머지 그림이나 게임 시작이 멈추지 않게).
export function createLoader(load, { warn = console.warn } = {}) {
  const jobs = new Map();
  const ok = new Set();
  const get = (asset) => {
    const key = asset.src ?? asset;
    if (!jobs.has(key)) {
      jobs.set(key, Promise.resolve().then(() => load(asset)).then((t) => { ok.add(key); return t; }, (e) => { warn(`그림을 받지 못했다: ${key}`, e); jobs.delete(key); return null; }));
    }
    return jobs.get(key);
  };
  return { get, has: (key) => ok.has(key) };
}

// 진행률: report(끝난 수, 받을 수)를 처음 한 번과 하나 끝날 때마다 부른다. 실패한 것도 끝난 것으로 센다
export async function track(jobs, report) {
  let done = 0;
  report(0, jobs.length);
  await Promise.all(jobs.map((j) => Promise.resolve(j).catch(() => {}).then(() => report(++done, jobs.length))));
}

// 나무의 잎(주소 또는 asset)만 한 줄로
export const leaves = (tree) => (typeof tree === 'string' || tree?.src ? [tree] : Object.values(tree).flatMap(leaves));

// 주소(또는 asset) 나무를 같은 모양의 텍스처 나무로. 모두 한꺼번에 요청한다
export async function loadTree(tree, get) {
  if (typeof tree === 'string' || tree?.src) return get(tree);
  if (Array.isArray(tree)) return Promise.all(tree.map((t) => loadTree(t, get)));
  const keys = Object.keys(tree);
  const vals = await Promise.all(keys.map((k) => loadTree(tree[k], get)));
  return Object.fromEntries(keys.map((k, i) => [k, vals[i]]));
}
