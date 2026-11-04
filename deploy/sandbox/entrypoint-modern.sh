#!/usr/bin/env bash
set -euo pipefail
[[ -r /judge0.conf && ! -e /api/judge0.conf ]] || { echo 'External execution configuration required' >&2; exit 1; }
set -o allexport
source /judge0.conf
set +o allexport
: "${AUTHN_TOKEN:?Execution authentication required}" "${POSTGRES_PASSWORD:?Execution database credential required}" \
  "${REDIS_PASSWORD:?Execution queue credential required}" "${SECRET_KEY_BASE:?Execution signing material required}"
[[ ${#AUTHN_TOKEN} -ge 32 && ${#POSTGRES_PASSWORD} -ge 16 && ${#REDIS_PASSWORD} -ge 16 && ${#SECRET_KEY_BASE} -ge 64 ]] \
  || { echo 'Execution credentials do not meet minimum lengths' >&2; exit 1; }
source /api/scripts/load-config
exec "$@"
