# nginx 안내

개발 서버와 운영 서버의 nginx 설정이에요. 두 서버 모두 API 주소는 Cloudflare 프록시를 거치지 않고 직접 받아요.
개발 설정에는 기존 직접 연결을 유지하면서 ALB의 HTTP 전달을 시험하는 경로도 있어요.

| 파일 | 서버 | 요청을 받는 방식 |
| --- | --- | --- |
| `api.conf` | 운영 | API 주소를 직접 받아요 |
| `api-dev.conf` | 개발 | API 주소는 직접 HTTPS 또는 ALB 경유 HTTP로 받고, 문서용 주소는 Cloudflare 프록시를 거친 요청만 받아요 |

결정 배경은 [ADR-0035](../../docs/adr/0035-serve-dev-api-without-cloudflare-proxy.md)와 [ADR-0036](../../docs/adr/0036-serve-prod-api-without-cloudflare-proxy.md)에 있어요.

## 주소

| 주소 | 서버 | Cloudflare 프록시 | 인증서 | 용도 |
| --- | --- | --- | --- | --- |
| `api.pheeeew.com` | 운영 | 끔 | Let's Encrypt (`/etc/letsencrypt`) | 앱의 API 요청. 스웨거는 앱 설정과 nginx에서 꺼져 있어요 |
| `api-dev.pheeeew.com` | 개발 | 끔 | Let's Encrypt (`/etc/letsencrypt`) | 앱의 API 요청. 스웨거 경로는 404예요 |
| `docs-dev.pheeeew.com` | 개발 | 켬 | Cloudflare Origin CA (`/etc/ssl/cloudflare`) | 스웨거. Cloudflare Access 로그인 뒤에 열려요 |

- 문서용 주소는 Cloudflare가 제시하는 클라이언트 인증서를 요구해요. 서버 IP로 직접 붙으면 거부돼요.
- Access 애플리케이션의 대상은 `docs-dev.pheeeew.com` 전체로 잡아요. 스웨거의 실행 요청도 이 주소를 거쳐요.
- 운영에는 문서용 주소가 없어요.

## 개발 ALB HTTP 시험

기존 직접 HTTPS 연결을 유지하면서 `앱 → HTTPS:443 ALB → HTTP:80 Nginx → 앱 서버`를 시험해요.
대상 그룹은 `HTTP:80`, `HTTP1`, 상태 검사는 `HTTP`, 트래픽 포트 `80`, 경로 `/actuator/health`, 성공 코드 `200`으로 설정해요.
ALB의 HTTPS:443 리스너에는 개발 API 도메인을 포함한 ACM 인증서가 필요해요. HTTP 리스너도 추가한다면 HTTPS로 리디렉션해요.

- HTTP API는 원래 TCP 연결 주소가 `project-lb-a` (`10.0.10.0/24`), `project-lb-b` (`10.0.11.0/24`)이고 `X-Forwarded-Proto: https`인 요청만 전달해요. 다른 출처의 API HTTP 요청은 기존처럼 HTTPS로 리디렉션해요.
- **배포 전 필수 확인:** ALB의 `routing.http.xff_header_processing.mode`는 기본값인 `append`여야 해요. HTTP API 경로에서만 ALB가 마지막에 추가한 실제 사용자 IP를 읽고, 요청 제한과 앱에 전달하는 IP에도 사용해요. `preserve`로 바꾸면 사용자 헤더를 신뢰하게 되므로 안 돼요.
- **배포 전 필수 확인:** EC2의 80번 포트 출처는 ALB의 `project-lb` 보안 그룹으로 제한해요. Nginx의 CIDR 제한만으로는 같은 서브넷의 다른 발신자를 구별하지 못해요. 공유 보안 그룹의 규칙을 임의로 수정하지 않아요. 기존 직접 HTTPS:443과 문서용 연결은 유지해요.
- IP Host로 오는 정확한 GET `/actuator/health`만 HTTP:80과 기존 HTTPS:443에서 받아 앱의 실제 정상·장애 응답을 반환해요. API·문서 도메인의 Actuator 차단과 알 수 없는 Host의 다른 경로 차단은 유지해요.

설정 배포 뒤 실제 ALB에서 target healthy, API 응답, 사용자 IP별 요청 제한을 확인한 다음 DNS를 전환해요.
시험을 끝내고 개발을 직접 연결만 쓰게 되돌릴 때는 ALB 연결과 HTTP API 시험 경로를 함께 정리해요. 운영 설정과 CD 워크플로는 이 시험에서 바꾸지 않아요.

로컬 Docker와 OpenSSL이 있으면 아래 명령으로 두 LB 서브넷의 HTTP/HTTPS 상태 검사, HTTP API 전달, 실제 사용자 IP와 HTTPS 인식, 사용자별 요청 제한과 기존 접근 차단을 검증해요.
임시 인증서·앱·내부 Docker 네트워크를 만들고 종료 시 정리해요. 실제 AWS 연결이나 보안 그룹은 검증하지 않아요.

```bash
python3 docker/nginx/test-alb-health.py
```

`BE Nginx CI`도 같은 명령을 실행해요. `main`, `be/dev`, `develop` 대상 PR에서
`BE/docker/nginx/**` 또는 `.github/workflows/be-nginx-ci.yml`이 바뀌면 실행되고, 검증 실패 시 CI도 실패해요.
현재 스크립트는 개발 설정(`api-dev.conf`)을 검증해요. 운영 설정(`api.conf`)은 아직 검사하지 않아요.

## 요청자 주소

API 주소의 직접 HTTPS 연결은 요청자 주소로 연결 주소(`$remote_addr`)만 써요. `CF-Connecting-IP` 헤더는 어디서 온 요청이든 믿지 않아요. 요청 제한이 이 값을 기준으로 하기 때문이에요.
개발의 ALB HTTP 경로만 원래 연결 출처를 확인한 뒤 `X-Forwarded-For`의 마지막 주소를 `$remote_addr`로 사용해요. 앱에는 정규화한 사용자 IP와 `https`, 포트 `443`, API Host를 전달하고 `Forwarded`, `CF-Connecting-IP`는 제거해요.

- 직접 붙은 요청은 이 헤더에 아무 값이나 적을 수 있어요.
- Cloudflare 대역에서 온 연결도 믿지 않아요. Cloudflare Workers가 이 헤더를 임의로 넣어 서버에 요청할 수 있어서, 대역만 보고 믿으면 요청 제한을 우회할 수 있어요.

| 서버 | 요청 제한 경로 |
| --- | --- |
| 운영 | 기기 등록, challenge 발급, 그룹 참여 |
| 개발 | 기기 등록, challenge 발급 |

문서용 주소(`docs-dev`)만 `CF-Connecting-IP` 를 요청자 주소로 써요. Cloudflare가 제시하는 클라이언트 인증서를 확인한 연결이기 때문이에요. Cloudflare 대역은 `api-dev.conf` 의 문서용 서버 블록에 있는 `set_real_ip_from` 목록이에요. [Cloudflare가 공개한 목록](https://www.cloudflare.com/ips/)이 바뀌면 함께 고쳐요.

**API 주소의 프록시가 켜져 있는 동안에는 요청 제한이 느슨하게 어긋나요.** 연결 주소가 Cloudflare 거점의 주소가 되어, 같은 거점을 지나는 사용자들이 한 사람처럼 세어져요. 전환 중이나 되돌린 상태를 오래 두지 않아요.

## 인증서 발급

서버에 certbot을 직접 설치하고 DNS 방식으로 발급해요. 서비스에 영향이 없어요.

1. Cloudflare에서 `pheeeew.com` 영역의 DNS 편집 권한만 가진 API 토큰을 만들어요. **서버마다 따로 만들어요.** 한쪽이 유출돼도 다른 쪽을 바꾸지 않기 위해서예요.
2. 토큰 파일은 `/etc/letsencrypt` 밖에 둬요. 저장소와 GitHub Secrets에는 넣지 않아요.

```bash
sudo install -d -m 700 /root/.secrets
sudo install -m 600 /dev/null /root/.secrets/cloudflare.ini
sudo nano /root/.secrets/cloudflare.ini    # dns_cloudflare_api_token = <토큰>
```

3. 발급해요. `-d` 에는 그 서버의 API 주소를 넣어요.

```bash
sudo certbot certonly \
  --dns-cloudflare \
  --dns-cloudflare-credentials /root/.secrets/cloudflare.ini \
  --dns-cloudflare-propagation-seconds 30 \
  -d api.pheeeew.com
```

**인증서가 없는 서버에 설정을 배포하면 nginx가 뜨지 못해요.** 발급을 먼저 해요.

nginx 컨테이너에는 `/etc/letsencrypt/live` 와 `/etc/letsencrypt/archive` 만 연결해요. 토큰 파일과 certbot 계정 정보는 컨테이너에서 보이지 않아요.

### 토큰 파일을 `/etc/letsencrypt` 안에 둔 서버

토큰을 옮기고 갱신 설정이 새 경로를 보게 해요. 도메인은 그 서버의 API 주소예요.

```bash
sudo install -d -m 700 /root/.secrets
sudo mv /etc/letsencrypt/cloudflare.ini /root/.secrets/cloudflare.ini
sudo sed -i 's#/etc/letsencrypt/cloudflare.ini#/root/.secrets/cloudflare.ini#' /etc/letsencrypt/renewal/api.pheeeew.com.conf
sudo certbot renew --dry-run
```

## 인증서 갱신

인증서는 90일짜리예요. 갱신이 실패하면 앱의 모든 요청이 실패해요. Let's Encrypt는 만료 안내 메일을 보내지 않으므로 만료 알림을 따로 둬요.

갱신된 인증서를 nginx가 다시 읽게 해요. **서버에 맞는 명령을 써요.**

```bash
# 운영 서버
echo -e '#!/bin/sh\ndocker exec pheeeew-prod-nginx nginx -s reload' | sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

# 개발 서버
echo -e '#!/bin/sh\ndocker exec pheeeew-dev-nginx nginx -s reload' | sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
```

그다음은 두 서버가 같아요.

```bash
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
sudo certbot renew --dry-run
systemctl list-timers | grep certbot
```

- 마지막 명령에 아무것도 나오지 않으면 자동 갱신이 예약되지 않은 상태예요.
- 토큰 파일을 지우면 갱신이 실패해요.

## 전환과 되돌리기

두 설정 파일은 프록시가 켜져 있어도 꺼져 있어도 동작해요.

1. 설정을 배포한 뒤 Cloudflare 경유와 직접 연결 양쪽을 확인해요. 직접 연결이 시간 초과되면 보안 그룹의 443 허용 범위를 확인해요.

```bash
curl -s -o /dev/null -w '%{http_code}\n' "https://api.pheeeew.com/api/v2/app/version?platform=android"
curl -s -o /dev/null -w '%{http_code}\n' --resolve api.pheeeew.com:443:<서버 IP> "https://api.pheeeew.com/api/v2/app/version?platform=android"
```

2. Cloudflare에서 API 주소의 레코드를 DNS 전용으로 바꿔요.
3. `https://api.pheeeew.com/cdn-cgi/trace` 가 200이 아니면 직접 연결된 상태예요. 기기가 예전 주소를 기억하는 몇 분 동안은 200이 나올 수 있어요.

문제가 생기면 레코드의 프록시를 다시 켜요. 요청이 다시 Cloudflare를 거쳐요. **이것은 급한 불을 끄는 조치이고 전환 전 상태로 완전히 돌아간 것은 아니에요.**

- 서버는 여전히 직접 붙는 요청을 받아요. 서버 IP를 아는 쪽은 Cloudflare를 우회할 수 있어요.
- 요청 제한이 Cloudflare 거점의 주소를 기준으로 세어져요.

완전히 되돌리려면 nginx 설정을 전환 전 버전으로 되돌려 배포하거나, 보안 그룹에서 443을 Cloudflare 대역만 허용해요.
