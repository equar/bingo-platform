#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python3 "$ROOT/deployment/aws/validate-config.py"
: "${SERVER:?Set SERVER to user@host}"
: "${SSH_KEY:?Set SSH_KEY}"
[[ -f "$SSH_KEY" ]] || { echo 'SSH key not found' >&2; exit 1; }
export MYSQL_DATABASE=bingo SPRING_PROFILES_ACTIVE=prod SERVER_PORT=8088
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
umask 077
python3 - "$work/backend.env" <<'PYENV'
import os, sys, base64
required=['MYSQL_HOST','MYSQL_USER','MYSQL_PASSWORD','JWT_SECRET','CORS_ALLOWED_ORIGINS','WEBSOCKET_ALLOWED_ORIGINS','ADMIN_ACCOUNT_ACTIVATION_URL','SPRING_MAIL_USERNAME','BINGO_FLOAT_BANK_ENCRYPTION_KEY']
settings={k:os.environ.get(k,'') for k in required}
missing=[k for k,v in settings.items() if not v or v.startswith(('REPLACE_','YOUR_'))]
if missing: raise SystemExit('Missing production settings: '+', '.join(missing))
if len(settings['JWT_SECRET']) < 32: raise SystemExit('JWT_SECRET must contain at least 32 characters')
try:
    if len(base64.b64decode(settings['BINGO_FLOAT_BANK_ENCRYPTION_KEY'],validate=True)) != 32: raise ValueError()
except Exception: raise SystemExit('BINGO_FLOAT_BANK_ENCRYPTION_KEY must be Base64 encoding of 32 bytes')
defaults={'SPRING_MAIL_PASSWORD':'','MYSQL_PORT':'3306','MYSQL_DATABASE':'bingo','SPRING_PROFILES_ACTIVE':'prod','BINGO_ENV_IMPORT':'','SERVER_PORT':'8088','SERVER_ADDRESS':'127.0.0.1','SPRING_JPA_HIBERNATE_DDL_AUTO':'validate','JWT_ACCESS_TOKEN_TTL_SECONDS':'3600','SPRING_MAIL_HOST':'smtp.gmail.com','SPRING_MAIL_PORT':'587','APP_DEV_SEED_ENABLED':'false','APP_BUSINESS_TIMEZONE':'Africa/Addis_Ababa'}
fixed={'BINGO_ENV_IMPORT','MYSQL_DATABASE','SPRING_PROFILES_ACTIVE','SERVER_PORT','SERVER_ADDRESS','SPRING_JPA_HIBERNATE_DDL_AUTO','APP_DEV_SEED_ENABLED'}
for k,v in defaults.items(): settings[k]=v if k in fixed else os.environ.get(k) or v
for k in ['APP_PAYMENT_PROVIDER_NAME','APP_PAYMENT_BUSINESS_ACCOUNT','APP_PAYMENT_INSTRUCTIONS']:
    if os.environ.get(k): settings[k]=os.environ[k]
with open(sys.argv[1],'w') as f:
    for k,v in settings.items():
        if any(c in v for c in ['\n','\r','\x00']): raise SystemExit('Multiline runtime setting: '+k)
        # systemd EnvironmentFile quoting, without invoking a shell.
        f.write(k+'="'+v.replace('\\','\\\\').replace('"','\\"')+'"\n')
PYENV
jar="$ROOT/bingo-backend/target/bingo-backend-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$jar" ]]; then
  (cd "$ROOT/bingo-backend" && mvn -B -Dmaven.test.skip=true clean package)
fi
[[ -f "$jar" ]] || { echo 'Bingo backend JAR not found' >&2; exit 1; }
remote="$(ssh -o BatchMode=yes -i "$SSH_KEY" "$SERVER" 'umask 077; mktemp -d /tmp/bingo-backend.XXXXXXXX')"
[[ "$remote" =~ ^/tmp/bingo-backend\.[a-zA-Z0-9]+$ ]]
cleanup() {
  status=$?
  trap - EXIT
  ssh -i "$SSH_KEY" "$SERVER" "rm -rf '$remote'" || status=1
  rm -rf "$work"
  exit "$status"
}
trap cleanup EXIT
scp -i "$SSH_KEY" "$jar" "$SERVER:$remote/backend.jar"
scp -i "$SSH_KEY" "$work/backend.env" "$ROOT/deployment/aws/bingo-backend.service" "$ROOT/deployment/aws/setup-rds-truststore.sh" "$SERVER:$remote/"
ssh -i "$SSH_KEY" "$SERVER" "sudo -n bash -s -- '$remote'" <<'REMOTE'
set -euo pipefail
incoming="$1"
java -version
[[ "$(java -version 2>&1)" == *'"25'* ]] || { echo 'Java 25 is required' >&2; exit 1; }
bash "$incoming/setup-rds-truststore.sh"
id bingo >/dev/null 2>&1 || useradd --system --home /opt/bingo --shell /usr/sbin/nologin bingo
install -d -o bingo -g bingo -m 755 /opt/bingo /opt/bingo/logs
# Keep a complete previous application/config pair. Flyway changes are not reversed.
if [[ -f /opt/bingo/backend.jar ]]; then
  cp -p /opt/bingo/backend.jar /opt/bingo/backend.jar.previous
  cp -p /opt/bingo/.env /opt/bingo/.env.previous
fi
rollback() {
  status=$?
  trap - EXIT
  if [[ "$status" != 0 ]]; then
    systemctl stop bingo-backend || true
    if [[ -f /opt/bingo/backend.jar.previous ]]; then
      cp -p /opt/bingo/backend.jar.previous /opt/bingo/backend.jar
      cp -p /opt/bingo/.env.previous /opt/bingo/.env
      systemctl restart bingo-backend || true
    fi
  fi
  exit "$status"
}
trap rollback EXIT
systemctl stop bingo-backend || true
install -o bingo -g bingo -m 644 "$incoming/backend.jar" /opt/bingo/backend.jar
install -o root -g root -m 600 "$incoming/backend.env" /opt/bingo/.env
install -o root -g root -m 644 "$incoming/bingo-backend.service" /etc/systemd/system/bingo-backend.service
systemctl daemon-reload
systemctl enable bingo-backend
systemctl restart bingo-backend
for attempt in $(seq 1 60); do
  if curl -fsS --max-time 3 http://127.0.0.1:8088/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo 'Bingo backend healthy'
    exit 0
  fi
  if [[ "$(systemctl show bingo-backend --property=ExecMainStatus --value)" != "0" ]]; then
    echo 'Bingo backend exited during startup' >&2
    break
  fi
  sleep 2
done
echo 'Bingo backend failed health check; startup diagnostics follow' >&2
python3 - <<'DIAGNOSTICS'
import re, shlex, subprocess
from pathlib import Path
secrets = []
for line in Path('/opt/bingo/.env').read_text().splitlines():
    if '=' not in line or line.lstrip().startswith('#'):
        continue
    key, value = line.split('=', 1)
    if re.search(r'PASSWORD|SECRET|KEY|TOKEN', key):
        parsed = shlex.split(value)
        if parsed and parsed[0]:
            secrets.append(parsed[0])
for command in [
    ['systemctl', 'show', 'bingo-backend', '--property=ActiveState,SubState,Result,ExecMainStatus'],
    ['journalctl', '-u', 'bingo-backend', '-n', '100', '--no-pager', '-o', 'cat'],
]:
    result = subprocess.run(command, capture_output=True, text=True)
    output = result.stdout + result.stderr
    for value in sorted(secrets, key=len, reverse=True):
        output = output.replace(value, '[REDACTED]')
    print(output)
DIAGNOSTICS
exit 1
REMOTE
