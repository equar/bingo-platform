# Production Deployment

Production application deployment uses GitHub Actions to build Bingo and
upload it over SSH to an existing Ubuntu Lightsail/EC2 host. The sole
production application deployment workflow is `.github/workflows/deploy.yml`;
it runs after an in-repository pull request is merged into `develop`. Changed
application files select what is built and deployed from the merge commit.
Manual dispatch on `develop` can redeploy selected applications. This changes
the deployment trigger, not its target: a merge to `develop` deploys to the
existing production Lightsail host with the production configuration.
There is no OIDC deployment role or required source-branch naming convention.

## Runtime layout and deployment behavior

| Component | Production location |
|---|---|
| Backend JAR | `/opt/bingo/backend.jar`, service `bingo-backend` |
| Backend environment | `/opt/bingo/.env`, root-owned mode 600 |
| Admin SPA | `/var/www/bingo-admin` |
| Public website | `/var/www/bingo-website` |
| MySQL schema | `bingo` |
| API listener | `127.0.0.1:8081` |

The workflow builds selected applications only: the Java 25 backend with Maven,
and the Admin/Astro frontends with Node 22, then deploys them over SSH. PR validation is the test gate:
`.github/workflows/pr-validation-pipeline.yml` runs backend tests against
disposable MySQL, Admin tests/lint/build, and website checks. Require this
validation in branch protection. The deployment package step skips backend
tests. `.github/workflows/validate-lightsail-infrastructure.yml` checks
deployment shell/Python syntax, the Nginx template, and the retained
CloudFormation reference; it does not deploy.

Backend artifacts stage in a private `~/.cache/bingo-deploy` directory owned
by the deployment SSH user, rather than relying on shared `/tmp` permissions.

Before stopping a running backend, the deployer reads the candidate JAR's
Flyway migrations and compares its highest version with the production schema
history. It refuses to replace the service when the candidate is behind the
database, preventing an older JAR from starting against a newer schema. The
deployer has one narrowly scoped recovery for the withdrawn
`V41__remove_legacy_prize_winner_caps.sql` migration: it removes that failed
history entry only when it is the sole failed migration and the candidate JAR
does not contain V41. Any other failed migration still blocks deployment for
manual investigation.
backend deploy retains the previous JAR/environment pair and attempts
restoration after a failed restart/health check only if the previous pair was
healthy and Flyway did not advance during the failed attempt. A JAR rollback
is unsafe after any migration is applied, because the previous JAR may not
match the new schema. Static deployments retain `.backup` directories.
Health-check failures fail the workflow. Database migrations are not
automatically rolled back; take a database snapshot before a risky Flyway
release. A failed frontend check can be recovered from its backup. Fix the
cause and use the normal reviewed merge/deployment path.

The backend is intentionally single-instance because scheduler and WebSocket
state are process-local. Configure monitoring, log retention, backups, restore
ownership, and incident procedures before production use.

## Server, database, DNS, and HTTPS setup

1. Provision a separate Ubuntu Lightsail/EC2 server with a static IP. Install
   Java 25 at `/usr/bin/java`, Nginx, curl, Certbot, and gettext-base. The
   deployment SSH user needs noninteractive sudo. Permit SSH from the runner
   and inbound HTTP/HTTPS; do not expose port 8081 publicly.
2. Provision MySQL schema `bingo` and a separate application identity scoped
   to `bingo.*` with the privileges required by Flyway and the application.
   The workflow does not create the database. Keep it private and use its DNS
   hostname for `MYSQL_HOST`; production JDBC requires `sslMode=VERIFY_IDENTITY`.
   The    deployment prepares `/etc/bingo/database-truststore.jks` and
   `/etc/bingo/database-ca-bundle.pem` from Java's default roots plus the
   official AWS database CA bundle. The PEM bundle is used by the deployment
   migration-compatibility preflight; neither file contains an application
   credential.
3. Point `bingo.bakaroo.com`, `admin.bingo.bakaroo.com`, and
   `api.bingo.bakaroo.com` to the server's static IP. After DNS resolves, copy
   `deployment/aws/setup-web-host.sh` and `nginx-bingo.conf.template` to the
   server and run `sudo bash setup-web-host.sh`. The script installs Bingo
   virtual hosts, obtains the `bingo-platform` certificate, validates HTTPS,
   and restores the previous Bingo Nginx configuration if setup fails.
   Existing Certbot renewal timers are reused.

   For manual Nginx setup, render only the three domain variables:

   ```bash
   export SITE_DOMAIN=bingo.bakaroo.com ADMIN_DOMAIN=admin.bingo.bakaroo.com API_DOMAIN=api.bingo.bakaroo.com
   envsubst '${SITE_DOMAIN} ${ADMIN_DOMAIN} ${API_DOMAIN}' < deployment/aws/nginx-bingo.conf.template > /tmp/nginx-bingo.conf
   sudo install -m 644 /tmp/nginx-bingo.conf /etc/nginx/sites-available/bingo.conf
   sudo ln -sfn /etc/nginx/sites-available/bingo.conf /etc/nginx/sites-enabled/bingo.conf
   sudo nginx -t
   sudo systemctl reload nginx
   ```

The deployment scripts do not configure Nginx or certificates. During initial
SSH setup, verify the server host fingerprint out of band; `ssh-keyscan` alone
does not authenticate the host. The SSH workflow requires reachable port 22
and passwordless sudo for the deployment account.

## GitHub Actions configuration

For automated import, copy `deployment/aws/github-actions.env.example` to the
ignored `deployment/aws/github-actions.env`, enter one `KEY=VALUE` per line,
and run from the repository root with an authenticated `gh` CLI:

```bash
bash deployment/aws/bootstrap-github-actions-config.sh deployment/aws/github-actions.env
```

Set `GH_REPO=owner/bingo-platform` if repository detection is unavailable.
The importer writes repository secrets/variables only; it does not provision
AWS or deploy. Do not put real values in the example file or commit the ignored
configuration.

Repository **secrets**:

| Name | Purpose |
|---|---|
| `LIGHTSAIL_HOST`, `LIGHTSAIL_USER` | Server address and SSH account |
| `LIGHTSAIL_SSH_KEY_B64` or `LIGHTSAIL_SSH_KEY` | Preferred Base64 or optional raw/newline-escaped private key |
| `MYSQL_PASSWORD` | Application database password |
| `JWT_SECRET` | Random signing secret of at least 32 characters |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP credentials when authenticated delivery is required |
| `BINGO_FLOAT_BANK_ENCRYPTION_KEY` | Base64 encoding of 32 random bytes; back up and preserve it across deployments |
| `ADMIN_BOOTSTRAP_PASSWORD` | One-time secret only while the bootstrap is explicitly enabled |

Repository **variables**:

| Name | Purpose/default |
|---|---|
| `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_USER` | Database DNS, `3306`, and application user |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `3600` |
| `CORS_ALLOWED_ORIGINS`, `WEBSOCKET_ALLOWED_ORIGINS` | Exact comma-separated browser origins, including Admin HTTPS origin |
| `ADMIN_ACCOUNT_ACTIVATION_URL` | `https://admin.bingo.bakaroo.com/activate-account` |
| `ADMIN_BOOTSTRAP_ENABLED` | `false`; enable only for one-time initial Admin setup |
| `ADMIN_BOOTSTRAP_PHONE`, `ADMIN_BOOTSTRAP_NAME`, `ADMIN_BOOTSTRAP_EMAIL` | Initial Admin identity, only while bootstrap is enabled |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP provider; Gmail defaults are `smtp.gmail.com`, `587` |
| `VITE_API_BASE_URL`, `ADMIN_PUBLIC_URL` | Admin HTTPS API origin and public origin |
| `PUBLIC_SITE_URL`, `PUBLIC_SITE_INDEXABLE` | Website origin and approved indexing state |
| `PUBLIC_ANDROID_AVAILABILITY`, `PUBLIC_ANDROID_URL`, `PUBLIC_IOS_AVAILABILITY`, `PUBLIC_IOS_URL` | Optional public app download state/URLs |
| `PUBLIC_SUPPORT_EMAIL`, `PUBLIC_SUPPORT_TELEGRAM_URL`, `PUBLIC_SUPPORT_WHATSAPP_URL` | Optional public contact channels |
| `APP_PAYMENT_PROVIDER_NAME`, `APP_PAYMENT_BUSINESS_ACCOUNT`, `APP_PAYMENT_INSTRUCTIONS` | Public payment instructions; defaults to Telebirr |

Production profile, schema `bingo`, port 8081, loopback binding, Hibernate
validation, and disabled development seeding are fixed by the deployment
scripts. This workflow does not require AWS access keys, an OIDC role, or
Secrets Manager ARNs.

## Release and verification

1. Protect `develop` with the required branch-policy and PR validation checks.
2. For a host using the retired OIDC deployment, stop its workflow and back
   up the database, runtime configuration, and artifacts. The current service
   uses `/opt/bingo`, not the old `/opt/bingo/backend/current` layout. Plan the
   JAR/service and static-root cutover together with Nginx; back up existing
   files first.
3. Merge an in-repository PR into `develop`. The deployment workflow
   checks out the merge commit and serializes production runs without
   cancelling a previous merge's deployment.
4. Verify website, Admin login, `/actuator/health`, CORS, authenticated
   WebSockets, email activation, encrypted receiving bank accounts, and
   persistence. Confirm Flyway and application health from sanitized logs.
5. Before any store release, configure
   `BINGO_MOBILE_API_BASE_URL=https://api.bingo.bakaroo.com`; agent release
   builds must also receive `--dart-define=API_BASE_URL=https://api.bingo.bakaroo.com`.

The old manual backend/Admin/all workflows, `deployment/lightsail/`, and
`lightsail-stack.yaml` describe reference/retired paths with different
filesystem layouts and secret handling. Do not deploy the OIDC CloudFormation
stack unchanged or run its old scripts against this SSH service. Mobile
publishing is separate; see the [mobile release guide](mobile/README.md).

The one-time first-admin procedure is documented in
[Admin bootstrap](aws/ADMIN_BOOTSTRAP.md). It is disabled by default, creates
only the requested first administrator, and must be disabled and cleaned up
after the initial password is changed.

Email delivery is selected with `APP_EMAIL_PROVIDER=smtp|graph`. See
[Email providers](aws/EMAIL_PROVIDERS.md) for Graph credentials, sender mailbox,
and SMTP settings. Both providers support deployment admin bootstrap.

## Selective deployments

For a merged PR, the workflow compares the merged commit with its first parent:

| Changed files | Applications deployed |
|---|---|
| `bingo-backend/` | Backend only; Flyway runs on backend startup |
| `bingo-admin/` | Admin only; backend remains running |
| `bingo-website/` | Public website only |
| Backend deploy script, service unit, or RDS truststore script | Backend only |
| Frontend deploy script | Both frontends |
| Deployment workflow or other shared AWS deployment configuration | All three |
| Markdown documentation or mobile-only files | None |

Changes spanning multiple applications select all affected applications. Removed
and renamed files count as changes. Frontend uploads replace only selected static
roots and preserve their individual backups. Selected frontends still require
working HTTPS and a healthy API for verification.

To deploy after changing repository secrets/variables, or to redeploy without a
source change, use **Actions → Build and Deploy Bingo to Lightsail → Run workflow**,
select branch **develop**, and choose `all`, `backend`, `frontend`, `admin`, or `website`.
`frontend` selects both frontends. Other branches cannot use manual production
deployment. Existing concurrency protection serializes these deployments.

The diff selects changes from the current merge, not from the last successful
deployment. Resolve and rerun failed deployments before subsequent releases;
if deployments were skipped or a previous deployment partly failed, manually deploy
`all` from the latest `develop` to reconcile all application artifacts. Manually
redeploying an older workflow run can deploy older code, so prefer this latest-develop
manual action for recovery. Nginx and certificate setup remain separate one-time
host operations.

For direct scripted frontend deployment, pass `admin`, `website`, or `all` to
`deployment/aws/deploy-frontend.sh`; omitting the argument retains the original
behavior of uploading both frontends.
