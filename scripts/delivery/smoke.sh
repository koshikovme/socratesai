#!/usr/bin/env bash
# Creates and deletes ONLY its unique disposable Compose project and volume.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
delivery_tmp="$(mktemp -d)"
project="socrates-smoke-$(date +%s)-${RANDOM}"
export BACKEND_IMAGE="${BACKEND_IMAGE:-socratesai/backend:delivery-test}"
export FRONTEND_IMAGE="${FRONTEND_IMAGE:-socratesai/frontend:delivery-test}"
export POLICY_IMAGE="${POLICY_IMAGE:-socratesai/policy-api:delivery-test}"
export DELIVERY_TMP="$delivery_tmp" DELIVERY_ROOT="$repo_root"
cleanup() {
  docker compose -p "$project" --env-file "$delivery_tmp/compose.env" -f "$repo_root/docker-compose.prod.yml" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$delivery_tmp"
}
trap cleanup EXIT
mkdir "$delivery_tmp/tls"
chmod 755 "$delivery_tmp/tls"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=localhost' \
  -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' \
  -keyout "$delivery_tmp/tls/privkey.pem" -out "$delivery_tmp/tls/fullchain.pem" >/dev/null 2>&1
# Ephemeral test key only; operator certificates use UID/GID 101 + 0640 permissions.
chmod 644 "$delivery_tmp/tls/privkey.pem"
python3 - <<'PY'
import base64,os,secrets,socket
from pathlib import Path
ports=[]
for _ in range(2):
 with socket.socket() as s:
  s.bind(('127.0.0.1',0)); ports.append(s.getsockname()[1])
values={
 'BACKEND_IMAGE':os.environ['BACKEND_IMAGE'],'FRONTEND_IMAGE':os.environ['FRONTEND_IMAGE'],'POLICY_IMAGE':os.environ['POLICY_IMAGE'],
 'APP_DOMAIN':'localhost','APP_PUBLIC_ORIGIN':f'https://localhost:{ports[1]}','EDGE_BIND_ADDRESS':'127.0.0.1',
 'EDGE_HTTP_PORT':str(ports[0]),'EDGE_HTTPS_PORT':str(ports[1]),'TLS_DIRECTORY':os.environ['DELIVERY_TMP']+'/tls',
 'POSTGRES_DB':'delivery_test','POSTGRES_USER':'delivery_test','POSTGRES_PASSWORD':secrets.token_hex(32),'POSTGRES_ADMIN_PASSWORD':secrets.token_hex(32),
 'APP_JWT_SECRET':base64.b64encode(secrets.token_bytes(64)).decode(),'APP_RUNTIME_CONFIG_FILE':os.environ['DELIVERY_ROOT']+'/deploy/runtime-catalog.yaml.example',
 'APP_ADMIN_BOOTSTRAP_ENABLED':'true','APP_ADMIN_BOOTSTRAP_EMAIL':'operator@example.test','APP_ADMIN_BOOTSTRAP_PASSWORD':secrets.token_hex(24),
 'APP_ADMIN_BOOTSTRAP_FULL_NAME':'Delivery smoke operator','APP_JUDGE0_ENABLED':'false','APP_JUDGING_ENABLED':'false'
}
p=Path(os.environ['DELIVERY_TMP'])/'compose.env';p.write_text(''.join(f'{k}={v}\n' for k,v in values.items()));p.chmod(0o600)
PY
compose=(docker compose -p "$project" --env-file "$delivery_tmp/compose.env" -f "$repo_root/docker-compose.prod.yml")
if docker run --rm --network none -e SPRING_PROFILES_ACTIVE=prod -e APP_JWT_SECRET=PRIVATE_SENTINEL \
    "$BACKEND_IMAGE" > "$delivery_tmp/invalid-startup.log" 2>&1; then
  echo 'Invalid production settings unexpectedly started' >&2; exit 1
fi
grep -q 'Invalid production configuration: app.jwt.secret' "$delivery_tmp/invalid-startup.log"
if grep -Eq 'PRIVATE_SENTINEL|HikariPool|Starting Flyway' "$delivery_tmp/invalid-startup.log"; then
  echo 'Startup validation exposed a value or ran after database initialization' >&2; exit 1
fi
"${compose[@]}" config --quiet
"${compose[@]}" up -d --no-build --wait --wait-timeout 180
python3 "$repo_root/scripts/delivery/smoke_http.py"
"${compose[@]}" exec -T backend bash -c '! command -v javac && ! java --list-modules | grep -q "^jdk.compiler@"'
for service in postgres policy-api backend; do
  container_id="$("${compose[@]}" ps -q "$service")"
  docker inspect "$container_id" --format '{{json .HostConfig.PortBindings}}' | python3 -c 'import json,sys; assert not any((json.load(sys.stdin) or {}).values()), "Private host port binding detected"'
done
versions="$("${compose[@]}" exec -T postgres psql -U delivery_test -d delivery_test -tAc 'select count(*) from flyway_schema_history where success')"
[[ "$versions" -ge 12 ]]
superuser="$("${compose[@]}" exec -T postgres psql -U delivery_test -d delivery_test -tAc 'select rolsuper from pg_roles where rolname=current_user')"
[[ "$superuser" == 'f' ]]
echo "Isolated TLS/API/STOMP/model/readiness smoke passed; Flyway migrations: $versions."
