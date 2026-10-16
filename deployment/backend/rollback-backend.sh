#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$REPO_ROOT/deployment/lib/common.sh"

SERVER="${SERVER:-ubuntu@YOUR_SERVER_HOST}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/bingo-deploy.pem}"
REMOTE_ROOT="${REMOTE_BACKEND_ROOT:-/opt/bingo/backend}"
SERVICE_NAME="${BACKEND_SERVICE_NAME:-bingo-backend}"

[[ -f "$SSH_KEY" ]] || die "SSH key not found: $SSH_KEY"
require_command ssh

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' SERVICE_NAME='$SERVICE_NAME' bash -s" <<'REMOTE'
set -euo pipefail
current_link="$REMOTE_ROOT/current"
previous_link="$REMOTE_ROOT/previous"

if [[ ! -L "$previous_link" ]]; then
  echo "ERROR: No previous release recorded at $previous_link" >&2
  exit 1
fi

previous_target="$(readlink -f "$previous_link")"
[[ -n "$previous_target" ]] || { echo "ERROR: Previous release is empty" >&2; exit 1; }

sudo systemctl stop "$SERVICE_NAME" || true
ln -sfn "$previous_target" "$current_link"
sudo systemctl start "$SERVICE_NAME"
curl -fsS http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"'
REMOTE
