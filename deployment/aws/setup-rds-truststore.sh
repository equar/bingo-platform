#!/usr/bin/env bash
set -euo pipefail
# Keep database trust isolated from other applications on the server.
JAVA_BIN="$(readlink -f "$(command -v java)")"
JAVA_HOME_DIR="$(dirname "$(dirname "$JAVA_BIN")")"
KEYTOOL="${BINGO_KEYTOOL:-$JAVA_HOME_DIR/bin/keytool}"
CACERTS="${BINGO_JAVA_CACERTS:-$JAVA_HOME_DIR/lib/security/cacerts}"
DESTINATION="${BINGO_TRUSTSTORE_PATH:-/etc/bingo/database-truststore.jks}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
if [[ -n "${BINGO_RDS_CA_BUNDLE_FILE:-}" ]]; then
  cp "$BINGO_RDS_CA_BUNDLE_FILE" "$work/bundle.pem"
else
  curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
    --connect-timeout 15 --max-time 90 --retry 3 \
    https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
    --output "$work/bundle.pem"
fi
python3 - "$work" <<'PY'
import re, subprocess, sys
from pathlib import Path
work = Path(sys.argv[1])
certificates = re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',
                          (work / 'bundle.pem').read_text(), re.DOTALL)
roots = 0
for certificate in certificates:
    path = work / 'candidate.pem'
    path.write_text(certificate + '\n')
    result = subprocess.run(['openssl', 'x509', '-in', str(path), '-noout',
                             '-subject', '-issuer', '-nameopt', 'RFC2253'],
                            capture_output=True, text=True, check=True)
    names = result.stdout.splitlines()
    if len(names) != 2 or names[0].split('=', 1)[1] != names[1].split('=', 1)[1]:
        continue
    # Trust roots only, not rotating server/intermediate certificates.
    subprocess.run(['openssl', 'verify', '-CAfile', str(path), str(path)],
                   capture_output=True, check=True)
    (work / f'root-{roots}.pem').write_text(certificate + '\n')
    roots += 1
if not roots:
    raise SystemExit('ERROR: AWS database CA bundle contains no valid root certificates')
print(f'Validated {roots} database root certificates')
PY
# Preserve standard public roots for SMTP and any other outbound TLS calls.
cp "$CACERTS" "$work/truststore.jks"
chmod 600 "$work/truststore.jks"
for certificate in "$work"/root-*.pem; do
  fingerprint="$(openssl x509 -in "$certificate" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':')"
  "$KEYTOOL" -importcert -noprompt -trustcacerts \
    -alias "bingo-rds-$fingerprint" -file "$certificate" \
    -keystore "$work/truststore.jks" -storepass changeit >/dev/null 2>&1
 done
"$KEYTOOL" -list -keystore "$work/truststore.jks" -storepass changeit >/dev/null
install -d -m 755 "$(dirname "$DESTINATION")"
install -m 644 "$work/truststore.jks" "$DESTINATION.new"
mv -f "$DESTINATION.new" "$DESTINATION"
echo 'Bingo database truststore installed'
