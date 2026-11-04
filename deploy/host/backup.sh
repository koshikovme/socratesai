#!/usr/bin/env bash
# An operator-held recipient certificate encrypts every dump before it reaches disk.
set -euo pipefail
umask 077
exec 9>/run/lock/socrates-backup.lock
flock -w 45 9
cd /opt/socratesai/current
target="/opt/socratesai/backups/socrates-$(date -u +%Y%m%dT%H%M%SZ).dump.cms"
trap 'rm -f "${target}.tmp"' EXIT
docker compose --env-file /opt/socratesai/private/production.env -f docker-compose.prod.yml \
  exec -T postgres sh -c 'exec pg_dump -U "$APP_DATABASE_USERNAME" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
  | openssl cms -encrypt -aes-256-gcm -binary -outform DER -out "${target}.tmp" /opt/socratesai/private/backup-recipient.pem
test -s "${target}.tmp"
mv "${target}.tmp" "$target"
sha256sum "$target" > "${target}.sha256"
find /opt/socratesai/backups -maxdepth 1 -type f -name 'socrates-*.dump.cms*' -mtime +13 -delete
echo "Encrypted backup complete: $(basename "$target")"
