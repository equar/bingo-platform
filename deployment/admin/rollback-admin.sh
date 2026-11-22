#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$REPO_ROOT/deployment/lib/common.sh"
source "$REPO_ROOT/deployment/lib/pinned-ssh-host.sh"

SERVER="${SERVER:-ubuntu@YOUR_SERVER_HOST}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/bingo-deploy.pem}"
REMOTE_ROOT="${REMOTE_ADMIN_ROOT:-/var/www/bingo-admin}"

[[ -f "$SSH_KEY" ]] || die "SSH key not found: $SSH_KEY"
configure_pinned_ssh_host "$SERVER" "${DEPLOY_SSH_KNOWN_HOSTS_FILE:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/bingo-known_hosts}"
require_command ssh

ssh "${SSH_OPTIONS[@]}" -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' bash -s" <<'REMOTE'
set -euo pipefail
current_link="$REMOTE_ROOT/current"
previous_link="$REMOTE_ROOT/previous"

if [[ ! -L "$previous_link" ]]; then
  echo "ERROR: No previous release recorded at $previous_link" >&2
  exit 1
fi

previous_target="$(readlink -f "$previous_link")"
[[ -n "$previous_target" ]] || { echo "ERROR: Previous release is empty" >&2; exit 1; }

ln -sfn "$previous_target" "$current_link"
curl -fsS http://127.0.0.1/ >/dev/null
REMOTE
