"""Import Bingo repository configuration using gh; never execute the env file."""
import os
import subprocess
import sys
from pathlib import Path
SECRETS = ['DEPLOY_SSH_KEY_B64', 'MYSQL_PASSWORD', 'JWT_SECRET', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD', 'BINGO_FLOAT_BANK_ENCRYPTION_KEY', 'ADMIN_BOOTSTRAP_PASSWORD', 'MICROSOFT_GRAPH_TENANT_ID', 'MICROSOFT_GRAPH_CLIENT_ID', 'MICROSOFT_GRAPH_CLIENT_SECRET']
VARIABLES = ['DEPLOY_HOST', 'DEPLOY_USER', 'APP_EMAIL_PROVIDER', 'APP_EMAIL_FROM', 'ADMIN_EMAIL_BOOTSTRAP_ENABLED', 'ADMIN_BOOTSTRAP_EMAILS', 'MYSQL_HOST', 'MYSQL_USER', 'CORS_ALLOWED_ORIGINS', 'WEBSOCKET_ALLOWED_ORIGINS', 'ADMIN_ACCOUNT_ACTIVATION_URL', 'ADMIN_BOOTSTRAP_ENABLED', 'ADMIN_BOOTSTRAP_PHONE', 'ADMIN_BOOTSTRAP_NAME', 'ADMIN_BOOTSTRAP_EMAIL', 'SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'VITE_API_BASE_URL', 'ADMIN_PUBLIC_URL', 'PUBLIC_SITE_URL', 'PUBLIC_SITE_INDEXABLE', 'PUBLIC_ANDROID_AVAILABILITY', 'PUBLIC_ANDROID_URL', 'PUBLIC_IOS_AVAILABILITY', 'PUBLIC_IOS_URL', 'PUBLIC_SUPPORT_EMAIL', 'PUBLIC_SUPPORT_TELEGRAM_URL', 'PUBLIC_SUPPORT_WHATSAPP_URL', 'APP_PAYMENT_PROVIDER_NAME', 'APP_PAYMENT_BUSINESS_ACCOUNT', 'APP_PAYMENT_INSTRUCTIONS', 'APP_GAME_PAYOUTS_ENABLED']
values = {}
for line in Path(sys.argv[1]).read_text().splitlines():
    if not line.strip() or line.lstrip().startswith('#'): continue
    if '=' not in line: raise SystemExit('Expected KEY=VALUE')
    key, value = line.split('=', 1)
    key, value = key.strip(), value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'": value = value[1:-1]
    if key not in SECRETS + VARIABLES: raise SystemExit('Unknown setting: ' + key)
    if key in values: raise SystemExit('Duplicate setting: ' + key)
    if value.startswith(('REPLACE_', 'YOUR_')): raise SystemExit('Replace placeholder: ' + key)
    values[key] = value
environment = os.environ.get('GH_ENVIRONMENT')
if environment not in ('test', 'production'):
    raise SystemExit('GH_ENVIRONMENT must be test or production; deployment settings must be environment-scoped')
for required in ('DEPLOY_HOST', 'DEPLOY_USER', 'DEPLOY_SSH_KEY_B64', 'MYSQL_HOST', 'MYSQL_USER',
                 'MYSQL_PASSWORD', 'JWT_SECRET', 'BINGO_FLOAT_BANK_ENCRYPTION_KEY',
                 'APP_EMAIL_PROVIDER', 'VITE_API_BASE_URL', 'ADMIN_PUBLIC_URL', 'PUBLIC_SITE_URL',
                 'CORS_ALLOWED_ORIGINS', 'WEBSOCKET_ALLOWED_ORIGINS', 'ADMIN_ACCOUNT_ACTIVATION_URL'):
    if not values.get(required):
        raise SystemExit(required + ' is required for a deployment environment')
provider = values['APP_EMAIL_PROVIDER']
if provider not in ('smtp', 'graph'):
    raise SystemExit('APP_EMAIL_PROVIDER must be smtp or graph')
if provider == 'graph':
    if not values.get('APP_EMAIL_FROM'):
        raise SystemExit('APP_EMAIL_FROM is required for Graph email')
    if any(not values.get('MICROSOFT_GRAPH_' + suffix)
           for suffix in ('TENANT_ID', 'CLIENT_ID', 'CLIENT_SECRET')):
        raise SystemExit('All MICROSOFT_GRAPH_* secrets are required for Graph email')
if provider == 'smtp' and (not values.get('SPRING_MAIL_USERNAME') or not values.get('SPRING_MAIL_PASSWORD')):
    raise SystemExit('SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD are required for SMTP email')
repo = os.environ.get('GH_REPO') or subprocess.check_output(['gh', 'repo', 'view', '--json', 'nameWithOwner', '-q', '.nameWithOwner'], text=True).strip()
subprocess.run(['gh', 'auth', 'status'], check=True)
for key, value in values.items():
    if not value: continue
    if key in SECRETS:
        command = ['gh', 'secret', 'set', key, '--repo', repo]
    else:
        command = ['gh', 'variable', 'set', key, '--repo', repo, '--body', value]
    if environment:
        command.extend(['--env', environment])
    subprocess.run(command, input=value if key in SECRETS else None, text=True, check=True)
    print('Configured ' + key)
