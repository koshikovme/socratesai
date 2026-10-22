#!/bin/sh
set -eu
# Only DNS hostnames; never interpolate arbitrary Nginx directives.
case "${APP_DOMAIN:-}" in ''|*[!a-zA-Z0-9.-]*|.*|*.) echo 'Invalid edge domain configuration' >&2; exit 1;; esac
[ "${#APP_DOMAIN}" -le 253 ] || exit 1
[ -r /run/tls/fullchain.pem ] && [ -r /run/tls/privkey.pem ] || { echo 'Readable external TLS certificate and key are required' >&2; exit 1; }
APP_PUBLIC_ORIGIN="${APP_PUBLIC_ORIGIN:-https://$APP_DOMAIN}"
case "$APP_PUBLIC_ORIGIN" in
  "https://$APP_DOMAIN") ;;
  "https://$APP_DOMAIN":*)
    edge_port="${APP_PUBLIC_ORIGIN##*:}"
    case "$edge_port" in ''|*[!0-9]*) echo 'Invalid edge origin' >&2; exit 1;; esac
    [ "$edge_port" -ge 1 ] && [ "$edge_port" -le 65535 ] || exit 1;;
  *) echo 'Invalid edge origin' >&2; exit 1;;
esac
export APP_PUBLIC_ORIGIN APP_WS_ORIGIN="wss://${APP_PUBLIC_ORIGIN#https://}"
envsubst '${APP_DOMAIN} ${APP_PUBLIC_ORIGIN} ${APP_WS_ORIGIN}' < /etc/nginx/templates/frontend.conf.template > /tmp/frontend.conf
nginx -t
exec nginx -g 'daemon off;'
