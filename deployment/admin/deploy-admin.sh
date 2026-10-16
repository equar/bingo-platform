#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$REPO_ROOT/deployment/lib/common.sh"

SERVER="${SERVER:-ubuntu@YOUR_SERVER_HOST}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/bingo-deploy.pem}"
REMOTE_ROOT="${REMOTE_ADMIN_ROOT:-/var/www/bingo-admin}"
SERVICE_USER="${SERVICE_USER:-bingo}"
PUBLIC_URL="${ADMIN_PUBLIC_URL:-${VITE_API_BASE_URL:-}}"
ADMIN_DIST_DIR="${ADMIN_DIST_DIR:-}"
DRY_RUN=0

usage() {
  cat <<'EOF'
Usage: deploy-admin.sh [--dry-run] [--public-url URL]

Environment variables:
  SERVER             SSH target, for example ubuntu@bingo.example.com
  SSH_KEY            SSH private key path
  REMOTE_ADMIN_ROOT  Remote runtime root, defaults to /var/www/bingo-admin
  ADMIN_PUBLIC_URL   Public origin used at build time and for health checks
  ADMIN_DIST_DIR     Prebuilt admin dist directory to deploy instead of rebuilding
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      ;;
    --public-url)
      PUBLIC_URL="$2"
      shift
      ;;
    --admin-dist)
      ADMIN_DIST_DIR="$2"
      shift
      ;;
    --server)
      SERVER="$2"
      shift
      ;;
    --ssh-key)
      SSH_KEY="$2"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
  shift
done

[[ -n "$PUBLIC_URL" ]] || die "--public-url is required"
[[ -f "$SSH_KEY" ]] || die "SSH key not found: $SSH_KEY"

require_command ssh
require_command scp

if [[ -n "$ADMIN_DIST_DIR" ]]; then
  [[ -d "$ADMIN_DIST_DIR" ]] || die "Admin dist directory not found: $ADMIN_DIST_DIR"
else
  ADMIN_DIST_DIR="$($REPO_ROOT/deployment/admin/build-admin.sh --api-base-url "$PUBLIC_URL")"
fi

release_name="$(date +%Y%m%d%H%M%S)"
remote_release_dir="$REMOTE_ROOT/releases/$release_name"
remote_current_link="$REMOTE_ROOT/current"
remote_previous_link="$REMOTE_ROOT/previous"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "Dry run only. No remote changes will be made."
  echo "Would upload $ADMIN_DIST_DIR to $SERVER:$remote_release_dir"
  echo "Would switch $remote_current_link to $remote_release_dir"
  exit 0
fi

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' SERVICE_USER='$SERVICE_USER' RELEASE_DIR='$remote_release_dir' bash -s" <<'REMOTE'
set -euo pipefail
sudo install -d -o "$SERVICE_USER" -g "$SERVICE_USER" -m 755 "$REMOTE_ROOT" "$REMOTE_ROOT/releases"
sudo install -d -o "$SERVICE_USER" -g "$SERVICE_USER" -m 755 "$RELEASE_DIR"
REMOTE

scp -i "$SSH_KEY" -r "$ADMIN_DIST_DIR/." "$SERVER:$remote_release_dir/"

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' SERVICE_USER='$SERVICE_USER' RELEASE_DIR='$remote_release_dir' bash -s" <<'REMOTE'
set -euo pipefail
sudo chown -R "$SERVICE_USER:$SERVICE_USER" "$RELEASE_DIR"
sudo find "$RELEASE_DIR" -type d -exec chmod 755 {} \;
sudo find "$RELEASE_DIR" -type f -exec chmod 644 {} \;
REMOTE

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' RELEASE_DIR='$remote_release_dir' bash -s" <<'REMOTE'
set -euo pipefail
current_link="$REMOTE_ROOT/current"
previous_link="$REMOTE_ROOT/previous"

if [[ -L "$current_link" ]]; then
  current_target="$(readlink -f "$current_link")"
  if [[ -n "$current_target" ]]; then
    ln -sfn "$current_target" "$previous_link"
  fi
fi

ln -sfn "$RELEASE_DIR" "$current_link"
REMOTE

curl -fsS "$PUBLIC_URL/" >/dev/null

echo "Admin deployed successfully to $SERVER"
