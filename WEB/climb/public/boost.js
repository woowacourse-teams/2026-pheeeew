// 분사 판 효과의 상태 계산(그리지 않는다). main.js 가 이 값으로 판의 빛과 위로 솟는 조각을 그린다.
export const RISE_LIFE = 1.2; // 솟는 조각 하나가 떠 있는 시간(초)
const BREATH = 2.4; // 숨 쉬는 주기(초)

// t = 시각(초), on = 캐릭터가 이 판 위에 서 있음, charge = 모은 정도 0~1(서 있을 때만 본다)
// 돌려주는 값: glow = 판의 밝기 0~1, every = 조각을 만드는 간격(초), max = 판 하나에 동시에 떠 있을 조각 수의 상한
export function boostLook(t, on, charge) {
  const breath = Math.sin((2 * Math.PI * t) / BREATH);
  if (!on) return { glow: 0.35 + 0.1 * breath, every: 0.3, max: 5 }; // 1.2초 ÷ 0.3초 = 늘 4개쯤
  const c = Math.min(1, Math.max(0, charge));
  return { glow: Math.min(1, 0.6 + 0.05 * breath + 0.4 * c), every: 0.22 - 0.14 * c, max: 6 + Math.round(8 * c) };
}
