#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL_VALUE="${1:-${API_BASE_URL:-}}"

if [[ -z "$API_BASE_URL_VALUE" ]]; then
  echo 'ERROR: API base URL is required.' >&2
  exit 1
fi

python3 - "$API_BASE_URL_VALUE" <<'PY'
import ipaddress
import sys
from urllib.parse import urlparse

value = sys.argv[1].strip()
parsed = urlparse(value)

if parsed.scheme != 'https':
    raise SystemExit('ERROR: API base URL must use https.')

if not parsed.hostname:
    raise SystemExit('ERROR: API base URL must include a host.')

hostname = parsed.hostname.lower()
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