#!/usr/bin/env bash
set -euo pipefail
# Scanner and database cache are isolated from application containers and credentials.
scanner='aquasec/trivy:0.76.1@sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c'
report_dir="${1:-.delivery/scans}"
mkdir -p "$report_dir"
report_dir="$(cd "$report_dir" && pwd)"
result=0
for name in backend frontend policy-api; do
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v socrates-trivy-cache:/root/.cache/ \
    -v "$report_dir:/reports" "$scanner" image --quiet --scanners vuln --ignore-unfixed \
    --severity HIGH,CRITICAL --exit-code 1 --format json --output "/reports/$name.json" \
    "socratesai/$name:delivery-test" || result=1
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v socrates-trivy-cache:/root/.cache/ \
    -v "$report_dir:/reports" "$scanner" image --quiet --format cyclonedx --output "/reports/$name.cdx.json" \
    "socratesai/$name:delivery-test"
done
# Record the stock database image separately: its unused gosu Go helper has upstream advisories.
# Do not suppress those findings or silently claim an all-stack zero. Runtime is explicitly UID 70.
postgres_image="$(awk '/image: postgres:16-alpine@sha256:/ {print $2}' docker-compose.prod.yml)"
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v socrates-trivy-cache:/root/.cache/ \
  -v "$report_dir:/reports" "$scanner" image --quiet --scanners vuln --ignore-unfixed \
  --severity HIGH,CRITICAL --format json --output /reports/postgres-upstream.json "$postgres_image"
exit "$result"
