#!/usr/bin/env bash
# Configure only the encrypted application-to-executor link; keys stay on each host.
set -euo pipefail
[[ "$EUID" -eq 0 ]] || exit 1
: "${LINK_ADDRESS:?}" "${PEER_ADDRESS:?}" "${PEER_ENDPOINT:?}" "${PEER_PUBLIC_KEY:?}" "${PEER_UNDERLAY_ADDRESS:?}"
install -d -m 0700 /etc/wireguard
if [[ ! -f /etc/wireguard/socrates.key ]]; then
  umask 077
  wg genkey > /etc/wireguard/socrates.key
fi
umask 077
cat > /etc/wireguard/wg0.conf <<EOF
[Interface]
Address = ${LINK_ADDRESS}/32
ListenPort = 51820
PrivateKey = $(cat /etc/wireguard/socrates.key)

[Peer]
PublicKey = ${PEER_PUBLIC_KEY}
AllowedIPs = ${PEER_ADDRESS}/32
Endpoint = ${PEER_ENDPOINT}:51820
PersistentKeepalive = 25
EOF
ufw allow from "$PEER_UNDERLAY_ADDRESS" to any port 51820 proto udp
systemctl enable --now wg-quick@wg0
# These hosts already have working operator/deployment public keys.
install -d -m 0755 /etc/ssh/sshd_config.d
cat > /etc/ssh/sshd_config.d/00-socrates.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
MaxAuthTries 3
EOF
sshd -t
systemctl reload ssh
