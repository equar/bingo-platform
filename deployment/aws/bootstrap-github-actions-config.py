"""Import Bingo repository configuration using gh; never execute the env file."""
import os
import subprocess
import sys
from pathlib import Path
SECRETS = ['LIGHTSAIL_HOST', 'LIGHTSAIL_USER', 'LIGHTSAIL_SSH_KEY_B64', 'LIGHTSAIL_SSH_KEY', 'MYSQL_PASSWORD', 'JWT_SECRET', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD', 'BINGO_FLOAT_BANK_ENCRYPTION_KEY', 'ADMIN_BOOTSTRAP_PASSWORD']
VARIABLES = ['MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_USER', 'JWT_ACCESS_TOKEN_TTL_SECONDS', 'CORS_ALLOWED_ORIGINS', 'WEBSOCKET_ALLOWED_ORIGINS', 'ADMIN_ACCOUNT_ACTIVATION_URL', 'ADMIN_BOOTSTRAP_ENABLED', 'ADMIN_BOOTSTRAP_PHONE', 'ADMIN_BOOTSTRAP_NAME', 'ADMIN_BOOTSTRAP_EMAIL', 'SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'VITE_API_BASE_URL', 'ADMIN_PUBLIC_URL', 'PUBLIC_SITE_URL', 'PUBLIC_SITE_INDEXABLE', 'PUBLIC_ANDROID_AVAILABILITY', 'PUBLIC_ANDROID_URL', 'PUBLIC_IOS_AVAILABILITY', 'PUBLIC_IOS_URL', 'PUBLIC_SUPPORT_EMAIL', 'PUBLIC_SUPPORT_TELEGRAM_URL', 'PUBLIC_SUPPORT_WHATSAPP_URL', 'APP_PAYMENT_PROVIDER_NAME', 'APP_PAYMENT_BUSINESS_ACCOUNT', 'APP_PAYMENT_INSTRUCTIONS']
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
if not (values.get('LIGHTSAIL_SSH_KEY_B64') or values.get('LIGHTSAIL_SSH_KEY')):
    raise SystemExit('An SSH private key is required')
repo = os.environ.get('GH_REPO') or subprocess.check_output(['gh', 'repo', 'view', '--json', 'nameWithOwner', '-q', '.nameWithOwner'], text=True).strip()
subprocess.run(['gh', 'auth', 'status'], check=True)
for key, value in values.items():
    if not value: continue
    if key in SECRETS:
        subprocess.run(['gh', 'secret', 'set', key, '--repo', repo], input=value, text=True, check=True)
    else:
        subprocess.run(['gh', 'variable', 'set', key, '--repo', repo, '--body', value], check=True)
    print('Configured ' + key)
