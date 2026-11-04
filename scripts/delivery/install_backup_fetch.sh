#!/usr/bin/env bash
# Operator workstation only; the timer catches up when this machine is online/logged in.
set -euo pipefail
repository="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
operator_units="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
install -d -m 0700 "$operator_units"
cat > "$operator_units/socrates-backup-fetch.service" <<EOF
[Unit]
Description=Fetch encrypted SocratesAI backups to the operator workstation

[Service]
Type=oneshot
UMask=0077
ExecStart=/usr/bin/python3 "$repository/scripts/delivery/fetch_backups.py" --identity-file "$repository/.delivery/ssh/deploy_ed25519" --destination "$repository/.delivery/production-backups"
EOF
cat > "$operator_units/socrates-backup-fetch.timer" <<'EOF'
[Unit]
Description=Daily encrypted SocratesAI off-host copy when the operator workstation is online

[Timer]
OnCalendar=*-*-* 03:00:00 UTC
Persistent=true
RandomizedDelaySec=300

[Install]
WantedBy=timers.target
EOF
systemctl --user daemon-reload
systemctl --user enable --now socrates-backup-fetch.timer
systemctl --user start socrates-backup-fetch.service
