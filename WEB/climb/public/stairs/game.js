import { direction, remaining, replenish, cap, MAX_INPUTS, MAX_TIME_MS } from './rules.js';

// 시계와 UI를 주입하지 않는 판 상태. 시간은 첫 입력부터, 점수 시간은 마지막 성공까지.
export class StairsGame {
  constructor(seed) {
    this.seed = seed; this.height = 0; this.timeMs = 0; this.gauge = cap(0);
    this.inputs = []; this.startedAt = null; this.lastT = 0; this.ended = false;
    this.reason = ''; this.x = 0; this.positions = [0];
  }
  gaugeAt(now) {
    if (this.startedAt === null) return cap(0);
    return remaining(this.gauge, this.height, Math.max(0, Math.floor(now - this.startedAt) - this.lastT));
  }
  tick(now) {
    if (this.ended || this.startedAt === null) return;
    if (this.gaugeAt(now) <= 0 || now - this.startedAt >= MAX_TIME_MS) this.end('시간이 다 됐어요');
  }
  input(d, now) {
    if (this.ended) return false;
    this.tick(now);
    if (this.ended) return false;
    if (this.startedAt === null) this.startedAt = now;
    const t = this.inputs.length ? Math.max(this.lastT + 1, Math.floor(now - this.startedAt)) : 0;
    const g = remaining(this.gauge, this.height, t - this.lastT);
    if (g <= 0 || t > MAX_TIME_MS) { this.end('시간이 다 됐어요'); return false; }
    this.inputs.push({ d, t });
    if (d !== direction(this.seed, this.height)) { this.end('앗, 반대쪽 계단이었어요!'); return false; }
    this.height++; this.timeMs = t; this.lastT = t; this.gauge = replenish(g, this.height);
    this.x += d === 'L' ? -1 : 1; this.positions.push(this.x);
    if (this.inputs.length >= MAX_INPUTS) this.end('5000칸 완주! 오늘의 구름 마스터');
    return true;
  }
  end(reason) { this.ended = true; this.reason = reason; }
}
