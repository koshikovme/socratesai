#!/usr/bin/env bash
# Only configuration and /bin/true: never compile/execute student programs on CI runners.
set -euo pipefail
image="${SANDBOX_IMAGE:-socratesai/judge0:delivery-test}"
directory="$(mktemp -d)"
trap 'rm -rf "$directory"' EXIT
chmod 0755 "$directory"
runtime=(docker run --rm --network none --read-only --cap-drop ALL --security-opt no-new-privileges:true --memory 128m)
if "${runtime[@]}" "$image" /bin/true > "$directory/missing.log" 2>&1; then
  echo 'Execution image accepted missing configuration' >&2; exit 1
fi
credential=PRIVATE_FIXTURE_SENTINEL_PRIVATE_FIXTURE_SENTINEL_PRIVATE_FIXTURE_SENTINEL
printf 'AUTHN_TOKEN=%s\nPOSTGRES_PASSWORD=%s\nREDIS_PASSWORD=%s\nSECRET_KEY_BASE=%s\n' \
  "$credential" "$credential" "$credential" "$credential" > "$directory/valid.conf"
chmod 0644 "$directory/valid.conf"
"${runtime[@]}" -v "$directory/valid.conf:/judge0.conf:ro" "$image" /bin/true > "$directory/valid.log" 2>&1
for required in AUTHN_TOKEN POSTGRES_PASSWORD REDIS_PASSWORD SECRET_KEY_BASE; do
  sed "/^${required}=/d" "$directory/valid.conf" > "$directory/missing.conf"
  chmod 0644 "$directory/missing.conf"
  if "${runtime[@]}" -v "$directory/missing.conf:/judge0.conf:ro" "$image" /bin/true > "$directory/rejected.log" 2>&1; then
    echo 'Execution image accepted an omitted credential' >&2; exit 1
  fi
done
if "${runtime[@]}" -v "$directory/valid.conf:/judge0.conf:ro" -v "$directory/valid.conf:/api/judge0.conf:ro" \
   "$image" /bin/true > "$directory/shadow.log" 2>&1; then
  echo 'Execution image accepted embedded configuration' >&2; exit 1
fi
if grep -q PRIVATE_FIXTURE_SENTINEL "$directory/"*.log; then
  echo 'Execution startup exposed credentials' >&2; exit 1
fi
echo 'Execution configuration is explicit, fail-closed and silent about credentials'
