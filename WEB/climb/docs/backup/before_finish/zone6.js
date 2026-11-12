// 정상: 달. 넓은 바닥 하나. 여기 서면 완주다.
export const top = 9;
export default function build({ rock }) {
  rock(4, 8, 3, 5); // 달로 오르는 디딤돌
  rock(12, 30, top - 2, top); // 달 표면(디딤돌 위는 비워 둔다)
}
export const decor = [];
