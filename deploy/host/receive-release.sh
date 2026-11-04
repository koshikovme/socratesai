#!/usr/bin/env bash
# Forced SSH command: this identity cannot run arbitrary commands, SCP or port forwarding.
set -euo pipefail
umask 077
case "${SSH_ORIGINAL_COMMAND:-}" in
  health) exec bash /opt/socratesai/current/deploy/host/health.sh ;;
  promote) ;;
  *) echo 'Unsupported deployment command' >&2; exit 1 ;;
esac
request_file="$(mktemp /opt/socratesai/private/promotion-XXXXXX.json)"
trap 'rm -f "$request_file"' EXIT
head -c 16385 > "$request_file"
[[ "$(stat -c %s "$request_file")" -le 16384 ]] || exit 1
# The service completes/rolls back even if the SSH client disconnects.
systemd-run --quiet --wait --pipe --collect --unit="socrates-promote-$(date +%s)-$RANDOM" \
  -p RuntimeMaxSec=15min -p UMask=0077 \
  /usr/bin/python3 /opt/socratesai/current/deploy/host/promote.py "$request_file"
