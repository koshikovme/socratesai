#!/usr/bin/env bash
# Provider tickets, source, stdin and diagnostics expire together after 24 hours.
set -euo pipefail
cd /opt/socratesai
docker compose --env-file private/judge0.conf --env-file private/images.env -f compose.yml \
  exec -T db sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' <<'SQL'
DELETE FROM submissions WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '24 hours';
SQL
