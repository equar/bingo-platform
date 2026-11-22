"""Validate public deployment settings before building or connecting to a host."""
import os
import base64
from datetime import datetime, timedelta, timezone
from urllib.parse import urlsplit
origins = {}
deployment_environment = os.environ.get('DEPLOY_ENVIRONMENT', 'prod')
if deployment_environment not in ('test', 'prod'):
    raise SystemExit('DEPLOY_ENVIRONMENT must be test or prod')
allowed_schemes = {'http', 'https'} if deployment_environment == 'test' else {'https'}
for key in ('VITE_API_BASE_URL', 'ADMIN_PUBLIC_URL', 'PUBLIC_SITE_URL'):
    value = os.environ.get(key, '')
    url = urlsplit(value)
    if (url.scheme not in allowed_schemes or not url.hostname or url.username or url.password
            or url.path or url.query or url.fragment
            or url.hostname in ('localhost', '127.0.0.1', '::1')):
        schemes = 'HTTP or HTTPS' if deployment_environment == 'test' else 'HTTPS'
        raise SystemExit(key + ' must be a public ' + schemes + ' origin without a trailing slash or path')
    origins[key] = value
expected_hosts = (
    {'api.test.bakaroo.com', 'admin.test.bakaroo.com', 'test.bakaroo.com'}
    if deployment_environment == 'test'
    else {'api.bingo.bakaroo.com', 'admin.bingo.bakaroo.com', 'bingo.bakaroo.com'}
)
if {urlsplit(value).hostname for value in origins.values()} != expected_hosts:
    raise SystemExit('Configured API, Admin, and website hosts do not match DEPLOY_ENVIRONMENT')
if os.environ.get('PUBLIC_SITE_INDEXABLE') not in ('true', 'false'):
    raise SystemExit('PUBLIC_SITE_INDEXABLE must be true or false')
for key in ('CORS_ALLOWED_ORIGINS', 'WEBSOCKET_ALLOWED_ORIGINS'):
    allowed = {origin.strip().rstrip('/') for origin in os.environ.get(key, '').split(',')}
    if not {origins['ADMIN_PUBLIC_URL'], origins['PUBLIC_SITE_URL']}.issubset(allowed):
        raise SystemExit(key + ' must include ADMIN_PUBLIC_URL and PUBLIC_SITE_URL')
activation = urlsplit(os.environ.get('ADMIN_ACCOUNT_ACTIVATION_URL', ''))
admin_url = urlsplit(origins['ADMIN_PUBLIC_URL'])
if (activation.scheme != admin_url.scheme or activation.netloc != admin_url.netloc
        or not activation.path or activation.username or activation.password):
    raise SystemExit('ADMIN_ACCOUNT_ACTIVATION_URL must use the admin origin and activation route')
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

if bootstrap_enabled == 'true' or email_bootstrap == 'true':
    if deployment_environment != 'prod':
        raise SystemExit('Admin bootstrap is permitted only for an explicitly approved production deployment')
    if os.environ.get('ADMIN_BOOTSTRAP_APPROVED', '').strip().lower() != 'true':
        raise SystemExit('Admin bootstrap requires approve_initial_admin_bootstrap=true for this workflow run')
    raw_expiry = os.environ.get('ADMIN_BOOTSTRAP_APPROVAL_EXPIRES_AT', '').strip()
    try:
        if not raw_expiry.endswith('Z'):
            raise ValueError()
        expiry = datetime.fromisoformat(raw_expiry.replace('Z', '+00:00'))
        if expiry.tzinfo is None:
            raise ValueError()
        expiry = expiry.astimezone(timezone.utc)
    except ValueError:
        raise SystemExit('ADMIN_BOOTSTRAP_APPROVAL_EXPIRES_AT must be a UTC RFC3339 timestamp')
    now = datetime.now(timezone.utc)
    if not now < expiry <= now + timedelta(minutes=60):
        raise SystemExit('Admin bootstrap approval must be unexpired and expire within 60 minutes')


provider = os.environ.get('APP_EMAIL_PROVIDER', '').strip()
if provider not in ('smtp', 'graph'):
    raise SystemExit('APP_EMAIL_PROVIDER must be explicitly set to smtp or graph')
if provider == 'graph':
    for key in ('TENANT_ID', 'CLIENT_ID', 'CLIENT_SECRET'):
        if not os.environ.get('MICROSOFT_GRAPH_' + key):
            raise SystemExit('MICROSOFT_GRAPH_' + key + ' is required for Graph email')
    import re
    if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', os.environ.get('APP_EMAIL_FROM', '')):
        raise SystemExit('APP_EMAIL_FROM must be the Graph sender mailbox email address')
elif not os.environ.get('SPRING_MAIL_USERNAME', '').strip():
    raise SystemExit('SPRING_MAIL_USERNAME is required for SMTP email')

key = os.environ.get('BINGO_FLOAT_BANK_ENCRYPTION_KEY', '').strip()
try:
    if len(base64.b64decode(key, validate=True)) != 32:
        raise ValueError()
except Exception:
    raise SystemExit('BINGO_FLOAT_BANK_ENCRYPTION_KEY must be Base64 encoding of exactly 32 bytes')
print('Bingo deployment URLs, origins, bootstrap, and email provider configuration validated')
