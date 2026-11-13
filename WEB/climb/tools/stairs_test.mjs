import assert from 'node:assert/strict';
import fs from 'node:fs';
import { direction, replay, remaining, replenish, GAUGE_MAX } from '../public/stairs/rules.js';
import { StairsGame } from '../public/stairs/game.js';
import { createNet, BUILD } from '../public/net.js';
const fixture = new URL('../../../BE/climb/src/test/resources/stairs/', import.meta.url);
for (const line of fs.readFileSync(new URL('generator-v1.txt', fixture), 'utf8').trim().split('\n')) {
  const [seed, expected] = line.split(':');
  assert.equal(Array.from({ length: 2000 }, (_, i) => direction(Number(seed), i)).join(''), expected);
  for (let i = 1999; i >= 0; i--) assert.equal(direction(Number(seed), i), expected[i]);
}
for (const line of fs.readFileSync(new URL('gauge-v1.csv', fixture), 'utf8').trim().split('\n')) {
  const [g, h, t, left, full] = line.split(',').map(Number);
  assert.equal(remaining(g, h, t), left); assert.equal(replenish(left), full);
}
const inputs = (n, interval = 100) => Array.from({ length: n }, (_, i) => ({ d: direction(123, i), t: i * interval }));
assert.deepEqual(replay(123, inputs(50)), { height: 50, timeMs: 4900 });
assert.deepEqual(replay(123, [{ d: direction(123, 0) === 'L' ? 'R' : 'L', t: 0 }]), { height: 0, timeMs: 0 });
const wrong = inputs(5); wrong[3].d = wrong[3].d === 'L' ? 'R' : 'L'; wrong[4].t = -1;
assert.deepEqual(replay(123, wrong), { height: 3, timeMs: 200 });
assert.throws(() => replay(123, inputs(4, 44)), /TOO_FAST/);
assert.equal(replay(123, inputs(4, 45)).height, 4);
assert.throws(() => replay(123, inputs(2, 6000)), /GAUGE_EXPIRED/);
for (const bad of [[], inputs(5001), [{ d: 'X', t: 0 }], [{ d: 'L', t: .5 }], inputs(2, 0)]) assert.throws(() => replay(123, bad));
const pattern = Array.from({ length: 2000 }, (_, i) => direction(123, i)).join('');
assert.match(pattern, /LRLRLRLR|RLRLRLRL/); assert.match(pattern, /LLLRRRLLL|RRRLLLRRR/); assert.match(pattern, /RRRRLRRR|LLLLRLLL/);
let easyTraps = 0, hardTraps = 0;
for (let seed = 0; seed < 100; seed++) for (const [start, hard] of [[10, false], [650, true]]) {
  const text = Array.from({ length: 160 }, (_, i) => direction(seed, start + i)).join('');
  const count = [...text.matchAll(/(?=(LLRLL|RRLRR))/g)].length;
  if (hard) hardTraps += count; else easyTraps += count;
}
assert.ok(hardTraps > easyTraps, `${hardTraps} > ${easyTraps}`);
const game = new StairsGame(123);
game.tick(90000); assert.equal(game.ended, false); assert.equal(game.gaugeAt(90000), GAUGE_MAX);
for (let i = 0; i < 30; i++) assert.equal(game.input(direction(123, i), 90000 + i * 180), true);
assert.deepEqual(replay(123, game.inputs), { height: game.height, timeMs: game.timeMs });
game.tick(120000); assert.equal(game.ended, true); assert.equal(game.timeMs, 5220);
const fast = new StairsGame(0); fast.input(direction(0,0),10); fast.input(direction(0,1),10);
assert.equal(fast.inputs[1].t, 1);
for (const interval of [333, 200, 125]) {
  const g = new StairsGame(0); let time = 0;
  while (!g.ended) { g.input(direction(0,g.height),time); time += interval; }
  assert.ok(time >= 30000 && time <= 120000, `${interval}ms: ${time}ms`);
  console.log(`stairs pacing ${interval}ms/input: ${(time/1000).toFixed(1)}s, ${g.height} steps`);
}
const calls = [];
const net = createNet({ base: 'http://localhost:4325', fetch: async (url, init) => { calls.push({ url, init }); return { status: 200, json: async () => ({}) }; }});
await net.stairsToday(); await net.stairsRanking('2026-11-13'); await net.stairsRun({ day: '2026-11-13', character: 'angry', inputs: inputs(2) });
assert.equal(BUILD, 2); assert.ok(calls.every(c => c.init.credentials === 'include'));
assert.match(calls[0].url, /\/stairs\/today$/); assert.match(calls[1].url, /\/stairs\/ranking\?day=2026-11-13$/); assert.equal(calls[2].init.method, 'POST');
console.log('stairs: PASS shared 6 × 2000 generator fixtures, gauge, replay, patterns, timing, input model, API');
