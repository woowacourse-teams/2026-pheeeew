// URL 옵션으로 켜는 시험 화면은 로컬 개발에서만 허용한다.
export function isDebugEnabled({ hostname, search }) {
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(hostname);
  return local && new URLSearchParams(search).has('debug');
}
