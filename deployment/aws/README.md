# AWS Lightsail deployment

## Proposed architecture

- One Lightsail Ubuntu 24.04 instance hosts Nginx, the Java 25 Spring Boot
  backend, and the built Astro website and React/Vite admin SPA.
- A separate Lightsail managed MySQL 8 database stores persistent application
  data. It is not a database container and is configured as not publicly
  accessible.
- A Lightsail static IP provides a stable IPv4 target for DNS. The public
  website, admin, and API use separate hostnames; Nginx routes each hostname
  and proxies API/WebSocket requests to `127.0.0.1:8081`.
- The instance firewall persistently exposes only TCP 80 and 443. The manual
  deploy workflow temporarily opens TCP 22 to the GitHub runner’s public IPv4
  `/32`, uses temporary SSH access details from Lightsail, then closes the
  rule. The IAM role’s Lightsail actions are restricted to the instance.
- HTTPS is issued and renewed by Let’s Encrypt on the instance. The first real
  deploy requires all three DNS A records to resolve to the static IP.
- The managed database has backups enabled. The CloudFormation resource and
  credentials are retained if the stack is deleted; delete them intentionally
  only after backups and data recovery are addressed.

This keeps infrastructure and ongoing operations simpler than ECS, ALB, VPC,
NAT, ECR, and RDS. It also creates a single-instance failure domain. The app’s
WebSocket broker and draw scheduler are process-local, so horizontal scaling
is not safe until those components are made distributed.

Alternatives that materially change cost:

- A larger Lightsail instance/database bundle increases the fixed monthly
  charge; choose sizes from the current Lightsail pricing page for the region.
- S3/CloudFront for static assets can reduce static traffic and improve
  geographic delivery, but adds services and configuration beyond the
  requested Lightsail architecture.
- A second application instance or a more highly available database raises
  cost and requires application changes for shared scheduling/WebSocket
  state. A NAT gateway, ALB, and ECS are not part of this design.
- Verify a region- and size-specific estimate in the AWS Pricing Calculator.
  Main cost drivers are the always-on instance bundle, managed database
  bundle/storage/backups, transfer overages, Secrets Manager secrets, and
  any DNS/domain registration. This setup has no ALB, NAT gateway, ECR,
  CloudFront, or S3 application-hosting charges.

## Infrastructure and app behavior

`lightsail-stack.yaml` creates the instance, static IP, managed database,
generated database administrator secret, empty backend runtime secret, and
GitHub OIDC deployment role. Review the proposed instance/database bundles
and blueprint for the selected region before creating the stack. The template
does not create a custom domain or DNS records.

The instance bootstrap installs Nginx, Certbot, MySQL client tools, and
Temurin Java 25; installs the AWS RDS global CA bundle in the Java truststore
for Java and keeps it available for identity-verified MySQL client
connections; creates the `bingo` service account; and configures the systemd
backend unit. Nginx serves the three hostnames and proxies `/ws` as a
WebSocket. Only the backend’s health endpoint is exposed under `/actuator`;
other actuator endpoints return 404.

The app deployment workflow is
`.github/workflows/deploy-lightsail.yml`. It is manual, restricted to `main`,
protected by GitHub Environment approvals, serialized, and defaults to
build/test-only. It does not provision resources or create/update the
CloudFormation stack. Mobile app/store publishing remains separate.

Backend `application-prod.yml` requires the Lightsail database hostname,
application database account, JWT secret, admin activation URL, CORS and
WebSocket origins, and mail credentials. The deploy workflow gets the
database endpoint from Lightsail, creates the app database account with
database-scoped privileges, and adds the database connection settings to the
runtime env file. The app never receives the database administrator
credentials.

Flyway runs at application startup. Maintain backward-compatible
expand/contract migrations. Backend releases, website releases, and admin
releases are versioned and switched through symlinks. Failed host-side health
checks restore previous links and Nginx configuration. That rollback does
not reverse schema/data migrations; take and test database snapshots before
risky schema changes and document a restore owner/procedure.

## Security, HTTPS, and operations

- The GitHub OIDC provider must exist in the AWS account. The generated role
  trusts only `repo:equar/bingo-platform:environment:bingo-lightsail-production`.
- The role can retrieve temporary SSH details and manage the SSH rule for the
  named instance; retrieve the backend runtime secret and generated database
  admin secret; and read the endpoint for the named managed database. It has
  no permission to create resources or deploy arbitrary AWS infrastructure.
- The database is not publicly accessible. The generated database
  administrator credential is used only to create the restricted backend
  database user. Keep the `MYSQL_USER` and `MYSQL_PASSWORD` runtime settings
  stable; rotating the app DB password requires a coordinated database and
  runtime-secret update.
- The backend runtime secret is a plaintext `.env`-format SecretString in
  AWS Secrets Manager, not JSON. Its name is `bingo/backend/production` by
  default; add the values listed in `SETUP_CHECKLIST.md` before deploying the
  backend. Never put these values in GitHub Actions.
- Set `CORS_ALLOWED_ORIGINS` and `WEBSOCKET_ALLOWED_ORIGINS` to include the
  exact admin HTTPS origin. The workflow checks this before restarting the
  service.
- Let’s Encrypt requires public DNS records and inbound TCP 80 during
  certificate issuance and renewal. Certbot renewal reloads Nginx. `BINGO_TLS_EMAIL`
  is used for certificate-expiry notices.
- The templates do not create CloudWatch alarms, AWS Budgets, automated
  instance snapshots, or application log shipping. Define monitoring,
  snapshot cadence, alert ownership, and recovery testing before production.

## Cost and availability

The instance and managed database each incur a fixed monthly charge based on
the selected bundles. The selected instance should have at least 2 GiB RAM as
a starting point for Java 25, Nginx, and the static sites; load-test and
resize based on actual memory/CPU and traffic. The database bundle should be
selected based on data size, connections, and backup needs. Both sizes are
required CloudFormation parameters rather than invented defaults.

As a current US-region reference, AWS’s pricing page lists a 2-GiB Linux
instance at **$12/month** and a standard 2-GiB managed database at
**$30/month** (encrypted, 80-GiB storage, 100-GiB transfer allowance): about
**$42/month** before Secrets Manager, domain registration, snapshots, taxes,
or transfer overages. Prices and transfer allowances vary by region. Lightsail
instance bundles include transfer (3 TB/month for the cited instance plan);
only outbound transfer above the plan allowance is billed as overage.
Secrets Manager charges per secret/API use. Verify the selected region and
plans on the [AWS Lightsail pricing page](https://aws.amazon.com/lightsail/pricing/)
and in the AWS Pricing Calculator before creating resources. This is a
low-complexity baseline, not a highly available production topology.

## Validation

From the repository root:

```bash
cfn-lint deployment/aws/lightsail-stack.yaml
```

The infrastructure validation workflow runs this lint check on relevant
pull requests and manual dispatch. It does not use AWS credentials or
provision resources. See [`SETUP_CHECKLIST.md`](SETUP_CHECKLIST.md) for the
exact GitHub variables, secret formats, IAM bootstrap, and ordered setup.
