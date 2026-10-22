# 개발 서버 배포 안내

`develop` 에 푸시하면 `BE DEV CD` 가 돌면서 `app-blue` 와 `app-green` 을 번갈아 띄워요. 한 번에 한 색만 떠 있고, 교체 구간에만 둘이 함께 떠요.

| 파일 | 역할 |
| --- | --- |
| `compose.dev.yml` | `app-blue`, `app-green`. 색마다 로그 볼륨이 따로예요 |
| `compose.nginx.dev.yml` | nginx. 앱 배포와 분리돼 있어 앱만 교체돼요 |
| `nginx/upstream/active.conf` | 지금 어느 색이 트래픽을 받는지. **서버에만 있고 배포가 덮지 않아요** |

## 전환 흐름

```
새 색 기동 → healthy → 전환 파일 교체 + nginx reload → 정상 응답 검증
→ 이전 요청 drain → 이전 색 graceful 종료
```

전환 **전**에 실패하면 전환하지 않고 끝나요. 전환 **후** 검증이 실패하면 이전 색으로 되돌리고 새 색을 정리해요. 어느 쪽이든 트래픽은 검증된 색에 남아요.

정상 응답 검증은 상태 코드 200, 본문의 `available` 이 boolean, `X-Upstream` 이 새 색인지를 함께 봐요. 503 에도 `X-Upstream` 은 붙으므로 헤더만으로 판단하지 않아요.

복구까지 실패하면 **두 색을 모두 남기고** 전환 파일과 실제 응답을 함께 보고해요. 그때는 사람이 확인해야 해요.

## 되돌리기

배포가 끝난 뒤에 문제를 발견했으면 Actions 에서 **`BE DEV CD` 를 수동 실행**하고 `image_tag` 에 이전 태그를 넣어요. 빌드를 건너뛰고 그 이미지로 다시 전환해요.

이전 태그는 서버에서 확인해요.

```bash
docker image ls --format '{{.Repository}}:{{.Tag}}  {{.CreatedSince}}' | grep dev-
```

`docker image prune -f` 는 태그 없는 이미지만 지우므로 이전 배포 이미지는 남아 있어요.

주의할 것이 둘이에요.

- 수동 실행은 **선택한 ref 의 compose 와 nginx 설정을 그대로 전송해요.** 앱 코드만 되돌릴 때는 맞지만, 설정까지 되돌리려면 그 시점의 커밋을 가리키는 ref 로 실행해야 해요.
- 구버전 앱이 **새 스키마에서 떠야 해요.** `ddl-auto: validate` 라서 엔티티가 참조하는 컬럼이 사라졌으면 부팅이 실패해요. 마이그레이션을 expand-contract 로 나누는 규칙이 [코드 컨벤션 9.3](../docs/decisions/backend-code-convention.md)에 있어요.

## 상태 확인

```bash
docker ps --format '{{.Names}}\t{{.Status}}'
docker exec pheeeew-dev-nginx cat /etc/nginx/upstream/active.conf
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'
docker logs pheeeew-dev-alloy 2>&1 | grep -i skipping
```

마지막 줄이 **비어 있어야** 해요. Alloy 가 두 색 로그를 다 수집한다는 뜻이에요.

## 검사

| 파일 | 검증하는 것 |
| --- | --- |
| `test-deploy-switch.py` | 워크플로의 배포 셸을 그대로 꺼내 전환과 복구 14분기 |
| `nginx/test-alb-health.py` | nginx 라우팅, 접근 제어, 장애 응답 |

둘 다 `BE Nginx CI` 에서 돌아요. 배포 셸을 바꾸면 테스트가 같은 셸을 읽으므로 따로 고칠 것이 없어요.
