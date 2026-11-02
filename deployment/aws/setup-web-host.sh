#!/usr/bin/env bash
set -euo pipefail
# Run once on the Bingo host as root, then rerun when proxy settings change.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE_DOMAIN="${SITE_DOMAIN:-bingo.bakaroo.com}"
ADMIN_DOMAIN="${ADMIN_DOMAIN:-admin.bingo.bakaroo.com}"
API_DOMAIN="${API_DOMAIN:-api.bingo.bakaroo.com}"
export SITE_DOMAIN ADMIN_DOMAIN API_DOMAIN
[[ "$EUID" -eq 0 ]] || { echo 'Run setup-web-host.sh as root' >&2; exit 1; }
for command in certbot nginx envsubst curl python3; do
  command -v "$command" >/dev/null || { echo "Missing prerequisite: $command" >&2; exit 1; }
done
python3 - <<'PY'
import os,re
hosts=[os.environ[k] for k in ('SITE_DOMAIN','ADMIN_DOMAIN','API_DOMAIN')]
if len(set(hosts)) != 3 or not all(re.fullmatch(r'[a-z0-9]+(?:[.-][a-z0-9]+)*',h) for h in hosts):
    raise SystemExit('Provide three distinct valid DNS hostnames')
PY
work="$(mktemp -d)"
config=/etc/nginx/sites-available/bingo.conf
link=/etc/nginx/sites-enabled/bingo.conf
old_config=false
old_link=false
if [[ -e "$config" || -L "$config" ]]; then cp -a "$config" "$work/config"; old_config=true; fi
if [[ -e "$link" || -L "$link" ]]; then cp -a "$link" "$work/link"; old_link=true; fi
changed=false
cleanup() {
  status=$?
  trap - EXIT
  if [[ "$status" -ne 0 && "$changed" == true ]]; then
    rm -f "$link" "$config"
    if [[ "$old_config" == true ]]; then cp -a "$work/config" "$config"; fi
    if [[ "$old_link" == true ]]; then cp -a "$work/link" "$link"; fi
    if nginx -t; then systemctl reload nginx; else status=1; fi
  fi
  rm -rf "$work"
  exit "$status"
}
trap cleanup EXIT
install -d -m 755 /var/www/letsencrypt/.well-known/acme-challenge
# Install only Bingo's HTTP challenge vhost until its certificate is available.
cat > "$work/http.conf" <<EOFHTTP
server {
    listen 80;
    listen [::]:80;
    server_name $SITE_DOMAIN $ADMIN_DOMAIN $API_DOMAIN;
    location ^~ /.well-known/acme-challenge/ { root /var/www/letsencrypt; }
    location / { return 404; }
}
EOFHTTP
changed=true
install -m 644 "$work/http.conf" "$config"
ln -sfn "$config" "$link"
nginx -t
systemctl reload nginx
# Reuse the existing Certbot account; this does not renew other applications.
certbot certonly --non-interactive --webroot -w /var/www/letsencrypt \
  --cert-name bingo-platform --keep-until-expiring \
  -d "$SITE_DOMAIN" -d "$ADMIN_DOMAIN" -d "$API_DOMAIN" \
  --deploy-hook 'nginx -t && systemctl reload nginx'
envsubst '${SITE_DOMAIN} ${ADMIN_DOMAIN} ${API_DOMAIN}' \
  < "$SCRIPT_DIR/nginx-bingo.conf.template" > "$work/https.conf"
install -m 644 "$work/https.conf" "$config"
nginx -t
systemctl reload nginx
for domain in "$SITE_DOMAIN" "$ADMIN_DOMAIN" "$API_DOMAIN"; do
  curl --fail --silent --show-error --max-time 30 \
    --retry 12 --retry-all-errors --retry-delay 2 "https://$domain/healthz" >/dev/null
done
# Existing Certbot timers perform renewal; the certificate stores its deploy hook.
if ! systemctl is-enabled --quiet certbot.timer && ! systemctl is-enabled --quiet snap.certbot.renew.timer; then
  systemctl enable --now certbot.timer
fi
changed=false
echo 'Bingo Nginx routing and verified HTTPS are ready'
