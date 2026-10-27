#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
state="$root/.delivery/demo"
project='socrates-local-demo'
python3 "$root/scripts/demo/initialize.py"
compose=(docker compose -p "$project" --project-directory "$root" --env-file "$state/compose.env" -f "$root/docker-compose.prod.yml" -f "$root/deploy/demo/compose.demo.yml")
case "${1:-up}" in
  up)
    for component in backend frontend policy-api; do
      if ! docker image inspect "socratesai/$component:delivery-test" >/dev/null 2>&1; then
        context="$root"
        [[ "$component" != frontend ]] || context="$root/socratesai-frontend"
        [[ "$component" != policy-api ]] || context="$root/ml"
        docker build -t "socratesai/$component:delivery-test" "$context"
      fi
    done
    "${compose[@]}" config --quiet
    "${compose[@]}" up -d --no-build --wait --wait-timeout 180
    python3 "$root/scripts/demo/seed.py"
    echo 'Local demo: http://localhost:8088'
    echo "Credentials and example programs: $state/access.md (owner-only, not committed)"
    ;;
  down) "${compose[@]}" down ;;
  status) "${compose[@]}" ps ;;
  *) echo 'Usage: bash scripts/demo/demo.sh [up|down|status]' >&2; exit 2;;
esac
