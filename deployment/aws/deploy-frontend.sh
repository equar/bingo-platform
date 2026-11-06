#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
: "${SERVER:?Set SERVER}"
: "${SSH_KEY:?Set SSH_KEY}"
case "${1:-all}" in
  all) apps=(admin website) ;;
  admin) apps=(admin) ;;
  website) apps=(website) ;;
  *) echo 'Usage: deploy-frontend.sh [all|admin|website]' >&2; exit 1 ;;
esac
: "${VITE_API_BASE_URL:?Set VITE_API_BASE_URL}"
urls=()
for app in "${apps[@]}"; do
  if [[ "$app" == admin ]]; then
    : "${ADMIN_PUBLIC_URL:?Set ADMIN_PUBLIC_URL}"
    urls+=("$ADMIN_PUBLIC_URL")
  else
    : "${PUBLIC_SITE_URL:?Set PUBLIC_SITE_URL}"
    urls+=("$PUBLIC_SITE_URL")
  fi
done
# Verify HTTPS before replacing static files; the web host setup is a prerequisite.
for url in "${urls[@]}" "$VITE_API_BASE_URL"; do
  if ! curl --silent --show-error --max-time 30 --output /dev/null "$url/"; then
    echo "HTTPS preflight failed for $url. Run deployment/aws/setup-web-host.sh on the host to configure Bingo's certificate and virtual hosts." >&2
    exit 1
  fi
done
# Builds are produced by GitHub Actions before deployment.
for app in "${apps[@]}"; do
  [[ -f "$ROOT/bingo-$app/dist/index.html" ]] || { echo "Build bingo-$app first" >&2; exit 1; }
done
remote="$(ssh -i "$SSH_KEY" "$SERVER" 'umask 077; mktemp -d /tmp/bingo-frontend.XXXXXXXX')"
[[ "$remote" =~ ^/tmp/bingo-frontend\.[a-zA-Z0-9]+$ ]]
cleanup() {
  ssh -i "$SSH_KEY" "$SERVER" "rm -rf '$remote'"
}
trap cleanup EXIT
for app in "${apps[@]}"; do
  scp -i "$SSH_KEY" -r "$ROOT/bingo-$app/dist" "$SERVER:$remote/$app"
done
ssh -i "$SSH_KEY" "$SERVER" "sudo -n bash -s -- '$remote' ${apps[*]}" <<'REMOTE'
set -euo pipefail
incoming="$1"
shift
for app in "$@"; do
  target="/var/www/bingo-$app"
  if [[ -d "$target" ]]; then
    rm -rf "$target.backup"
    cp -a "$target" "$target.backup"
  fi
  rm -rf "$target.new"
  mkdir -p "$target.new"
  cp -a "$incoming/$app/." "$target.new/"
  rm -rf "$target"
  mv "$target.new" "$target"
  chown -R www-data:www-data "$target"
  find "$target" -type d -exec chmod 755 {} +
  find "$target" -type f -exec chmod 644 {} +
done
REMOTE
for url in "${urls[@]}"; do
  curl -fsS --retry 3 --max-time 30 "$url/" >/dev/null
done
curl -fsS --retry 3 --max-time 30 "$VITE_API_BASE_URL/actuator/health" | grep -q '"status":"UP"'
