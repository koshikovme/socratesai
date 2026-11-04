#!/usr/bin/env bash
# Bootstrap an explicitly authorized, dedicated Ubuntu application/execution host.
set -euo pipefail
[[ "$EUID" -eq 0 ]] || { echo 'Run as root on the dedicated host' >&2; exit 1; }
. /etc/os-release
[[ "$ID" == ubuntu ]] || { echo 'This bootstrap supports Ubuntu hosts' >&2; exit 1; }
export DEBIAN_FRONTEND=noninteractive
apt-get update
if [[ "${SOCRATES_OS_UPGRADE:-false}" == true ]]; then
  apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade
fi
apt-get install -y ca-certificates curl gnupg ufw fail2ban unattended-upgrades certbot wireguard-tools jq python3 unzip
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
install -d -m 0755 /etc/docker /etc/systemd/journald.conf.d /etc/systemd/system/docker.service.d
if [[ ! -e /etc/docker/daemon.json ]]; then
  cat > /etc/docker/daemon.json <<'EOF'
{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"},"live-restore":true}
EOF
fi
cat > /etc/systemd/journald.conf.d/socrates-retention.conf <<'EOF'
[Journal]
SystemMaxUse=150M
RuntimeMaxUse=50M
MaxRetentionSec=7day
EOF
cat > /etc/systemd/system/docker.service.d/limits.conf <<'EOF'
[Service]
LimitNOFILE=65536
EOF
cat > /etc/sysctl.d/90-socrates.conf <<'EOF'
fs.file-max=2097152
net.ipv4.ip_forward=1
vm.swappiness=10
EOF
sysctl --system >/dev/null
systemctl daemon-reload
systemctl enable --now docker fail2ban
systemctl restart systemd-journald
ufw allow 22/tcp
ufw default deny incoming
ufw default allow outgoing
ufw --force enable
install -d -m 0700 /opt/socratesai /opt/socratesai/private /opt/socratesai/backups
docker version --format '{{.Server.Version}}'
docker compose version
