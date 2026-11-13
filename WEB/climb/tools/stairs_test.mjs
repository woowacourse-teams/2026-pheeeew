import assert from 'node:assert/strict';
import fs from 'node:fs';
import { direction, replay, remaining, replenish, drainRate, capMs, cap, GAUGE_GAIN } from '../public/stairs/rules.js';
import { StairsGame } from '../public/stairs/game.js';
import { createNet, BUILD } from '../public/net.js';
const fixture = new URL('../../../BE/climb/src/test/resources/stairs/', import.meta.url);
for (const line of fs.readFileSync(new URL('generator-v2.txt', fixture), 'utf8').trim().split('\n')) {
  const [seed, expected] = line.split(':');
  assert.equal(Array.from({ length: 2000 }, (_, i) => direction(Number(seed), i)).join(''), expected);
  for (let i = 1999; i >= 0; i--) assert.equal(direction(Number(seed), i), expected[i]);
}
for (const line of fs.readFileSync(new URL('gauge-v2.csv', fixture), 'utf8').trim().split('\n')) {
  const [g, h, t, left, full, maxMs, maxGauge] = line.split(',').map(Number);
  assert.equal(remaining(g, h, t), left); assert.equal(replenish(left, h + 1), full);
  assert.equal(capMs(h), maxMs); assert.equal(cap(h), maxGauge);
}
const inputs = (n, interval = 100) => Array.from({ length: n }, (_, i) => ({ d: direction(123, i), t: i * interval }));
assert.deepEqual(replay(123, inputs(50)), { height: 50, timeMs: 4900 });
assert.deepEqual(replay(123, [{ d: direction(123, 0) === 'L' ? 'R' : 'L', t: 0 }]), { height: 0, timeMs: 0 });
const wrong = inputs(5); wrong[3].d = wrong[3].d === 'L' ? 'R' : 'L'; wrong[4].t = -1;
assert.deepEqual(replay(123, wrong), { height: 3, timeMs: 200 });
assert.throws(() => replay(123, inputs(4, 44)), /TOO_FAST/);
assert.equal(replay(123, inputs(4, 45)).height, 4);
assert.throws(() => replay(123, inputs(2, 6000)), /GAUGE_EXPIRED/);
// 300칸까지 빠르게 올라 꽉 채운 뒤, 만료 직전/정확히 0/초과를 검사한다.
for (const pause of [1999, 2000, 2001]) {
  const full = new StairsGame(123);
  const events = inputs(300, 45);
  for (const input of events) assert.equal(full.input(input.d, input.t), true);
  assert.equal(full.gauge, cap(300));
  assert.equal(cap(300), 5600000);
  const t = events.at(-1).t + pause;
  assert.equal(full.gaugeAt(t), (2000 - pause) * 2800);
  assert.equal(full.input(direction(123, 300), t), pause < 2000);
  events.push({ d: direction(123, 300), t });
  if (pause < 2000) assert.deepEqual(replay(123, events), { height: 301, timeMs: t });
  else assert.throws(() => replay(123, events), /GAUGE_EXPIRED/);
}
for (const bad of [[], inputs(5001), [{ d: 'X', t: 0 }], [{ d: 'L', t: .5 }], inputs(2, 0)]) assert.throws(() => replay(123, bad));
// 전체 구간과 경계를 검사한다. 끝에서 잘린 묶음은 분포·리듬 집계에서 제외한다.
let earlyTraps = 0, lateTraps = 0;
for (const seed of [...Array(100).keys(), -1, -2147483648, 2147483647, 0x9e3779b9 | 0]) {
  const pattern = Array.from({ length: 2001 }, (_, i) => direction(seed, i)).join('');
  assert.equal(pattern.slice(0, 10), seed & 1 ? 'RRRRRLLLLL' : 'LLLLLRRRRR');
  const text = pattern.slice(10, 2000);
  assert.doesNotMatch(text, /LRLRL|RLRLR/, `alternation seed=${seed}`);
  const runs = text.match(/L+|R+/g).map(r => r.length).slice(0, -1);
  const counts = Array(7).fill(0);
  let traps = 0;
  for (let i = 0; i < runs.length; i++) {
    counts[Math.min(6, runs[i])]++;
    if (i >= 3) assert.ok(!runs.slice(i - 3, i + 1).every(n => n === runs[i]), `rhythm seed=${seed}, run=${i}`);
    if (i >= 2 && runs[i - 1] === 1 && runs[i - 2] >= 3 && runs[i] >= 3) traps++;
  }
  for (let length = 1; length <= 6; length++) assert.ok(counts[length] / runs.length >= .05, `distribution seed=${seed}, length=${length}`);
  assert.ok(traps >= 30, `long traps seed=${seed}: ${traps}`);
  // 최장 번갈아(4칸)는 전체 4칸 창 중 5% 이하로 제한한다.
  assert.ok([...text.matchAll(/(?=(LRLR|RLRL))/g)].length / text.length < .05);
  earlyTraps += [...pattern.slice(10, 50).matchAll(/(?=(LLRLL|RRLRR))/g)].length;
  lateTraps += [...pattern.slice(800, 840).matchAll(/(?=(LLRLL|RRLRR))/g)].length;
}
assert.ok(lateTraps > earlyTraps * 1.25, `${lateTraps} > ${earlyTraps} * 1.25`);
for (const [height, target] of [[0, 2.5], [100, 4], [300, 7], [500, 10], [800, 14.5]]) {
  assert.equal(drainRate(height) * 1000 / GAUGE_GAIN, target);
}
for (const [height, ms] of [[0, 6000], [150, 4000], [300, 2000], [450, 1600], [600, 1200], [900, 1200], [1, 5986], [299, 2013], [301, 1997], [599, 1202]]) {
  assert.equal(capMs(height), ms);
}
assert.equal(cap(0), 6000000);
for (let h = 1; h <= 5000; h++) {
  assert.equal(drainRate(h), 1000 + h * 6);
  assert.ok(Number.isSafeInteger(cap(h)));
  assert.ok(capMs(h) <= capMs(h - 1));
  assert.equal(replenish(cap(h - 1), h), Math.min(cap(h), cap(h - 1) + GAUGE_GAIN));
}
// seed, 기본 간격, 지터, 성공 칸, 마지막 성공 t, 만료 입력 t, 만료 직전 게이지
for (const line of fs.readFileSync(new URL('replay-v2.csv', fixture), 'utf8').trim().split('\n')) {
  const [seed, interval, jitter, height, timeMs, expiredAt, left] = line.split(',').map(Number);
  const g = new StairsGame(seed), events = [];
  let t = 0;
  for (let i = 0; i <= height; i++) {
    if (i) t += interval + (i * 37 % (jitter * 2 + 1)) - jitter;
    const input = { d: direction(seed, i), t };
    if (i === height) {
      assert.equal(g.gaugeAt(t), left);
      assert.equal(t, expiredAt);
    }
    assert.equal(g.input(input.d, t), i < height);
    events.push(input);
  }
  assert.equal(g.height, height); assert.equal(g.timeMs, timeMs); assert.equal(g.ended, true);
  assert.deepEqual(replay(seed, events.slice(0, -1)), { height, timeMs });
  assert.throws(() => replay(seed, events), /GAUGE_EXPIRED/);
  if (height >= 300) {
    // 자연스럽게 잔량이 줄어든 300칸 이후에는 500ms 정지도 만료된다.
    const paused = events.slice(0, -1);
    paused.push({ d: direction(seed, height), t: timeMs + 500 });
    assert.throws(() => replay(seed, paused), /GAUGE_EXPIRED/);
  }
  console.log(`stairs replay ${interval}ms ±${jitter}: expires at step ${height + 1}, ${expiredAt}ms`);
}
const game = new StairsGame(123);
game.tick(90000); assert.equal(game.ended, false); assert.equal(game.gaugeAt(90000), cap(0));
for (let i = 0; i < 30; i++) assert.equal(game.input(direction(123, i), 90000 + i * 180), true);
assert.deepEqual(replay(123, game.inputs), { height: game.height, timeMs: game.timeMs });
game.tick(120000); assert.equal(game.ended, true); assert.equal(game.timeMs, 5220);
const fast = new StairsGame(0); fast.input(direction(0,0),10); fast.input(direction(0,1),10);
assert.equal(fast.inputs[1].t, 1);
const calls = [];
const net = createNet({ base: 'http://localhost:4325', fetch: async (url, init) => { calls.push({ url, init }); return { status: 200, json: async () => ({}) }; }});
await net.stairsToday(); await net.stairsRanking('2026-11-13'); await net.stairsRun({ day: '2026-11-13', nickname: '히유', character: 'angry', inputs: inputs(2) });
assert.equal(BUILD, 2); assert.ok(calls.every(c => c.init.credentials === 'omit'));
assert.match(calls[0].url, /\/stairs\/today$/); assert.match(calls[1].url, /\/stairs\/ranking\?day=2026-11-13$/); assert.equal(calls[2].init.method, 'POST');
console.log('stairs: PASS shared 6 × 2000 generator fixtures, gauge targets/cap, shared expiry, 104 seeds patterns, timing, input model, API');
