#!/bin/sh
# Production blue/green release; caller must hold the production operation lock.
set -eu
cd /opt/pheeeew
: "${APP_IMAGE:?}" "${LOCK_TOKEN:?}"
python3 press-prod-lock.py check "$LOCK_TOKEN"
sh press-prod-deploy-guard.sh /opt/pheeeew

POINTER=nginx/upstream/active.conf
BACKUP=nginx/upstream/active.conf.bak
PROBE_HOST=api.pheeeew.com
PROBE_PATH="/api/v3/devices/nicknames/availability?nickname=probe"
PROBE_URL="https://$PROBE_HOST$PROBE_PATH"
NGINX="docker compose -f compose.nginx.yml"
APPS="docker compose -f compose.yml"
DRAIN_WORKERS=
RELEASE_LOCK_ON_FAILURE=no

no_pending_transition() {
  for record in .press-deploy.json .press-deploy.json.new "$BACKUP"; do
    if [ -e "$record" ] || [ -L "$record" ]; then
      return 1
    fi
  done
}

finish() {
  status=$1
  trap - 0 HUP INT TERM
  if [ "$status" -ne 0 ]; then
    if [ "$RELEASE_LOCK_ON_FAILURE" = yes ] && no_pending_transition \
        && [ "$(read_pointer)" = "$ACTIVE" ] && wait_served "$ACTIVE"; then
      if python3 press-prod-lock.py release "$LOCK_TOKEN"; then
        echo "안전한 배포 중단을 확인하여 현재 실행의 작업 잠금을 해제했습니다."
      else
        echo "작업 잠금 해제를 확인하지 못했습니다. 잠금 상태를 수동 확인하세요." >&2
      fi
    else
      echo "배포 상태가 불확실하여 작업 잠금을 유지합니다. 기록과 실제 서버를 확인하세요." >&2
    fi
  fi
  exit "$status"
}

# Release only in this remote process, never merely because the SSH caller failed.
trap 'finish "$?"' 0
trap 'RELEASE_LOCK_ON_FAILURE=no; exit 129' HUP
trap 'RELEASE_LOCK_ON_FAILURE=no; exit 130' INT
trap 'RELEASE_LOCK_ON_FAILURE=no; exit 143' TERM

abort_prepared_deployment() {
  failure=$1
  if python3 press-generation.py abort-prepared "pheeeew-prod-$ACTIVE"; then
    RELEASE_LOCK_ON_FAILURE=yes
  fi
  exit "$failure"
}

if command -v python3 >/dev/null 2>&1; then
  JSON_TOOL=python3
elif command -v jq >/dev/null 2>&1; then
  JSON_TOOL=jq
else
  echo "JSON 검증 도구(python3, jq)가 없어 전환을 검증할 수 없습니다. 배포하지 않습니다."
  exit 1
fi

read_pointer() {
  sed -n 's/.*set \$upstream_app \([a-z-]*\);.*/\1/p' "$POINTER"
}

available_is_boolean() {
  if [ "$JSON_TOOL" = python3 ]; then
    python3 -c "import json, sys; body = json.load(open(sys.argv[1])); sys.exit(0 if isinstance(body, dict) and isinstance(body.get('available'), bool) else 1)" "$1" 2>/dev/null
  else
    jq -e 'type == "object" and has("available") and (.available | type) == "boolean"' "$1" >/dev/null 2>&1
  fi
}

served() {
  code=$(curl -sS --resolve "$PROBE_HOST:443:127.0.0.1" \
              --connect-timeout 2 --max-time 5 \
              -o /tmp/probe.body -D /tmp/probe.head \
              -w '%{http_code}' "$PROBE_URL") || return 1
  [ "$code" = 200 ] || return 1
  available_is_boolean /tmp/probe.body || return 1
  sed -n 's/^[Xx]-[Uu]pstream:[[:space:]]*//p' /tmp/probe.head | tr -d '\r\n'
}

target_answers_api() {
  $NGINX exec -T nginx \
       timeout 5 wget -q -T 5 -O - "http://$TARGET:8080$PROBE_PATH" </dev/null >/tmp/target.body 2>/dev/null \
    || return 1
  available_is_boolean /tmp/target.body
}

wait_target_api() {
  deadline=$(($(date +%s) + 30))
  while :; do
    target_answers_api && return 0
    [ "$(date +%s)" -ge "$deadline" ] && break
    sleep 1
  done
  return 1
}

wait_served() {
  want=$1
  deadline=$(($(date +%s) + 30))
  got=
  while :; do
    got=$(served) || got=
    [ "$got" = "$want" ] && return 0
    [ "$(date +%s)" -ge "$deadline" ] && break
    sleep 1
  done
  echo "엣지가 $want 를 서비스하지 않습니다. 마지막 확인: ${got:-정상 응답 없음}"
  return 1
}

read_nginx_processes() {
  out=$($NGINX exec -T nginx \
          sh -c "ps -o pid=,args= || exit 9; echo COUNTED" </dev/null 2>/dev/null) || return 1
  case "$out" in
    *COUNTED) ;;
    *) return 1 ;;
  esac
  printf '%s\n' "$out" | awk '$1 ~ /^[0-9]+$/ { seen=1 } END { exit !seen }' || return 1
  printf '%s\n' "$out"
}

capture_workers() {
  # Reload only acknowledges a signal. Capture workers before their titles can change.
  DRAIN_WORKERS=
  if processes=$(read_nginx_processes); then
    DRAIN_WORKERS=$(printf '%s\n' "$processes" | \
      awk '$1 ~ /^[0-9]+$/ && $2 == "nginx:" && $3 == "worker" && $4 == "process" { printf "%s ", $1 }')
  fi
}

count_previous_workers() {
  # Empty/failed snapshots are unknown, never proof that requests have drained.
  [ -n "$DRAIN_WORKERS" ] || return 1
  processes=$(read_nginx_processes) || return 1
  printf '%s\n' "$processes" | awk -v previous="$DRAIN_WORKERS" '
    BEGIN { n=split(previous, pids); for (i=1; i<=n; i++) old[pids[i]]=1 }
    $1 in old { count++ }
    END { print count+0 }'
}

wait_drained() {
  deadline=$(($(date +%s) + 60))
  checked=no
  while :; do
    if left=$(count_previous_workers); then
      case "$left" in
        ''|*[!0-9]*) ;;
        *)
          checked=yes
          [ "$left" -eq 0 ] && return 0 ;;
      esac
    fi
    [ "$(date +%s)" -ge "$deadline" ] && break
    sleep 1
  done
  [ "$checked" = yes ] \
    && echo "   제한 시간 안에 종료 중인 nginx 워커가 사라지지 않았습니다." \
    || echo "   nginx 워커 상태를 한 번도 확인하지 못했습니다."
  return 1
}

report_mismatch() {
  echo "!! 복구에 실패했습니다. 전환 파일과 실제 서비스가 어긋날 수 있습니다."
  echo "   전환 파일: $(read_pointer)"
  echo "   엣지 응답: $(served || echo '정상 응답 없음')"
  echo "   $ACTIVE 와 $TARGET 을 모두 남겨 둡니다. 수동 확인이 필요합니다."
}

restore_pointer() {
  [ -f "$BACKUP" ] || return 1
  cat "$BACKUP" > "$POINTER" || return 1
  $NGINX exec -T nginx nginx -t </dev/null || return 1
  capture_workers
  $NGINX exec -T nginx nginx -s reload </dev/null || return 1
  wait_served "$ACTIVE" || return 1
}

rollback() {
  # A failed psql response may follow a committed UPDATE. Verify restoration before cleanup.
  if ! python3 press-generation.py restore "pheeeew-prod-$TARGET"; then
    report_mismatch
    return 1
  fi
  if ! restore_pointer; then
    report_mismatch
    return 1
  fi
  if ! python3 press-generation.py open-active "pheeeew-prod-$ACTIVE"; then
    report_mismatch
    return 1
  fi
  echo "$ACTIVE 로 복구했고 정상 응답을 확인했습니다."
  if wait_drained; then
    echo "$TARGET 으로 향하던 요청이 모두 끝났습니다."
  else
    echo "$TARGET 으로 향하던 요청이 끝났는지 확인하지 못했습니다."
  fi
  if ! python3 press-generation.py cleanup-target "pheeeew-prod-$TARGET"; then
    report_mismatch
    return 1
  fi
  python3 press-generation.py verify-active "pheeeew-prod-$ACTIVE" || return 1
  rm -f .press-deploy.json "$BACKUP" || return 1
  no_pending_transition || return 1
  [ "$(read_pointer)" = "$ACTIVE" ] && wait_served "$ACTIVE" || return 1
  RELEASE_LOCK_ON_FAILURE=yes
  echo "$TARGET 을 정리했습니다."
}

ACTIVE=$(read_pointer)
case "$ACTIVE" in
  app-blue|app-green) ;;
  *) echo "전환 파일의 값이 올바르지 않습니다: '${ACTIVE:-빈 값}'. 전환하지 않습니다."; exit 1 ;;
esac
[ "$ACTIVE" = app-blue ] && TARGET=app-green || TARGET=app-blue
echo "현재 $ACTIVE, 새로 띄울 것 $TARGET"

command -v python3 >/dev/null
command -v psql >/dev/null
command -v openssl >/dev/null
if ! no_pending_transition; then
  echo "미완료 전환 기록이 있습니다. DB와 프록시를 수동 확인하기 전에는 재배포하지 않습니다."
  exit 1
fi
# Booting a candidate never changes DB ownership or competition activation.
wait_served "$ACTIVE" || { echo "현재 트래픽과 전환 파일이 다릅니다. 배포하지 않습니다."; exit 1; }
RELEASE_LOCK_ON_FAILURE=yes
# A preflight check is not a memory reservation.
check_memory() {
  available_kib=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
  case "$available_kib" in ''|*[!0-9]*) echo "가용 메모리를 확인할 수 없습니다."; return 1;; esac
  if [ "$available_kib" -lt 786432 ]; then
    echo "후보 서버를 기동할 메모리 여유가 부족합니다. 기존 서버를 유지합니다."
    return 1
  fi
}
check_memory || exit 1
RELEASE_LOCK_ON_FAILURE=no
python3 press-generation.py prepare "pheeeew-prod-$ACTIVE" "$TARGET"
APPS="docker compose --env-file .env --env-file press-generations.env -f compose.yml"
python3 press-prod-preflight.py "$TARGET" || abort_prepared_deployment "$?"

if $APPS pull "$TARGET"; then
  :
else
  failure=$?
  echo "새 이미지를 가져오지 못했습니다. 전환하지 않습니다."
  abort_prepared_deployment "$failure"
fi
contract=$(docker image inspect "$APP_IMAGE" --format '{{index .Config.Labels "com.pheeeew.press-generation-contract"}}') || contract=
if [ "$contract" != 2 ]; then
  echo "운영 일반 배포는 계약 2 이미지만 허용합니다. 구버전 복귀는 별도 절차가 필요합니다."
  abort_prepared_deployment 1
fi

if ! check_memory; then
  abort_prepared_deployment 1
fi
python3 press-prod-preflight.py "$TARGET" || abort_prepared_deployment "$?"

if ! $APPS up -d --wait --wait-timeout 180 "$TARGET"; then
  echo "===== 새 버전 기동 실패 — 로그 ====="
  $APPS logs --tail=100 "$TARGET" 2>&1 | grep -a -v -F -e 'http_request_failed' -e 'scheduled_task_failed' || true
  if python3 press-generation.py record-target "pheeeew-prod-$TARGET" \
      && python3 press-generation.py cleanup-target "pheeeew-prod-$TARGET"; then
    python3 press-generation.py verify-active "pheeeew-prod-$ACTIVE" || exit 1
    rm -f .press-deploy.json || exit 1
    RELEASE_LOCK_ON_FAILURE=yes
  else
    report_mismatch
  fi
  echo "전환하지 않고 끝냅니다. 트래픽은 $ACTIVE 에 그대로 있습니다."
  exit 1
fi

python3 press-generation.py record-target "pheeeew-prod-$TARGET" || { report_mismatch; exit 1; }

if ! wait_target_api; then
  echo "새 버전이 최소 API 를 응답하지 않습니다."
  $APPS logs --tail=50 "$TARGET" 2>&1 | grep -a -v -F -e 'http_request_failed' -e 'scheduled_task_failed' || true
  if python3 press-generation.py cleanup-target "pheeeew-prod-$TARGET"; then
    python3 press-generation.py verify-active "pheeeew-prod-$ACTIVE" || exit 1
    rm -f .press-deploy.json || exit 1
    RELEASE_LOCK_ON_FAILURE=yes
  else
    report_mismatch
  fi
  echo "전환하지 않고 끝냅니다. 트래픽은 $ACTIVE 에 그대로 있습니다."
  exit 1
fi
echo "$TARGET 이 최소 API 를 응답합니다."

if [ "$(read_pointer)" != "$ACTIVE" ] || ! wait_served "$ACTIVE"; then
  report_mismatch
  exit 1
fi
cp "$POINTER" "$BACKUP"
if ! python3 press-generation.py handoff "pheeeew-prod-$TARGET"; then
  echo "서버 세대 전환을 확인하지 못했습니다. 실제 DB 상태를 재확인하고 복구합니다."
  rollback || true
  exit 1
fi
if ! python3 press-generation.py close-active "pheeeew-prod-$ACTIVE"; then
  echo "기존 WS 연결 정리를 확인하지 못했습니다. 검증된 원본 복귀만 시도합니다."
  rollback || true
  exit 1
fi
printf 'set $upstream_app %s;\n' "$TARGET" > "$POINTER"

if ! $NGINX exec -T nginx nginx -t </dev/null; then
  echo "전환 설정이 올바르지 않습니다."
  rollback || true
  echo "전환하지 않고 끝냅니다. 트래픽은 $ACTIVE 에 그대로 있습니다."
  exit 1
fi

capture_workers
if ! $NGINX exec -T nginx nginx -s reload </dev/null; then
  echo "reload 가 실패했습니다. 전환 파일을 되돌립니다."
  rollback || true
  exit 1
fi

if ! wait_served "$TARGET"; then
  echo "전환이 반영되지 않았습니다. 전환 파일을 되돌립니다."
  rollback || true
  exit 1
fi
echo "$TARGET 으로 전환했고 정상 응답을 확인했습니다."

if ! python3 press-generation.py verify "pheeeew-prod-$TARGET"; then
  report_mismatch
  exit 1
fi

if ! python3 press-generation.py open-target "pheeeew-prod-$TARGET"; then
  echo "입력 개방을 확인하지 못했습니다. 개방 시도 전인 경우에만 원본 복귀를 허용합니다."
  if ! rollback; then
    python3 press-generation.py emergency-stop "pheeeew-prod-$TARGET" || true
    report_mismatch
  fi
  exit 1
fi

if wait_drained; then
  echo "이전 요청이 모두 끝났습니다."
else
  echo "이전 요청이 끝났는지 확인하지 못했습니다. $ACTIVE 를 graceful 하게 내립니다."
  echo "   종료 유예 30초 안에서 앱이 남은 요청을 스스로 마무리합니다."
fi
python3 press-generation.py cleanup-active "pheeeew-prod-$ACTIVE" || { report_mismatch; exit 1; }
echo "앱 전환 완료: $APP_IMAGE ($TARGET). 모니터링 배포 후 마지막 확인을 기다립니다."
