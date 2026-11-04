#!/usr/bin/env bash
# Certbot deploy hook. Copy real files because the edge does not mount Certbot's symlink tree.
set -euo pipefail
certificate_directory=${RENEWED_LINEAGE:?Certbot must supply the renewed certificate path}
install -d -m 0750 -o root -g 101 /opt/socratesai/private/tls
install -m 0644 "$certificate_directory/fullchain.pem" /opt/socratesai/private/tls/fullchain.pem.new
install -m 0640 -o root -g 101 "$certificate_directory/privkey.pem" /opt/socratesai/private/tls/privkey.pem.new
mv /opt/socratesai/private/tls/fullchain.pem.new /opt/socratesai/private/tls/fullchain.pem
mv /opt/socratesai/private/tls/privkey.pem.new /opt/socratesai/private/tls/privkey.pem
cd /opt/socratesai/current
compose=(docker compose --env-file /opt/socratesai/private/production.env -f docker-compose.prod.yml -f deploy/host/compose.acme.yml)
if [[ -n "$("${compose[@]}" ps -q frontend-nginx)" ]]; then
  "${compose[@]}" exec -T frontend-nginx nginx -t
  "${compose[@]}" exec -T frontend-nginx nginx -s reload
fi
