# Test and Production Deployment

Application deployments use `.github/workflows/deploy.yml` to build selected
components and upload them over SSH to an Ubuntu Lightsail production host or
the Terraform-provisioned EC2 test host.
Merging an in-repository pull request into `develop` deploys to the isolated
`test` GitHub Environment; merging into `release` deploys to the `production`
Environment. Manual dispatch is supported on either branch. Configure each
GitHub Environment with its own host, database connection, secrets, and public
URLs; production should require an environment reviewer before deployment.
There is no OIDC deployment role.

For the complete infrastructure, application, and mobile release flows, see
the [deployment architecture diagram](../.github/modernize/assessment/engines/facts/architecture-diagram.md).
The [variable inventory](../deploymentVariables.txt) lists each input, where
to obtain it, how to generate secrets, and the current GitHub configuration
status. For the ordered setup from prerequisites through production release,
follow the [deployment steps](../deployment-steps.txt).

## Provisioning commands

Infrastructure provisioning is separate from application deployments. From
the repository root, copy the example files and replace the public-key, CIDR,
and production bundle placeholders:

```bash
aws sts get-caller-identity
cd deployment/terraform
cp env/test.tfvars.example env/test.tfvars
cp env/prod.tfvars.example env/prod.tfvars
./provision.sh test plan
./provision.sh prod plan
```

Review each full plan before applying. Test applies use the GoDaddy PAT only
when `route53_zone_id` is empty; Terraform then syncs the test A records to the
Elastic IP. Production domain records are configured separately. The
[Terraform runbook](terraform/README.md) and [variable inventory](../deploymentVariables.txt)
contain secure secret entry, outputs, and setup commands.

## Runtime layout and deployment behavior

| Component | Test environment | Production environment |
|---|---|---|
| Backend JAR | `/opt/bingo/backend.jar`, service `bingo-backend` | `/opt/bingo/backend.jar`, service `bingo-backend` |
| Backend environment | `/opt/bingo/.env`, root-owned mode 600 | `/opt/bingo/.env`, root-owned mode 600 |
| Admin SPA | `/var/www/bingo-admin` | `/var/www/bingo-admin` |
| Public website | `/var/www/bingo-website` | `/var/www/bingo-website` |
| MySQL schema | Local MySQL `bingotest` on EC2 | Managed MySQL `bingo` |
| API listener | `127.0.0.1:8091` | `127.0.0.1:8081` |

The workflow builds selected applications only: the Java 25 backend with Maven,
and the Admin/Astro frontends with Node 22, then deploys them over SSH. PR validation is the test gate:
`.github/workflows/pr-validation-pipeline.yml` runs backend tests against
disposable MySQL, Admin tests/lint/build, and website checks. Require this
validation in branch protection. The deployment package step skips backend
tests. `.github/workflows/validate-deployment-infrastructure.yml` checks
Terraform formatting/validation, deployment shell/Python syntax, and rendered
Nginx configurations; it does not deploy.

Backend and frontend artifacts stage in a private `~/.cache/bingo-deploy`
directory owned by the deployment SSH user, rather than relying on shared
`/tmp` permissions.

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

## Server, database, DNS, and HTTP test setup

1. Provision production Lightsail with the production Terraform plan. Provision test with
   `deployment/terraform/provision.sh test apply`; Terraform creates a
   `t3.micro` EC2 host and associates a billable Elastic IP, installs Java 25,
   Node 22, Nginx, and local MySQL, and creates HTTP-only virtual hosts. When
   GoDaddy is selected, export `GODADDY_PAT` before apply; Terraform creates or
   updates `test.bakaroo.com`, `admin.test.bakaroo.com`, and
   `api.test.bakaroo.com` after associating the Elastic IP. With a non-empty
   `route53_zone_id`, Terraform uses Route 53 instead. No TLS certificate is
   needed for test. Wait for `cloud-init status --wait` and confirm all three
   names resolve before deploying.
2. The test EC2 host creates a local `bingotest` schema and app user on its
   first backend deployment. Test MySQL listens only on loopback; port 3306 is
   not public. Test JDBC disables TLS only for that loopback connection.
   RSA key retrieval is enabled only for this local test connection.
   Production retains the private `bingo` managed database, requires
   `sslMode=VERIFY_IDENTITY`, and disables key retrieval. The production deployment prepares
   `/etc/bingo/database-truststore.jks` and
   `/etc/bingo/database-ca-bundle.pem` from Java's default roots plus the
   official AWS database CA bundle. The PEM bundle is used by the deployment
   migration-compatibility preflight; neither file contains an application
   credential.
3. Use Terraform's `web_static_ip` output to point production
   `bingo.bakaroo.com`, `admin.bingo.bakaroo.com`, and
   `api.bingo.bakaroo.com` to the Lightsail host. After DNS resolves, copy
   `deployment/aws/setup-web-host.sh` and `nginx-bingo.conf.template` to the
   server and run `sudo bash setup-web-host.sh`. The script installs Bingo
   virtual hosts, obtains the `bingo-platform` certificate, validates HTTPS,
   and restores the previous Bingo Nginx configuration if setup fails.
   Existing Certbot renewal timers are reused.
   Test Nginx serves HTTP and routes the API to loopback port 8091; production
   remains HTTPS and uses port 8081.

The test environment intentionally sends API credentials, session tokens,
Admin traffic, and WebSocket frames without encryption. Use test-only accounts
and data; do not use it over untrusted networks. Production deployment remains
HTTPS-only.

   For manual Nginx setup, render the three domain and backend-port variables:

   ```bash
   export SITE_DOMAIN=bingo.bakaroo.com ADMIN_DOMAIN=admin.bingo.bakaroo.com API_DOMAIN=api.bingo.bakaroo.com API_PORT=8081
   envsubst '${SITE_DOMAIN} ${ADMIN_DOMAIN} ${API_DOMAIN} ${API_PORT}' < deployment/aws/nginx-bingo.conf.template > /tmp/nginx-bingo.conf
   sudo install -m 644 /tmp/nginx-bingo.conf /etc/nginx/sites-available/bingo.conf
   sudo ln -sfn /etc/nginx/sites-available/bingo.conf /etc/nginx/sites-enabled/bingo.conf
   sudo nginx -t
   sudo systemctl reload nginx
   ```

The application deployment workflow does not configure production Nginx or
certificates; initial host setup is a separate operation.
During initial SSH setup, verify the server host fingerprint out of band;
`ssh-keyscan` alone does not authenticate the host. The SSH workflow requires
reachable port 22 and passwordless sudo for the deployment account.

## GitHub Actions configuration

Create GitHub Environments named `test` and `production`, and configure each
with its own host, credentials, origins, and URLs. After Terraform applies the test stack, use the stable Elastic IP from
`web_static_ip` as `DEPLOY_HOST` and `ubuntu` as `DEPLOY_USER` in the `test`
environment. Set `DEPLOY_SSH_KEY_B64` to the Base64-encoded private key matching
`ssh_public_key` in `env/test.tfvars`. Set test `MYSQL_HOST=127.0.0.1`,
`MYSQL_USER`, and the `MYSQL_PASSWORD` secret; the test workflow creates the
`bingotest` database user on the EC2 host. Keep that password stable unless
rotating it manually on the instance.

At this review, only `bingo-backend-production` and `copilot` exist in the
repository. Create `test` and `production` before application deployment, and
create `bingo-app-android-production` and `bingo-app-ios-production` with
required reviewers before mobile production promotion or submission. Do not
rely on an unprotected or empty environment. See the [complete variable
inventory](../deploymentVariables.txt) for required values, sources, and the
current state of repository-level settings.

**Production database gate:** the current repository-level `MYSQL_HOST` points
to RDS, while the production Terraform stack provisions Lightsail MySQL. The
application workflow does not migrate data between those databases, and the
backend deployment script does not create a production application DB user.
Keep the current production DB target until the intended database, schema, app
user/grants, credentials, data migration, and rollback plan are verified.

Set test `VITE_API_BASE_URL` to Terraform's `test_api_base_url`,
`ADMIN_PUBLIC_URL` to `test_admin_url`, and `PUBLIC_SITE_URL` to
`test_site_url`; set `ADMIN_ACCOUNT_ACTIVATION_URL` to
`http://admin.test.bakaroo.com/activate-account` and include
`http://admin.test.bakaroo.com` in both CORS variables. Internal Android
testing and TestFlight workflows default to
`http://api.test.bakaroo.com`. Terraform automatically syncs the three test
GoDaddy A records to the Elastic IP when `route53_zone_id` is empty; it uses
Route 53 when that variable is set. The test names are outside the production
`bingo.bakaroo.com` `includeSubDomains` HSTS scope. The internal mobile release workflows allow
HTTP only for this test API hostname; production keeps HTTPS. Keep the
two environments' database, JWT, encryption, email, and SSH credentials isolated. The test
environment uses the `bingotest` schema and test-only API host; the
production environment uses `bingo`. Configure production reviewers in
the repository's environment protection settings.

After the test host is ready and the `test` environment is configured, manually
run **Actions → Build and Deploy Bingo** on `develop` with target
`all` once. This deploys the initial backend and Admin/site assets; later
selective deployments can then update only changed components.

For automated import, copy `deployment/aws/github-actions.env.example` to an
ignored environment-specific file, enter one `KEY=VALUE` per line, and run
from the repository root with an authenticated `gh` CLI. The target environment
must already exist:

```bash
GH_ENVIRONMENT=production bash deployment/aws/bootstrap-github-actions-config.sh deployment/aws/github-actions-prod.env
GH_ENVIRONMENT=test bash deployment/aws/bootstrap-github-actions-config.sh deployment/aws/github-actions-test.env
```

Set `GH_REPO=owner/bingo-platform` if repository detection is unavailable.
The importer writes secrets/variables to the selected GitHub Environment; it
does not provision AWS or deploy. Do not put real values in example files or
commit the ignored configuration.

Environment **secrets**:

| Name | Purpose |
|---|---|
| `DEPLOY_SSH_KEY_B64` | Base64 encoding of the complete, unencrypted deployment SSH private key |
| `MYSQL_PASSWORD` | Application database password |
| `JWT_SECRET` | Random signing secret of at least 32 characters |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP credentials when authenticated delivery is required |
| `MICROSOFT_GRAPH_TENANT_ID`, `MICROSOFT_GRAPH_CLIENT_ID`, `MICROSOFT_GRAPH_CLIENT_SECRET` | Graph credentials when `APP_EMAIL_PROVIDER=graph` |
| `BINGO_FLOAT_BANK_ENCRYPTION_KEY` | Base64 encoding of 32 random bytes; back up and preserve it across deployments |
| `ADMIN_BOOTSTRAP_PASSWORD` | One-time secret only while the bootstrap is explicitly enabled |

Environment **variables**:

| Name | Purpose/default |
|---|---|
| `DEPLOY_HOST`, `DEPLOY_USER` | Target host address and SSH account; configure separately in each environment |
| `MYSQL_HOST`, `MYSQL_USER` | Database DNS and application user; the port defaults to `3306` |
| `APP_EMAIL_PROVIDER`, `APP_EMAIL_FROM` | Required email provider selection and authorized sender mailbox |
| `ADMIN_EMAIL_BOOTSTRAP_ENABLED`, `ADMIN_BOOTSTRAP_EMAILS` | Optional email-based Admin bootstrap; see the default behavior below |
| `CORS_ALLOWED_ORIGINS`, `WEBSOCKET_ALLOWED_ORIGINS` | Exact comma-separated browser origins; test uses HTTP, production HTTPS |
| `ADMIN_ACCOUNT_ACTIVATION_URL` | Production default: `https://admin.bingo.bakaroo.com/activate-account`; test uses its HTTP Admin URL |
| `ADMIN_BOOTSTRAP_ENABLED` | `false`; enable only for one-time initial Admin setup |
| `ADMIN_BOOTSTRAP_PHONE`, `ADMIN_BOOTSTRAP_NAME`, `ADMIN_BOOTSTRAP_EMAIL` | Initial Admin identity, only while bootstrap is enabled |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | SMTP provider; Gmail defaults are `smtp.gmail.com`, `587` |
| `VITE_API_BASE_URL`, `ADMIN_PUBLIC_URL` | API origin and Admin public origin; HTTP is allowed only in test |
| `PUBLIC_SITE_URL`, `PUBLIC_SITE_INDEXABLE` | Website origin and approved indexing state |
| `PUBLIC_ANDROID_AVAILABILITY`, `PUBLIC_ANDROID_URL`, `PUBLIC_IOS_AVAILABILITY`, `PUBLIC_IOS_URL` | Optional public app download state/URLs |
| `PUBLIC_SUPPORT_EMAIL`, `PUBLIC_SUPPORT_TELEGRAM_URL`, `PUBLIC_SUPPORT_WHATSAPP_URL` | Optional public contact channels |
| `APP_PAYMENT_PROVIDER_NAME`, `APP_PAYMENT_BUSINESS_ACCOUNT`, `APP_PAYMENT_INSTRUCTIONS` | Public payment instructions; defaults to Telebirr |

`ADMIN_EMAIL_BOOTSTRAP_ENABLED` defaults to `false` in test but `true` in
production when unset; the production workflow's default recipients are
`apps@bakaroo.com` and `equarhishe@gmail.com`. Set it explicitly to `false`
unless those accounts should be created. The separate
`ADMIN_BOOTSTRAP_ENABLED` path is disabled by default and cannot be enabled at
the same time. The full source/generation instructions for all variables,
including optional public-site and SMTP settings, are in
[`deploymentVariables.txt`](../deploymentVariables.txt).

The production branch uses schema `bingo` and port 8081; the test branch uses
schema `bingotest` and port 8091. Both use the hardened `prod` Spring profile,
loopback binding, Hibernate validation, and disabled development seeding. The
backend remains private behind Nginx. The workflow does not automatically
apply Terraform. Provision infrastructure through the reviewed instructions in
[`terraform/README.md`](./terraform/README.md) before enabling application
deployments.

## Release and verification

1. Protect `develop` and `release` with the required branch-policy and PR validation checks.
2. For a host using the retired OIDC deployment, stop its workflow and back
   up the database, runtime configuration, and artifacts. The current service
   uses `/opt/bingo`, not the old `/opt/bingo/backend/current` layout. Plan the
   JAR/service and static-root cutover together with Nginx; back up existing
   files first.
3. Merge an in-repository PR into `develop` to deploy to test. Verify the
   `bingotest` schema and API on port 8091 before promoting `develop` into
   `release`. The release-branch push starts the production workflow, which
   waits for approval through the protected `production` Environment before
   deploying. Deployments to each environment are serialized independently.
4. Verify website, Admin login, `/actuator/health`, CORS, authenticated
   WebSockets, email activation, encrypted receiving bank accounts, and
   persistence. Confirm Flyway and application health from sanitized logs.
5. Before a mobile store release, confirm the workflow's
   `mobile_api_base_url` input is the intended public HTTPS API origin; agent
   release builds must also receive
   `--dart-define=API_BASE_URL=https://api.bingo.bakaroo.com`.

The active application deployment path is `.github/workflows/deploy.yml`;
infrastructure provisioning remains a separate reviewed Terraform operation.
Mobile publishing is separate; see the [mobile release guide](mobile/README.md).

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
roots and preserve their individual backups. Production frontends require working HTTPS; test frontends use HTTP. Both
require a healthy API for verification.

To deploy after changing environment secrets/variables, or to redeploy without a
source change, use **Actions → Build and Deploy Bingo → Run workflow**,
select **develop** for test or **release** for production, and choose `all`,
`backend`, `frontend`, `admin`, or `website`. `frontend` selects both frontends.
Existing concurrency protection serializes deployments per environment.

The diff selects changes from the current merge, not from the last successful
deployment. Resolve and rerun failed deployments before subsequent releases;
if deployments were skipped or a previous deployment partly failed, manually deploy
`all` from the latest `develop` or `release` to reconcile all application artifacts.
Manually redeploying an older workflow run can deploy older code, so use the latest
branch for recovery. Nginx and certificate setup remain separate one-time host
operations for production; Terraform provisions the test host's HTTP-only
Nginx configuration.

For direct scripted frontend deployment, pass `admin`, `website`, or `all` to
`deployment/aws/deploy-frontend.sh`; omitting the argument retains the original
behavior of uploading both frontends.
