# Bingo GitHub Actions

## Workflow roles

- `branch-flow-guard.yml` enforces allowed pull-request flow.
- `pr-validation-pipeline.yml` runs backend verification against an isolated
  MySQL service, admin lint/tests/typecheck/build, and website validation/build.
- `validate-lightsail-infrastructure.yml` runs `cfn-lint` against
  `deployment/aws/lightsail-stack.yaml`; it never assumes an AWS role.
- `deploy-lightsail.yml` is the manual production deployment workflow for the
  backend, admin, and website. It requires `main`, uses the protected
  `bingo-lightsail-production` environment, serializes deployments, and has
  `dry_run=true` by default.
- Flutter store-publishing workflows remain separate.

## AWS deployment behavior

Every manual dispatch runs all application tests and builds. The `component`
input selects `all`, `backend`, `admin`, or `website` for upload. A dry run
does not assume AWS credentials, retrieve Secrets Manager values, or connect
to Lightsail.

A real deployment uses the environment’s OIDC role. The workflow retrieves
temporary SSH access details from Lightsail, opens TCP 22 only to the current
GitHub runner IPv4 address, and closes that rule on completion. The instance
firewall template exposes only HTTP and HTTPS persistently. The workflow
verifies the public HTTPS endpoints after the host-side deployment checks.

The backend runtime env file is retrieved from AWS Secrets Manager. The
workflow also retrieves the generated Lightsail database administrator
credential to create a separate MySQL user scoped to the Bingo database. The
admin password is used only for this bootstrap operation, not by the backend.
GitHub stores only secret ARNs as variables; it does not store the secret
values, AWS access keys, or a permanent SSH key.

Flyway runs at backend startup. Backend and static site deployments use
versioned releases and symlinks. Host-side health failures trigger a rollback
to the previous release. Database schema migrations are not automatically
reversed; snapshot and migration guidance is in
[`aws/README.md`](aws/README.md).

## GitHub controls and values

Create a GitHub Environment named `bingo-lightsail-production`. Restrict it
to the `main` branch and require reviewer approval. The OIDC role trust
subject is restricted to this repository and environment. Give the workflow
only `contents: read` and `id-token: write`; do not add AWS access-key or
private-key secrets.

The complete variable table and ordered AWS bootstrap steps are in
[`aws/SETUP_CHECKLIST.md`](aws/SETUP_CHECKLIST.md). Configure those values as
**environment variables** in `bingo-lightsail-production`. Keep backend
runtime configuration in the AWS Secrets Manager secret named by the stack
output. Do not copy values from the BillPay reference repository; its
application credentials are unrelated. Its workflow structure was reviewed
as a reference; no credential values or application-specific secret values
were copied.

## Verification and rollback

For the first production deployment, confirm the DNS records and HTTPS
certificate prerequisites before dispatching with `dry_run=false`. After a
deployment, verify the public site, admin SPA, API health endpoint, REST CORS,
WebSocket connectivity, account email delivery, database persistence, and
admin activation flow.

The deployment script restores the prior application links/configuration if
its Lightsail-side checks fail. If a later external check fails, dispatch the
known-good `main` commit again. For a failed schema/data release, use the
approved Lightsail database snapshot recovery procedure; application rollback
does not revert database changes.
