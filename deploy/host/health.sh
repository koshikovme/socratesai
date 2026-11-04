#!/usr/bin/env bash
# Safe operational status only; never collect requests, environment values or diagnostics.
set -euo pipefail
operations_directory="$(docker volume inspect socrates-operations --format '{{.Mountpoint}}')"
trap 'status=$?; printf "%s health=%s\n" "$(date -u +%FT%TZ)" "$status" >> "$operations_directory/health.log"' EXIT
cd /opt/socratesai/current
docker compose --env-file /opt/socratesai/private/production.env -f docker-compose.prod.yml \
  -f deploy/host/compose.acme.yml ps --format json | python3 -c '
import json,sys
rows=[json.loads(line) for line in sys.stdin if line.strip()]
expected={"postgres","backend","policy-api","frontend-nginx"}
healthy={r["Service"] for r in rows if r.get("State")=="running" and r.get("Health")=="healthy"}
if healthy != expected: raise SystemExit("Container readiness failed")
'
public_origin="$(awk -F= '$1 == "APP_PUBLIC_ORIGIN" {print substr($0,index($0,"=")+1)}' /opt/socratesai/private/production.env)"
curl --fail --silent --show-error --max-time 10 "$public_origin/healthz" >/dev/null
df --output=pcent / | tail -1 | awk '{if (int($1)>=80) exit 1}'
echo 'SocratesAI readiness and disk checks passed'
