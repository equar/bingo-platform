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
bootstrap_enabled = os.environ.get('ADMIN_BOOTSTRAP_ENABLED', 'false').strip().lower()
if bootstrap_enabled not in ('true', 'false'):
    raise SystemExit('ADMIN_BOOTSTRAP_ENABLED must be true or false')
if bootstrap_enabled == 'true':
    for key in ('ADMIN_BOOTSTRAP_PHONE', 'ADMIN_BOOTSTRAP_NAME', 'ADMIN_BOOTSTRAP_EMAIL',
                'ADMIN_BOOTSTRAP_PASSWORD'):
        if not os.environ.get(key, '').strip():
            raise SystemExit(key + ' is required when ADMIN_BOOTSTRAP_ENABLED=true')

email_bootstrap = os.environ.get('ADMIN_EMAIL_BOOTSTRAP_ENABLED', 'false').strip().lower()
if email_bootstrap not in ('true', 'false'):
    raise SystemExit('ADMIN_EMAIL_BOOTSTRAP_ENABLED must be true or false')
if email_bootstrap == 'true':
    import re
    emails = os.environ.get('ADMIN_BOOTSTRAP_EMAILS', '').split(',')
    if not all(len(email.strip()) <= 255 and re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', email.strip()) for email in emails):
        raise SystemExit('ADMIN_BOOTSTRAP_EMAILS must contain comma-separated valid email addresses')
    if bootstrap_enabled == 'true':
        raise SystemExit('Enable only one admin bootstrap mode at a time')


provider = os.environ.get('APP_EMAIL_PROVIDER', 'smtp') or 'smtp'
if provider not in ('smtp', 'graph'):
    raise SystemExit('APP_EMAIL_PROVIDER must be smtp or graph')
if provider == 'graph':
    for key in ('TENANT_ID', 'CLIENT_ID', 'CLIENT_SECRET'):
        if not (os.environ.get('MICROSOFT_GRAPH_' + key) or os.environ.get('GRAPH_' + key)):
            raise SystemExit('MICROSOFT_GRAPH_' + key + ' is required for Graph email')
    import re
    if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', os.environ.get('APP_EMAIL_FROM', '')):
        raise SystemExit('APP_EMAIL_FROM must be the Graph sender mailbox email address')
elif not os.environ.get('SPRING_MAIL_USERNAME', '').strip():
    raise SystemExit('SPRING_MAIL_USERNAME is required for SMTP email')
print('Bingo production URLs, origins, bootstrap, and email provider configuration validated')
