#!/usr/bin/env bash
set -euo pipefail

COMPONENT="${1:-}"
RELEASE_ID="${2:-}"
SITE_URL="${3:-}"
ADMIN_URL="${4:-}"
API_URL="${5:-}"
TLS_EMAIL="${6:-}"
ARCHIVE="${7:-}"

case "$COMPONENT" in
  all|backend|admin|website) ;;
  *) echo "ERROR: Unsupported deployment component." >&2; exit 2 ;;
esac

if [[ ! "$RELEASE_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$ ]]; then
  echo "ERROR: Invalid release identifier." >&2
  exit 2
fi

if [[ ! -f "$ARCHIVE" ]]; then
  echo "ERROR: Deployment archive was not found." >&2
  exit 2
fi

for required_command in certbot curl envsubst nginx python3 systemctl tar; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "ERROR: Required host command is missing: $required_command" >&2
    exit 1
  fi
done

read -r SITE_DOMAIN ADMIN_DOMAIN API_DOMAIN < <(
  python3 - "$SITE_URL" "$ADMIN_URL" "$API_URL" <<'PY'
import sys
from urllib.parse import urlsplit

hosts = []
for value in sys.argv[1:]:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise SystemExit("Deployment URLs must be HTTPS origins without paths, queries, or fragments.")
    hosts.append(parsed.hostname)
print(*hosts)
PY
)

if [[ -z "$TLS_EMAIL" || "$TLS_EMAIL" == *$'\n'* ]]; then
  echo "ERROR: A valid TLS contact email is required." >&2
  exit 2
fi

INCOMING_DIR="/tmp/bingo-deploy-$RELEASE_ID"
OLD_SITE=""
OLD_ADMIN=""
OLD_BACKEND=""
SITE_CHANGED=0
ADMIN_CHANGED=0
BACKEND_CHANGED=0
NGINX_CONFIG_BACKUP="/tmp/bingo-nginx-$RELEASE_ID.conf"
NGINX_CONFIG_CHANGED=0

save_previous_link() {
  local current="$1"
  if [[ -L "$current" ]]; then
    readlink -f "$current"
  fi
}

switch_link() {
  local release_dir="$1"
  local current="$2"
  local temporary="${current}.new"
  sudo ln -sfn "$release_dir" "$temporary"
  sudo mv -Tf "$temporary" "$current"
}

restore_link() {
  local current="$1"
  local old_target="$2"
  if [[ -n "$old_target" ]]; then
    switch_link "$old_target" "$current"
  else
    sudo rm -f "$current"
  fi
}

rollback() {
  local exit_status=$?
  local rollback_failed=0
  trap - EXIT ERR
  if [[ "$exit_status" -ne 0 ]]; then
    echo "ERROR: Lightsail release failed; restoring the previous application links and Nginx configuration." >&2
    if [[ "$BACKEND_CHANGED" -eq 1 ]]; then
      if ! restore_link /opt/bingo/backend/current "$OLD_BACKEND"; then
        echo "ERROR: Could not restore the previous backend release link." >&2
        rollback_failed=1
      fi
      if [[ -n "$OLD_BACKEND" ]]; then
        if ! sudo systemctl restart bingo-backend; then
          echo "ERROR: Could not restart the previous backend release." >&2
          rollback_failed=1
        fi
      else
        if ! sudo systemctl stop bingo-backend; then
          echo "ERROR: Could not stop the failed first backend release." >&2
          rollback_failed=1
        fi
      fi
    fi
    if [[ "$SITE_CHANGED" -eq 1 ]]; then
      if ! restore_link /var/www/bingo-website/current "$OLD_SITE"; then
        echo "ERROR: Could not restore the previous website release link." >&2
        rollback_failed=1
      fi
    fi
    if [[ "$ADMIN_CHANGED" -eq 1 ]]; then
      if ! restore_link /var/www/bingo-admin/current "$OLD_ADMIN"; then
        echo "ERROR: Could not restore the previous admin release link." >&2
        rollback_failed=1
      fi
    fi
    if [[ "$NGINX_CONFIG_CHANGED" -eq 1 && -f "$NGINX_CONFIG_BACKUP" ]]; then
      if ! sudo install -o root -g root -m 0644 "$NGINX_CONFIG_BACKUP" /etc/nginx/sites-available/bingo.conf; then
        echo "ERROR: Could not restore the previous Nginx configuration." >&2
        rollback_failed=1
      fi
    fi
    if sudo nginx -t; then
      if ! sudo systemctl reload nginx; then
        echo "ERROR: Could not reload Nginx after rollback." >&2
        rollback_failed=1
      fi
    else
      echo "ERROR: Nginx configuration remains invalid after rollback." >&2
      rollback_failed=1
    fi
  fi
  if [[ -d "$INCOMING_DIR" ]] && ! sudo rm -rf "$INCOMING_DIR"; then
    echo "ERROR: Could not remove the temporary deployment directory." >&2
    rollback_failed=1
  fi
  if [[ "$rollback_failed" -eq 1 ]]; then
    exit 1
  fi
  exit "$exit_status"
}
trap rollback EXIT

sudo install -d -o root -g root -m 0700 "$INCOMING_DIR"
sudo tar --no-same-owner -xzf "$ARCHIVE" -C "$INCOMING_DIR"
sudo chmod -R go-rwx "$INCOMING_DIR"

if [[ "$COMPONENT" == "all" || "$COMPONENT" == "backend" ]]; then
  if [[ ! -f "$INCOMING_DIR/backend/backend.env" || ! -f "$INCOMING_DIR/backend/database-admin.json" ]]; then
    echo "ERROR: Backend runtime env file or managed database bootstrap credentials are missing from the release archive." >&2
    exit 1
  fi
  sudo python3 - "$INCOMING_DIR/backend/backend.env" "$INCOMING_DIR/backend/database-admin.json" \
    "$INCOMING_DIR/mysql-admin.cnf" "$INCOMING_DIR/create-app-user.sql" \
    "https://$ADMIN_DOMAIN" "$ADMIN_DOMAIN" <<'PY'
import json
import os
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

path, admin_secret_path, client_path, sql_path, admin_origin, admin_domain = sys.argv[1:]
settings = {}
for line in Path(path).read_text(encoding="utf-8").splitlines():
    stripped = line.strip()
    if not stripped or stripped.startswith("#"):
        continue
    if "=" not in stripped:
        raise SystemExit("ERROR: Backend runtime secret must use KEY=VALUE lines.")
    key, value = stripped.split("=", 1)
    key = key.strip()
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
        raise SystemExit("ERROR: Backend runtime secret contains an invalid environment variable name.")
    if key in settings:
        raise SystemExit(f"ERROR: Backend runtime secret contains a duplicate setting: {key}.")
    settings[key] = value.strip().strip("\"'")

required = (
    "MYSQL_HOST", "MYSQL_PORT", "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD", "JWT_SECRET",
    "CORS_ALLOWED_ORIGINS", "WEBSOCKET_ALLOWED_ORIGINS",
    "ADMIN_ACCOUNT_ACTIVATION_URL", "SPRING_MAIL_USERNAME", "SPRING_MAIL_PASSWORD",
)
missing = [key for key in required if not settings.get(key)]
if missing:
    raise SystemExit("ERROR: Backend runtime secret is missing required keys: " + ", ".join(missing))
if len(settings["JWT_SECRET"]) < 32:
    raise SystemExit("ERROR: JWT_SECRET must contain at least 32 characters.")
if not re.fullmatch(r"[A-Za-z0-9.-]+", settings["MYSQL_HOST"]):
    raise SystemExit("ERROR: MYSQL_HOST must be the Lightsail database DNS endpoint.")
if settings["MYSQL_PORT"] != "3306":
    raise SystemExit("ERROR: MYSQL_PORT must be 3306 for the Lightsail managed database.")
if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", settings["MYSQL_DATABASE"]):
    raise SystemExit("ERROR: MYSQL_DATABASE is not a valid MySQL database name.")
if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,15}", settings["MYSQL_USER"]):
    raise SystemExit("ERROR: MYSQL_USER must be a simple MySQL user name with at most 16 characters.")
if not re.fullmatch(r"[A-Za-z0-9]{32,64}", settings["MYSQL_PASSWORD"]):
    raise SystemExit("ERROR: MYSQL_PASSWORD must be a generated 32-64 character alphanumeric value.")
for key in ("CORS_ALLOWED_ORIGINS", "WEBSOCKET_ALLOWED_ORIGINS"):
    allowed = {origin.strip().rstrip("/") for origin in settings[key].split(",")}
    if admin_origin not in allowed:
        raise SystemExit(f"ERROR: {key} must include the production admin origin.")
activation = urlsplit(settings["ADMIN_ACCOUNT_ACTIVATION_URL"])
if activation.scheme != "https" or activation.hostname != admin_domain:
    raise SystemExit("ERROR: ADMIN_ACCOUNT_ACTIVATION_URL must use the production admin HTTPS hostname.")

database_admin = json.loads(Path(admin_secret_path).read_text(encoding="utf-8"))
admin_user = database_admin.get("username")
admin_password = database_admin.get("password")
if admin_user != "bingo_db_admin" or not isinstance(admin_password, str) or not re.fullmatch(r"[A-Za-z0-9]{32,64}", admin_password):
    raise SystemExit("ERROR: Database admin secret does not match the generated Lightsail credentials format.")

client_config = (
    "[client]\n"
    f"user={admin_user}\n"
    f"password={admin_password}\n"
    "ssl-ca=/etc/ssl/certs/aws-rds-global-bundle.pem\n"
    f"host={settings['MYSQL_HOST']}\n"
    f"port={settings['MYSQL_PORT']}\n"
    "ssl-mode=VERIFY_IDENTITY\n"
)
Path(client_path).write_text(client_config, encoding="utf-8")
os.chmod(client_path, 0o600)
database = settings["MYSQL_DATABASE"]
app_user = settings["MYSQL_USER"]
app_password = settings["MYSQL_PASSWORD"]
sql = (
    f"CREATE USER IF NOT EXISTS '{app_user}'@'%' IDENTIFIED BY '{app_password}';\n"
    f"GRANT ALL PRIVILEGES ON `{database}`.* TO '{app_user}'@'%';\n"
)
Path(sql_path).write_text(sql, encoding="utf-8")
os.chmod(sql_path, 0o600)
PY
  sudo mysql --defaults-extra-file="$INCOMING_DIR/mysql-admin.cnf" \
    --batch --skip-column-names < "$INCOMING_DIR/create-app-user.sql"
fi

sudo certbot certonly \
  --webroot \
  --webroot-path /var/www/letsencrypt \
  --non-interactive \
  --agree-tos \
  --keep-until-expiring \
  --email "$TLS_EMAIL" \
  --cert-name bingo-platform \
  -d "$SITE_DOMAIN" \
  -d "$ADMIN_DOMAIN" \
  -d "$API_DOMAIN"

if [[ "$COMPONENT" == "all" || "$COMPONENT" == "website" ]]; then
  if [[ ! -d "$INCOMING_DIR/website" ]]; then
    echo "ERROR: Website build artifact is missing from the release archive." >&2
    exit 1
  fi
  OLD_SITE="$(save_previous_link /var/www/bingo-website/current)"
  SITE_RELEASE="/var/www/bingo-website/releases/$RELEASE_ID"
  sudo install -d -o www-data -g www-data -m 0755 "$SITE_RELEASE"
  sudo cp -a "$INCOMING_DIR/website/." "$SITE_RELEASE/"
  sudo chown -R www-data:www-data "$SITE_RELEASE"
  sudo find "$SITE_RELEASE" -type d -exec chmod 755 {} \;
  sudo find "$SITE_RELEASE" -type f -exec chmod 644 {} \;
  switch_link "$SITE_RELEASE" /var/www/bingo-website/current
  SITE_CHANGED=1
fi

if [[ "$COMPONENT" == "all" || "$COMPONENT" == "admin" ]]; then
  if [[ ! -d "$INCOMING_DIR/admin" ]]; then
    echo "ERROR: Admin build artifact is missing from the release archive." >&2
    exit 1
  fi
  OLD_ADMIN="$(save_previous_link /var/www/bingo-admin/current)"
  ADMIN_RELEASE="/var/www/bingo-admin/releases/$RELEASE_ID"
  sudo install -d -o www-data -g www-data -m 0755 "$ADMIN_RELEASE"
  sudo cp -a "$INCOMING_DIR/admin/." "$ADMIN_RELEASE/"
  sudo chown -R www-data:www-data "$ADMIN_RELEASE"
  sudo find "$ADMIN_RELEASE" -type d -exec chmod 755 {} \;
  sudo find "$ADMIN_RELEASE" -type f -exec chmod 644 {} \;
  switch_link "$ADMIN_RELEASE" /var/www/bingo-admin/current
  ADMIN_CHANGED=1
fi

if [[ "$COMPONENT" == "all" || "$COMPONENT" == "backend" ]]; then
  if [[ ! -f "$INCOMING_DIR/backend/backend.jar" || ! -f "$INCOMING_DIR/backend/backend.env" ]]; then
    echo "ERROR: Backend JAR or runtime env file is missing from the release archive." >&2
    exit 1
  fi
  OLD_BACKEND="$(save_previous_link /opt/bingo/backend/current)"
  BACKEND_RELEASE="/opt/bingo/backend/releases/$RELEASE_ID"
  sudo install -d -o root -g root -m 0755 "$BACKEND_RELEASE"
  sudo install -o root -g root -m 0644 "$INCOMING_DIR/backend/backend.jar" "$BACKEND_RELEASE/backend.jar"
  sudo install -o root -g root -m 0600 "$INCOMING_DIR/backend/backend.env" "$BACKEND_RELEASE/backend.env"
  sudo rm -f "$INCOMING_DIR/backend/database-admin.json" "$INCOMING_DIR/mysql-admin.cnf" "$INCOMING_DIR/create-app-user.sql"
  switch_link "$BACKEND_RELEASE" /opt/bingo/backend/current
  BACKEND_CHANGED=1
  sudo systemctl restart bingo-backend
  for attempt in $(seq 1 24); do
    if curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"'; then
      break
    fi
    if [[ "$attempt" -eq 24 ]]; then
      echo "ERROR: Backend did not become healthy after restart." >&2
      exit 1
    fi
    sleep 5
  done
fi

sudo install -d -o root -g root -m 0755 /etc/nginx/sites-available
if [[ -L /etc/nginx/sites-enabled/bingo.conf ]]; then
  previous_nginx_config="$(readlink -f /etc/nginx/sites-enabled/bingo.conf)"
  if [[ -f "$previous_nginx_config" ]]; then
    sudo cp "$previous_nginx_config" "$NGINX_CONFIG_BACKUP"
  fi
elif [[ -f /etc/nginx/sites-available/bingo.conf ]]; then
  sudo cp /etc/nginx/sites-available/bingo.conf "$NGINX_CONFIG_BACKUP"
fi
NGINX_CONFIG_CHANGED=1
sudo env SITE_DOMAIN="$SITE_DOMAIN" ADMIN_DOMAIN="$ADMIN_DOMAIN" API_DOMAIN="$API_DOMAIN" \
  envsubst '${SITE_DOMAIN} ${ADMIN_DOMAIN} ${API_DOMAIN}' \
  < "$INCOMING_DIR/nginx-sites.conf.template" \
  | sudo tee /etc/nginx/sites-available/bingo.conf >/dev/null
sudo ln -sfn /etc/nginx/sites-available/bingo.conf /etc/nginx/sites-enabled/bingo.conf
sudo rm -f /etc/nginx/sites-enabled/bingo-bootstrap.conf
sudo nginx -t
sudo systemctl reload nginx

for domain in "$SITE_DOMAIN" "$ADMIN_DOMAIN" "$API_DOMAIN"; do
  if ! curl --fail --silent --show-error --resolve "$domain:443:127.0.0.1" "https://$domain/healthz" >/dev/null; then
    echo "ERROR: HTTPS virtual host health check failed for a configured domain." >&2
    exit 1
  fi
done

if [[ "$COMPONENT" == "all" || "$COMPONENT" == "backend" ]]; then
  if ! curl --fail --silent --show-error --resolve "$API_DOMAIN:443:127.0.0.1" \
    "https://$API_DOMAIN/actuator/health" | grep -q '"status":"UP"'; then
    echo "ERROR: HTTPS backend health check failed." >&2
    exit 1
  fi
fi

echo "Lightsail release $RELEASE_ID deployed successfully."
