#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${SERVER:?Set SERVER}"
: "${SSH_KEY:?Set SSH_KEY}"
: "${VITE_API_BASE_URL:?Set VITE_API_BASE_URL}"
: "${ADMIN_PUBLIC_URL:?Set ADMIN_PUBLIC_URL}"
: "${PUBLIC_SITE_URL:?Set PUBLIC_SITE_URL}"
# Builds are produced by GitHub Actions before deployment.
for app in bingo-admin bingo-website; do
  [[ -f "$ROOT/$app/dist/index.html" ]] || { echo "Build $app first" >&2; exit 1; }
done
remote="$(ssh -i "$SSH_KEY" "$SERVER" 'umask 077; mktemp -d /tmp/bingo-frontend.XXXXXXXX')"
[[ "$remote" =~ ^/tmp/bingo-frontend\.[a-zA-Z0-9]+$ ]]
trap 'ssh -i "$SSH_KEY" "$SERVER" "rm -rf '''$remote'''"' EXIT
scp -i "$SSH_KEY" -r "$ROOT/bingo-admin/dist" "$SERVER:$remote/admin"
scp -i "$SSH_KEY" -r "$ROOT/bingo-website/dist" "$SERVER:$remote/website"
ssh -i "$SSH_KEY" "$SERVER" "sudo -n bash -s -- '$remote'" <<'REMOTE'
set -euo pipefail
for app in admin website; do
  target="/var/www/bingo-$app"
  if [[ -d "$target" ]]; then
    rm -rf "$target.backup"
    cp -a "$target" "$target.backup"
  fi
  rm -rf "$target.new"
  mkdir -p "$target.new"
  cp -a "$1/$app/." "$target.new/"
  rm -rf "$target"
  mv "$target.new" "$target"
  chown -R www-data:www-data "$target"
  find "$target" -type d -exec chmod 755 {} +
  find "$target" -type f -exec chmod 644 {} +
done
REMOTE
curl -fsS --retry 3 --max-time 30 "$ADMIN_PUBLIC_URL/" >/dev/null
curl -fsS --retry 3 --max-time 30 "$PUBLIC_SITE_URL/" >/dev/null
curl -fsS --retry 3 --max-time 30 "$VITE_API_BASE_URL/actuator/health" | grep -q '"status":"UP"'
