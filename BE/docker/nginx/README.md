# nginx 안내

개발 서버와 운영 서버의 nginx 설정이에요. 두 서버가 요청을 받는 방식이 달라요.

| 파일 | 서버 | 요청을 받는 방식 |
| --- | --- | --- |
| `api.conf` | 운영 | Cloudflare 프록시를 거친 요청만 받아요 |
| `api-dev.conf` | 개발 | API 주소는 직접 받고, 문서용 주소는 Cloudflare 프록시를 거친 요청만 받아요 |

결정 배경은 [ADR-0035](../../docs/adr/0035-serve-dev-api-without-cloudflare-proxy.md)에 있어요.

## 개발 서버의 두 주소

| 주소 | Cloudflare 프록시 | 인증서 | 용도 |
| --- | --- | --- | --- |
| `api-dev.pheeeew.com` | 끔 | Let's Encrypt (`/etc/letsencrypt`) | 앱의 API 요청. 스웨거 경로는 404예요 |
| `docs-dev.pheeeew.com` | 켬 | Cloudflare Origin CA (`/etc/ssl/cloudflare`) | 스웨거. Cloudflare Access 로그인 뒤에 열려요 |

- 문서용 주소는 Cloudflare가 제시하는 클라이언트 인증서를 요구해요. 서버 IP로 직접 붙으면 거부돼요.
- Access 애플리케이션의 대상은 `docs-dev.pheeeew.com` 전체로 잡아요. 스웨거의 실행 요청도 이 주소를 거쳐요.

## 요청자 주소

요청자 주소는 연결 주소(`$remote_addr`)를 써요. Cloudflare 대역에서 온 연결만 `CF-Connecting-IP` 헤더의 값으로 바꿔요. 직접 붙은 요청이 이 헤더를 보내도 무시해요. 기기 등록과 challenge 발급의 요청 제한이 이 값을 기준으로 하기 때문이에요.

Cloudflare 대역은 `api-dev.conf` 의 `set_real_ip_from` 목록이에요. [Cloudflare가 공개한 목록](https://www.cloudflare.com/ips/)이 바뀌면 함께 고쳐요.

## 인증서 발급

개발 서버에 certbot을 직접 설치하고 DNS 방식으로 발급해요. 서비스에 영향이 없어요.

1. Cloudflare에서 `pheeeew.com` 영역의 DNS 편집 권한만 가진 API 토큰을 만들어요.
2. 서버의 `/etc/letsencrypt/cloudflare.ini` 에 `dns_cloudflare_api_token = <토큰>` 한 줄을 넣고 권한을 600으로 둬요. 토큰은 저장소와 GitHub Secrets에 넣지 않아요.
3. 발급해요.

```bash
sudo certbot certonly \
  --dns-cloudflare \
  --dns-cloudflare-credentials /etc/letsencrypt/cloudflare.ini \
  --dns-cloudflare-propagation-seconds 30 \
  -d api-dev.pheeeew.com
```

**인증서가 없는 서버에 `api-dev.conf` 를 배포하면 nginx가 뜨지 못해요.** 발급을 먼저 해요.

## 인증서 갱신

인증서는 90일짜리예요. 갱신이 실패하면 앱의 모든 요청이 실패해요.

```bash
echo -e '#!/bin/sh\ndocker exec pheeeew-dev-nginx nginx -s reload' | sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
sudo certbot renew --dry-run
systemctl list-timers | grep certbot
```

- 첫 두 줄은 갱신된 인증서를 nginx가 다시 읽게 해요.
- 마지막 명령에 아무것도 나오지 않으면 자동 갱신이 예약되지 않은 상태예요.
- 토큰 파일을 지우면 갱신이 실패해요.

## 전환과 되돌리기

`api-dev.conf` 는 프록시가 켜져 있어도 꺼져 있어도 동작해요.

1. 설정을 배포한 뒤 Cloudflare 경유와 직접 연결 양쪽을 확인해요.

```bash
curl -s -o /dev/null -w '%{http_code}\n' "https://api-dev.pheeeew.com/api/v2/app/version?platform=android"
curl -s -o /dev/null -w '%{http_code}\n' --resolve api-dev.pheeeew.com:443:<서버 IP> "https://api-dev.pheeeew.com/api/v2/app/version?platform=android"
```

2. Cloudflare에서 `api-dev` 레코드를 DNS 전용으로 바꿔요.
3. `https://api-dev.pheeeew.com/cdn-cgi/trace` 가 404면 직접 연결된 상태예요.

되돌릴 때는 `api-dev` 레코드의 프록시를 다시 켜요. nginx 설정은 그대로 둬요.
