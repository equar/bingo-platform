#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL_VALUE="${1:-${API_BASE_URL:-}}"
ALLOW_TEST_HTTP=false

if [[ -z "$API_BASE_URL_VALUE" ]]; then
  echo 'ERROR: API base URL is required.' >&2
  exit 1
fi
if [[ "${2:-}" == "--allow-test-http" && "$#" -eq 2 ]]; then
  ALLOW_TEST_HTTP=true
elif [[ "$#" -gt 1 ]]; then
  echo "Usage: $0 <api-base-url> [--allow-test-http]" >&2
  exit 64
fi

python3 - "$API_BASE_URL_VALUE" "$ALLOW_TEST_HTTP" <<'PY'
import ipaddress
import sys
from urllib.parse import urlparse

value = sys.argv[1].strip()
parsed = urlparse(value)
allow_test_http = sys.argv[2] == 'true'

if not parsed.hostname:
    raise SystemExit('ERROR: API base URL must include a host.')

hostname = parsed.hostname.lower()
if parsed.username is not None or parsed.password is not None:
    raise SystemExit('ERROR: API base URL must not contain credentials.')
if parsed.path not in ('', '/') or parsed.query or parsed.fragment:
    raise SystemExit('ERROR: API base URL must be an origin without a path, query, or fragment.')
try:
    port = parsed.port
except ValueError:
    raise SystemExit('ERROR: API base URL contains an invalid port.')
if parsed.scheme != 'https' and not (
    allow_test_http
    and parsed.scheme == 'http'
    and hostname == 'api.test.bakaroo.com'
    and port in (None, 80)
):
    raise SystemExit('ERROR: API base URL must use HTTPS; HTTP is allowed only for the test API in test upload lanes.')

blocked_hosts = {
    'localhost',
    'host.docker.internal',
    '10.0.2.2',
    '127.0.0.1',
    '0.0.0.0',
    '::1',
}

if hostname in blocked_hosts or hostname.endswith('.local'):
    raise SystemExit('ERROR: API base URL must not point to localhost, emulator, or private hostnames.')

try:
    address = ipaddress.ip_address(hostname)
except ValueError:
    address = None

if address and (
    address.is_private
    or address.is_loopback
    or address.is_link_local
    or address.is_reserved
    or address.is_multicast
):
    raise SystemExit('ERROR: API base URL must not point to a private, loopback, or link-local address.')

print(value)
PY