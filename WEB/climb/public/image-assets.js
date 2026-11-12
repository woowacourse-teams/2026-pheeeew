// 선택적 이미지가 없거나 HTML fallback이면 읽지 않는다. 이전의 잘못된 캐시도 재검증한다.
export async function hasImage(url, request = fetch) {
  try {
    const response = await request(url, { method: 'HEAD', cache: 'no-cache' });
    return response.ok && (response.headers.get('content-type') ?? '').toLowerCase().startsWith('image/');
  } catch {
    return false;
  }
}
