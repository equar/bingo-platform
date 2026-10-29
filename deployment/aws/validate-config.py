"""Validate public deployment settings before building or connecting to a host."""
import os
from urllib.parse import urlsplit
origins = {}
for key in ('VITE_API_BASE_URL', 'ADMIN_PUBLIC_URL', 'PUBLIC_SITE_URL'):
    value = os.environ.get(key, '')
    url = urlsplit(value)
    if (url.scheme != 'https' or not url.hostname or url.username or url.password
            or url.path or url.query or url.fragment
            or url.hostname in ('localhost', '127.0.0.1', '::1')):
        raise SystemExit(key + ' must be a public HTTPS origin without a trailing slash or path')
    origins[key] = value
if len({urlsplit(v).hostname for v in origins.values()}) != 3:
    raise SystemExit('Use distinct website, admin, and API hostnames')
if os.environ.get('PUBLIC_SITE_INDEXABLE') not in ('true', 'false'):
    raise SystemExit('PUBLIC_SITE_INDEXABLE must be true or false')
for key in ('CORS_ALLOWED_ORIGINS', 'WEBSOCKET_ALLOWED_ORIGINS'):
    allowed = {origin.strip().rstrip('/') for origin in os.environ.get(key, '').split(',')}
    if origins['ADMIN_PUBLIC_URL'] not in allowed:
        raise SystemExit(key + ' must include ADMIN_PUBLIC_URL')
activation = urlsplit(os.environ.get('ADMIN_ACCOUNT_ACTIVATION_URL', ''))
if (activation.scheme != 'https' or activation.netloc != urlsplit(origins['ADMIN_PUBLIC_URL']).netloc
        or not activation.path or activation.username or activation.password):
    raise SystemExit('ADMIN_ACCOUNT_ACTIVATION_URL must use the admin HTTPS hostname and activation route')
print('Bingo production URLs and origins validated')
