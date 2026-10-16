#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$REPO_ROOT/deployment/lib/common.sh"

SERVER="${SERVER:-ubuntu@YOUR_SERVER_HOST}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/bingo-deploy.pem}"
REMOTE_ROOT="${REMOTE_BACKEND_ROOT:-/opt/bingo/backend}"
SERVICE_NAME="${BACKEND_SERVICE_NAME:-bingo-backend}"
SERVICE_USER="${SERVICE_USER:-bingo}"
ENV_FILE="${BACKEND_ENV_FILE:-}"
BACKEND_JAR_PATH="${BACKEND_JAR_PATH:-}"
DRY_RUN=0

usage() {
  cat <<'EOF'
Usage: deploy-backend.sh [--dry-run] [--env-file PATH]

Environment variables:
  SERVER                 SSH target, for example ubuntu@bingo.example.com
  SSH_KEY                SSH private key path
  REMOTE_BACKEND_ROOT     Remote runtime root, defaults to /opt/bingo/backend
  BACKEND_ENV_FILE        Local env file with production secrets and config
  BACKEND_JAR_PATH        Prebuilt backend jar to deploy instead of rebuilding
  BACKEND_SERVICE_NAME    systemd service name, defaults to bingo-backend
  SERVICE_USER           Remote runtime user, defaults to bingo
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=1
      ;;
    --env-file)
      ENV_FILE="$2"
      shift
      ;;
    --backend-jar)
      BACKEND_JAR_PATH="$2"
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

[[ -n "$ENV_FILE" ]] || die "--env-file is required"
[[ -f "$ENV_FILE" ]] || die "Env file not found: $ENV_FILE"
[[ -f "$SSH_KEY" ]] || die "SSH key not found: $SSH_KEY"

require_command mvn
require_command ssh
require_command scp

if [[ -n "$BACKEND_JAR_PATH" ]]; then
  [[ -f "$BACKEND_JAR_PATH" ]] || die "Backend jar not found: $BACKEND_JAR_PATH"
  backend_jar="$BACKEND_JAR_PATH"
else
  cd "$REPO_ROOT/bingo-backend"
  mvn -DskipTests clean package

  backend_jar="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*.jar' | sort | tail -n 1)"
  [[ -n "$backend_jar" ]] || die "Backend jar was not produced"
fi

load_env_file "$ENV_FILE"

: "${MYSQL_HOST:?MYSQL_HOST is required in $ENV_FILE}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required in $ENV_FILE}"
: "${MYSQL_USER:?MYSQL_USER is required in $ENV_FILE}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required in $ENV_FILE}"
: "${JWT_SECRET:?JWT_SECRET is required in $ENV_FILE}"
: "${CORS_ALLOWED_ORIGINS:?CORS_ALLOWED_ORIGINS is required in $ENV_FILE}"
: "${WEBSOCKET_ALLOWED_ORIGINS:?WEBSOCKET_ALLOWED_ORIGINS is required in $ENV_FILE}"

if [[ "$JWT_SECRET" == REPLACE_WITH_* || "$JWT_SECRET" == REPLACE_ME* ]]; then
  die "JWT_SECRET must be replaced with a strong random secret"
fi

release_name="$(date +%Y%m%d%H%M%S)"
remote_release_dir="$REMOTE_ROOT/releases/$release_name"
remote_current_link="$REMOTE_ROOT/current"
remote_previous_link="$REMOTE_ROOT/previous"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "Dry run only. No remote changes will be made."
  echo "Would upload $backend_jar to $SERVER:$remote_release_dir/backend.jar"
  echo "Would upload $ENV_FILE to $SERVER:$REMOTE_ROOT/.env"
  echo "Would switch $remote_current_link to $remote_release_dir and restart $SERVICE_NAME"
  exit 0
fi

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' SERVICE_USER='$SERVICE_USER' SERVICE_NAME='$SERVICE_NAME' bash -s" <<'REMOTE'
set -euo pipefail
sudo install -d -o "$SERVICE_USER" -g "$SERVICE_USER" -m 755 "$REMOTE_ROOT" "$REMOTE_ROOT/releases" "$REMOTE_ROOT/logs"
REMOTE

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' SERVICE_USER='$SERVICE_USER' RELEASE_DIR='$remote_release_dir' bash -s" <<'REMOTE'
set -euo pipefail
sudo install -d -o "$SERVICE_USER" -g "$SERVICE_USER" -m 755 "$RELEASE_DIR"
REMOTE

scp -i "$SSH_KEY" "$backend_jar" "$SERVER:$remote_release_dir/backend.jar"
scp -i "$SSH_KEY" "$ENV_FILE" "$SERVER:$REMOTE_ROOT/.env"

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' SERVICE_USER='$SERVICE_USER' RELEASE_DIR='$remote_release_dir' bash -s" <<'REMOTE'
set -euo pipefail
sudo chown -R "$SERVICE_USER:$SERVICE_USER" "$RELEASE_DIR"
sudo chmod 755 "$RELEASE_DIR"
sudo chmod 644 "$RELEASE_DIR/backend.jar"
sudo chown root:root "$REMOTE_ROOT/.env"
sudo chmod 600 "$REMOTE_ROOT/.env"
REMOTE

ssh -i "$SSH_KEY" "$SERVER" "REMOTE_ROOT='$REMOTE_ROOT' SERVICE_NAME='$SERVICE_NAME' RELEASE_DIR='$remote_release_dir' bash -s" <<'REMOTE'
set -euo pipefail
current_link="$REMOTE_ROOT/current"
previous_link="$REMOTE_ROOT/previous"

if [[ -L "$current_link" ]]; then
  current_target="$(readlink -f "$current_link")"
  if [[ -n "$current_target" ]]; then
    ln -sfn "$current_target" "$previous_link"
  fi
fi

sudo systemctl stop "$SERVICE_NAME" || true
ln -sfn "$RELEASE_DIR" "$current_link"
sudo systemctl start "$SERVICE_NAME"
REMOTE

ssh -i "$SSH_KEY" "$SERVER" "bash -s" <<'REMOTE'
set -euo pipefail
curl -fsS http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"'
REMOTE

echo "Backend deployed successfully to $SERVER"
