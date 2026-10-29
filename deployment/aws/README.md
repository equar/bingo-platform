# Bingo AWS deployment

Bingo follows the BillPay deployment model: GitHub builds the applications after
an in-repository feature-branch pull request is merged into `main`, then uploads
them over SSH to an existing Ubuntu Lightsail/EC2 server. No OIDC role or AWS
API credentials are used by the application deployment workflow.

| Application | Server destination |
|---|---|
| Java 25 backend | `/opt/bingo/backend.jar`, service `bingo-backend` |
| Backend runtime configuration | `/opt/bingo/.env`, root-owned mode 600 |
| React admin | `/var/www/bingo-admin` |
| Astro website | `/var/www/bingo-website` |
| MySQL schema | `bingo` (fixed) |
| API listener | `127.0.0.1:8088` |

The sole application deployment workflow is `.github/workflows/deploy.yml`.
It checks out the merged commit, packages the backend with Maven (tests are
skipped like BillPay), builds both frontends, configures the SSH key, deploys
the backend, and uploads static files. The PR validation pipeline remains the
test gate; require it in branch protection for `main`.
Feature branches merge directly into `main`, with no source branch naming
restriction. Every such merge deploys; no intermediate branches are required.

See [SETUP_CHECKLIST.md](SETUP_CHECKLIST.md) for server, DNS, database, GitHub,
and HTTPS setup. All imported settings are **repository** secrets/variables,
not values in the old `bingo-lightsail-production` environment.

Unlike BillPay's application-specific configuration, Bingo uses Flyway,
Hibernate `validate`, SMTP email, WebSockets, and `JWT_ACCESS_TOKEN_TTL_SECONDS`.
It has no `/billpay` context path, Graph mail integration, or BillPay admin
bootstrap. Initialize the first production admin through a controlled,
application-compatible provisioning procedure; never enable development seed
accounts in production.

Backend deploys retain the previous JAR/env pair and attempt restoration on a
failed restart/health check. Static deployments retain `.backup` directories.
Health failures fail the workflow. This is a sequential deployment with a brief
backend restart and no automatic rollback of database migrations. Take a DB
snapshot before risky Flyway releases. Recover static files from their backup
if a public frontend check fails; rerun deployment by rerunning the successful
merge event's workflow after correcting configuration or merging a fix.

The old OIDC CloudFormation template `lightsail-stack.yaml` and files under
`deployment/lightsail/` remain reference-only infrastructure. Their generated
systemd paths, secret storage, SSH firewall rules, and deployment role belong
to the retired OIDC path. Do not deploy that stack unchanged for this SSH
workflow or run its old release script against this service. Existing hosts
using `/opt/bingo/backend/current` need a coordinated cutover to `/opt/bingo`;
back up existing application/env files first. The old manual deployment
workflows were removed to prevent competing production deployments.

The SSH workflow requires reachable port 22 from its runner, a configured
private key, and passwordless sudo for the deployment account, just as BillPay
does. `ssh-keyscan` establishes host trust on first connection; verify the
server fingerprint out of band during setup. Keep the backend single-instance
because its scheduler and WebSocket state are process-local. Configure instance
and database backups, log retention, monitoring, and recovery ownership.

The protected `/opt/bingo/.env` is read by systemd, which passes configuration
through the process environment. `BINGO_ENV_IMPORT` is deliberately empty
in this service/runtime configuration so Spring does not attempt a second,
unprivileged read of the root-only file. Local development `.env` imports
remain available. Startup failures print bounded, secret-redacted journal
entries before rollback.

Database TLS trust is prepared automatically before switching the backend.
`setup-rds-truststore.sh` downloads the official AWS CA bundle over HTTPS,
validates/imports root certificates, and preserves the Java default roots in
`/etc/bingo/database-truststore.jks`. The Bingo systemd unit selects this
truststore without changing other applications or disabling JDBC certificate
and hostname verification. Its `changeit` password protects a public CA store,
not an application credential. Failed downloads/imports leave the installed
truststore and running application untouched.
