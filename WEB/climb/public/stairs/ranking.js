import { FACE, TINT, medal } from '../ui.js';

const escape = value => String(value ?? '').replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);

// 입력 중인 이름이 아닌, 이 탭에서 등록에 성공한 이름만 전달한다.
export function findMyRow(items, nickname) {
  return nickname ? items.findIndex(item => item.nickname === nickname) : -1;
}

export function rankingRows(items, nickname = '') {
  const mine = findMyRow(items, nickname);
  return items.map((item, index) => {
    const podium = medal(item.rank);
    const character = Object.hasOwn(TINT, item.character) ? item.character : 'defeated';
    return `<div class="r${podium ? ` podium ${podium}` : ''}${index === mine ? ' my-record' : ''}"${index === mine ? ' data-mine="true"' : ''}><b class="${podium ? 'medal' : ''}">${escape(item.rank)}</b><div class="avatar mini"><img src="${FACE(character)}" alt=""></div><span class="n">${escape(item.nickname)}</span><span class="v">${escape(item.height)}칸<small>${(item.timeMs / 1000).toFixed(3)}초</small></span></div>`;
  }).join('') || '<p class="none">첫 기록의 주인공이 되어 주세요!</p>';
}
