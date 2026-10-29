# Bingo deployment setup checklist

## Prepare the server and database

1. Provision/use a separate Bingo Ubuntu Lightsail/EC2 server and static IP.
   Install Java 25 at `/usr/bin/java`, Nginx, curl, Certbot, and gettext-base.
   The deployment SSH account must support noninteractive sudo. Authorize its
   public SSH key, permit SSH from the deployment runner, and expose 80/443.
   Do not expose the backend port 8088 publicly.
2. Create the MySQL database **`bingo`**, with a separate application user
   such as `bingo_app`, and grant privileges on `bingo.*` required by Flyway
   and the application. Provision the database before deployment: the workflow
   does not create it. Keep the database private and reachable by this server.
   Production JDBC uses `sslMode=VERIFY_IDENTITY`; use its DNS hostname as
   `MYSQL_HOST`. The deployment creates `/etc/bingo/database-truststore.jks`
   from the Java default roots plus the official AWS database root CA bundle.
   The server needs curl, OpenSSL, Python 3, keytool, and outbound HTTPS to
   `truststore.pki.rds.amazonaws.com`. Only the Bingo JVM uses this truststore.
3. Point the A records for `bingo.bakaroo.com`, `admin.bingo.bakaroo.com`,
   and `api.bingo.bakaroo.com` at the server static IP.
4. Issue a certificate using Certbot's webroot flow for all three hosts, with
   certificate name `bingo-platform` and webroot `/var/www/letsencrypt`.
   Configure an initial HTTP server for the ACME challenge before issuance.
   Render `nginx-bingo.conf.template` using **only** its three domain variables:

   ```bash
   export SITE_DOMAIN=bingo.bakaroo.com ADMIN_DOMAIN=admin.bingo.bakaroo.com API_DOMAIN=api.bingo.bakaroo.com
   envsubst '${SITE_DOMAIN} ${ADMIN_DOMAIN} ${API_DOMAIN}' < deployment/aws/nginx-bingo.conf.template > /tmp/nginx-bingo.conf
   sudo install -m 644 /tmp/nginx-bingo.conf /etc/nginx/sites-available/bingo.conf
   sudo ln -sfn /etc/nginx/sites-available/bingo.conf /etc/nginx/sites-enabled/bingo.conf
   sudo nginx -t
   sudo systemctl reload nginx
   ```

   Configure certificate renewal to reload Nginx.
   The deployment scripts do not change Nginx or certificates.

## Configure GitHub repository settings

Copy `github-actions.env.example` to `github-actions.env` in this directory.
The real file is ignored by Git. Fill in your own Bingo configuration.
The importer does not execute the file and supports one `KEY=VALUE` per line.
Then use an authenticated GitHub CLI from this repository:

```bash
bash deployment/aws/bootstrap-github-actions-config.sh deployment/aws/github-actions.env
```

Keep `ADMIN_BOOTSTRAP_ENABLED=false` in the normal deployment configuration.
For the one-time first Admin login, follow [ADMIN_BOOTSTRAP.md](ADMIN_BOOTSTRAP.md)
and supply its password only as the `ADMIN_BOOTSTRAP_PASSWORD` repository secret.

Set `GH_REPO=owner/bingo-platform` explicitly if necessary. The command writes
repository secrets/variables; it never provisions AWS or deploys applications.

Repository **secrets**:

| Name | Value |
|---|---|
| `LIGHTSAIL_HOST` | Bingo server DNS name or IPv4 |
| `LIGHTSAIL_USER` | SSH account, e.g. `ubuntu` |
| `LIGHTSAIL_SSH_KEY_B64` | Preferred: Base64 private key, one line |
| `LIGHTSAIL_SSH_KEY` | Optional alternative: raw or newline-escaped private key |
| `MYSQL_PASSWORD` | Bingo application database password |
| `JWT_SECRET` | Strong random signing secret, at least 32 characters |
| `SPRING_MAIL_USERNAME` | Production SMTP username |
| `SPRING_MAIL_PASSWORD` | Optional for deployment; SMTP password/app password when authenticated email delivery is needed |
| `BINGO_FLOAT_BANK_ENCRYPTION_KEY` | Base64 encoding of 32 random bytes; back it up and preserve it across deployments |
| `ADMIN_BOOTSTRAP_PASSWORD` | One-time secret, only while `ADMIN_BOOTSTRAP_ENABLED=true`; use at least 16 characters and no more than 72 UTF-8 bytes |

Repository **variables** (all are listed in the example file):

| Name | Value/default |
|---|---|
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER` | Database DNS, `3306`, `bingo_app` |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `3600` |
| `CORS_ALLOWED_ORIGINS`, `WEBSOCKET_ALLOWED_ORIGINS` | Comma-separated origins including exact admin HTTPS origin; add browser player/agent origins when applicable |
| `ADMIN_ACCOUNT_ACTIVATION_URL` | `https://admin.bingo.bakaroo.com/activate-account` |
| `ADMIN_BOOTSTRAP_ENABLED` | `false` by default; set to `true` only for initial administrator setup |
| `ADMIN_BOOTSTRAP_PHONE`, `ADMIN_BOOTSTRAP_NAME`, `ADMIN_BOOTSTRAP_EMAIL` | Initial administrator identity; phone must be a valid Ethiopian mobile number and email is used by Admin login |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | `smtp.gmail.com`, `587`, or your SMTP provider |
| `VITE_API_BASE_URL` | `https://api.bingo.bakaroo.com` |
| `ADMIN_PUBLIC_URL` | `https://admin.bingo.bakaroo.com` |
| `PUBLIC_SITE_URL` | Website HTTPS origin, no trailing slash |
| `PUBLIC_SITE_INDEXABLE` | Exactly `true` or `false` |
| `PUBLIC_ANDROID_AVAILABILITY`, `PUBLIC_ANDROID_URL` | Optional public download configuration |
| `PUBLIC_IOS_AVAILABILITY`, `PUBLIC_IOS_URL` | Optional public download configuration |
| `PUBLIC_SUPPORT_EMAIL`, `PUBLIC_SUPPORT_TELEGRAM_URL`, `PUBLIC_SUPPORT_WHATSAPP_URL` | Optional public support contacts |
| `APP_PAYMENT_PROVIDER_NAME`, `APP_PAYMENT_BUSINESS_ACCOUNT`, `APP_PAYMENT_INSTRUCTIONS` | Production payment instructions; provider defaults to Telebirr |

`MYSQL_DATABASE=bingo`, production profile, port 8088, loopback binding,
Hibernate validation, and disabled development seeding are fixed by the deploy
script. No AWS access keys, OIDC role, or Secrets Manager ARNs are required.
Do not copy credentials, host addresses, or Graph settings from BillPay.

## Release and verify

1. Protect `main` with required PR validation and the branch-flow guard. Protect
   workflow files with appropriate repository permissions/review rules.
2. For an existing OIDC deployment, stop its workflow and back up its DB,
   runtime configuration, and artifacts. This workflow installs the
   `bingo-backend` unit pointing at `/opt/bingo/backend.jar`, replacing the
   prior service unit. Static roots change from `current` links to direct
   directories; plan the Nginx change together with the first deployment.
3. Merge a feature-branch PR directly into `main` (any source branch name).
   `Build and Deploy Bingo to Lightsail` runs automatically. GitHub repository
   Actions must permit the configured action versions. Tests run on the PR;
   deployment packaging skips tests like BillPay.
4. Verify website, admin login, API `/actuator/health`, CORS, live WebSockets,
   email delivery/activation, encrypted receiving bank accounts, and persistence.
5. Set `BINGO_MOBILE_API_BASE_URL` for the existing player store workflows to
   `https://api.bingo.bakaroo.com`, and use
   `--dart-define=API_BASE_URL=https://api.bingo.bakaroo.com` for
   agent release builds. Mobile app publication remains separate.

No server, database, DNS records, repository secrets, or branch protections
are created merely by checking in these files. No live deployment has been
verified by this implementation.
