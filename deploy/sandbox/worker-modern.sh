#!/usr/bin/env bash
set -euo pipefail
bundle exec rake resque:scheduler &
scheduler=$!
bundle exec rails resque:workers &
worker=$!
cleanup() { kill -TERM "$worker" "$scheduler" 2>/dev/null || true; wait "$worker" "$scheduler" 2>/dev/null || true; }
trap cleanup EXIT TERM INT
wait -n "$worker" "$scheduler"
