#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || exit 1
cd /opt/socratesai/current
install -m 0644 deploy/host/socrates-{backup,health}.{service,timer} /etc/systemd/system/
docker volume create socrates-operations >/dev/null
operations_directory="$(docker volume inspect socrates-operations --format '{{.Mountpoint}}')"
chmod 0700 "$operations_directory"
cat > /etc/logrotate.d/socrates-operations <<EOF
$operations_directory/health.log {
    daily
    rotate 14
    size 1M
    compress
    missingok
    notifempty
    copytruncate
}
EOF
systemctl daemon-reload
systemctl enable --now socrates-backup.timer socrates-health.timer
