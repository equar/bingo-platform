# Bingo deployment

## Deployable applications

| Application | Framework / runtime | Development port | Build | Lightsail target |
|---|---|---:|---|---|
| `bingo-website` | Astro 5 / Node 22 | 4321 | `npm ci`, `npm run check`, `npm run validate:links`, `npm run build`; `dist/` | Nginx static site on the Lightsail instance |
| `bingo-admin` | React 18 / TypeScript / Vite 6 / Node 22 | 5173 | `npm ci`, lint, tests, TypeScript check, `npm run build`; `dist/`; build-time `VITE_API_BASE_URL` | Nginx static SPA on the Lightsail instance |
| `bingo-backend` | Spring Boot 4.1 / Java 25 / Maven / MySQL 8 / Flyway | 8080 locally; 8081 on the production host | `mvn -B verify`; deploy the packaged JAR | systemd service on the Lightsail instance |
| `bingo-app`, `bingo-agent` | Flutter mobile apps | Native/web-specific | Existing mobile CI/store workflows | Not part of AWS website/backend deployment |

## Lightsail architecture

The reviewed configuration is in [`aws/README.md`](aws/README.md), with the
operator’s detailed setup steps and GitHub values in
[`aws/SETUP_CHECKLIST.md`](aws/SETUP_CHECKLIST.md).

- One Ubuntu 24.04 Lightsail instance runs Nginx, the Java 25 backend under
  systemd, and the static public website and admin SPA.
- One separate Lightsail managed MySQL database stores persistent application
  state. No database runs in an application container or on instance storage.
- A Lightsail static IPv4 address is attached to the instance. The public site,
  admin, and API use three distinct HTTPS hostnames routed to that address.
- Nginx terminates HTTPS using a Let’s Encrypt certificate and proxies API and
  WebSocket traffic to the backend on loopback port 8081. Only ports 80 and 443
  are open persistently. SSH is opened to the GitHub runner’s IPv4 address only
  during a deployment, using temporary Lightsail access details, then closed.
- Backend data is in MySQL. No required local uploads or persistent application
  files were found. Backend logs go to the system journal.

This is a cost-conscious, **single-instance** architecture and a single point
of failure. The current WebSocket broker and game draw scheduler are
process-local, so the backend must remain at one instance until those features
use a distributed design. Instance snapshots and database backups do not make
the application highly available.

## Workflows and safety gates

- `.github/workflows/pr-validation-pipeline.yml` is the CI path. It runs
  backend verification against MySQL, admin lint/tests/typecheck/build, and
  website checks/build.
- `.github/workflows/validate-lightsail-infrastructure.yml` lints CloudFormation,
  checks shell syntax, and tests the rendered Nginx configuration. It does not
  assume AWS credentials or create resources.
- `.github/workflows/deploy-lightsail.yml` is the only AWS application
  deployment workflow. It is manual (`workflow_dispatch`), restricted to
  `main`, serialized, uses the protected `bingo-lightsail-production`
  environment, and defaults to `dry_run=true`. Every dispatch runs the
  application checks and builds. Choose `all`, `backend`, `admin`, or
  `website` for a real deployment.
- The workflow uses GitHub OIDC, not long-lived AWS access keys. The IAM role
  retrieves Lightsail’s temporary SSH key, opens SSH only to the runner’s
  current public IPv4 CIDR, deploys, and closes the rule even after a failed
  deploy. SSH keys and backend/database passwords are not GitHub secrets.
- CloudFormation is never run by an application workflow. Operators review
  and create the Lightsail stack manually. No resources are provisioned by
  these checked-in workflows.
- Flutter builds and store publication stay separate.

The exact variable names, where they come from, and the backend secret format
are in the setup checklist. Only public URLs and website content are
build-time configuration. Database credentials, JWT signing material, and
SMTP credentials belong in AWS Secrets Manager.

### Existing SSH deployment workflows

The repository also has separate manual SSH workflows in
`.github/workflows/deploy-admin.yml`, `.github/workflows/deploy-backend.yml`,
and `.github/workflows/deploy-all.yml`. They use a configured remote server and
long-lived SSH-key/environment-file secrets; they are not part of the
Lightsail/OIDC setup or its checklist. Keep them separate from the Lightsail
path unless deliberately migrating them. Before using either path for the same
production service, confirm the target and coordinate the cutover to avoid
overlapping deployments.

## Migrations, rollback, and recovery

Flyway migrations run when the backend starts. Use backward-compatible
expand/contract migrations. The deployment script keeps versioned releases
and switches symlinks; if host-side health checks fail, it restores the
previous application links and Nginx configuration. This does not reverse a
database migration or application data changes.

Before a release with a risky schema change, take and verify a managed
database snapshot. For recovery, restore the database to a new Lightsail
database and perform the operator-approved data recovery procedure. Keep
instance snapshots as a separate recovery measure. A prior application
release can be redeployed by selecting the known-good commit on `main`; do not
expect the workflow to roll back Flyway changes.

## Existing operator-managed SSH scripts

The legacy `deployment/backend/`, `deployment/admin/`, and `deployment/deploy-all.sh`
scripts and their `deploy-backend.yml`, `deploy-admin.yml`, and `deploy-all.yml`
workflows target an operator-managed Linux server. They are not the AWS
Lightsail workflow and do not provision its database, static IP, or IAM role.
Do not use them for Lightsail releases.
