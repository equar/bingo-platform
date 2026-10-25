# Lightsail deployment setup checklist

This is configuration preparation only. No AWS resources were provisioned,
the deployment workflow was not run, and nothing was committed or pushed.
Do not run a production deployment until the decisions, prerequisites, and
values below are ready.

> **Separate existing workflows:** `deploy-admin.yml`, `deploy-backend.yml`,
> and `deploy-all.yml` are distinct manual SSH deployments with their own
> environments and long-lived SSH/runtime secrets. They are not used by the
> Lightsail workflow and their values are not required for this setup. Confirm
> their target before using them alongside Lightsail; do not point both paths
> at the same production service during a cutover.

## Architecture decisions to confirm

- [ ] Confirm one AWS region for the Lightsail instance, managed database,
      Secrets Manager secrets, and IAM role.
- [ ] Confirm the Lightsail instance bundle. Use at least 2 GiB RAM as the
      initial sizing assumption for Java 25, Nginx, and two static sites; load
      test and resize based on actual use.
- [ ] Confirm the Lightsail managed MySQL bundle. The template defaults the
      database engine blueprint to MySQL 8.0; verify that the selected
      blueprint and bundle are available in the chosen region.
- [ ] Confirm three distinct DNS hostnames: public website, admin, and API.
      All three must use HTTPS and point to the Lightsail static IPv4 address.
- [ ] Confirm `main` is the production branch and nominate GitHub Environment
      reviewers. The workflow is manual only and defaults to a dry run.
- [ ] Accept the single Lightsail instance as a single point of failure. The
      current WebSocket broker and game scheduler are process-local; do not
      add a second backend instance until distributed coordination/fan-out is
      implemented.
- [ ] Confirm database snapshot and instance snapshot cadence, restore owner,
      monitoring, and AWS Budget alerts. The template does not create alarms,
      budgets, log shipping, or automated instance snapshots.
- [ ] Confirm the backend email sender/provider. The current Spring
      configuration uses Gmail SMTP (`smtp.gmail.com`); production requires
      working SMTP credentials, such as a Gmail app password, or an application
      change for another SMTP provider.
- [ ] Confirm public Android/iOS links, support contacts, indexing policy, and
      the admin activation URL.

## AWS resources and bootstrap

1. Choose the AWS region and confirm the AWS account can create Lightsail,
   IAM, and Secrets Manager resources.
2. Create or verify the AWS IAM OIDC identity provider for
   `token.actions.githubusercontent.com`. Its audience must include
   `sts.amazonaws.com`. If it does not exist, create it using the official
   GitHub OIDC provider thumbprint/process for the account.
3. Create a GitHub Environment named exactly
   `bingo-lightsail-production`. Restrict deployments to `main`, require
   reviewer approval, and limit who can approve.
4. Create the CloudFormation stack from `deployment/aws/lightsail-stack.yaml`.
   Supply the IAM OIDC provider ARN, confirm the repository/environment
   parameters, choose the Lightsail instance and managed database bundles,
   and review the change set. The stack creates:
   - one Ubuntu 24.04 Lightsail instance;
   - one Lightsail managed MySQL database with public access disabled and
     backup retention enabled;
   - an attached static IPv4 address;
   - a generated database administrator secret;
   - a retained, initially unpopulated backend runtime secret; and
   - a GitHub OIDC role scoped to this instance, database, and two secrets.
5. Copy the stack outputs listed below to the GitHub Environment variables.
   Retrieve the generated database administrator secret in AWS Secrets
   Manager; do not put its value in GitHub.
6. Populate the backend runtime secret in Secrets Manager as a **plaintext
   `.env` file**, one `KEY=VALUE` per line. Use generated, alphanumeric
   `MYSQL_PASSWORD` (32–64 characters) and a `JWT_SECRET` of at least 32
   characters. Set both origin lists to the exact admin HTTPS origin.
7. Create DNS A records for the website/admin/API hostnames pointing to the
   stack’s static IP. Wait for DNS propagation before the first real deploy.
8. Add the GitHub Environment variables in the table below. The AWS role ARN
   and all AWS resource names/ARNs are **variables**, not secrets.
9. Dispatch `Deploy Bingo to Lightsail` from `main` with `dry_run=true` first.
   Confirm all tests/builds pass. After DNS, database secret, environment
   approvals, and production settings are confirmed, dispatch with
   `dry_run=false`. Let’s Encrypt issuance is performed during the first real
   deployment and requires all three hostnames to resolve to the instance.

## CloudFormation stack parameters

Supply these when creating `deployment/aws/lightsail-stack.yaml`. They are
CloudFormation inputs, not GitHub Actions values.

| Exact parameter name | Required? | What it is used for | Where to obtain it | Sensitive? | Stored in |
|---|---|---|---|---|---|
| `GitHubOidcProviderArn` | Required; no default | Existing GitHub Actions OIDC identity provider used by the role trust policy | AWS IAM → Identity providers; create the provider first if absent | No | CloudFormation stack parameter |
| `GitHubOwner` | Optional; default `equar` | Restricts OIDC trust to the repository owner | Repository URL/owner | No | CloudFormation stack parameter |
| `GitHubRepository` | Optional; default `bingo-platform` | Restricts OIDC trust to this repository | Repository URL/name | No | CloudFormation stack parameter |
| `GitHubEnvironment` | Optional; default `bingo-lightsail-production` | Restricts OIDC trust to the protected production environment | GitHub Environment name | No | CloudFormation stack parameter |
| `InstanceName` | Optional; default `bingo-production` | Names the Lightsail instance and related resources | Choose a unique name in the region | No | CloudFormation stack parameter |
| `InstanceBlueprintId` | Optional; default `ubuntu_24_04` | Selects the Lightsail OS blueprint required by the Java 25 bootstrap | Lightsail instance blueprints in the selected region | No | CloudFormation stack parameter |
| `InstanceBundleId` | Required; no default | Selects instance CPU, memory, storage, and transfer allowance | Lightsail instance bundles and current regional pricing | No | CloudFormation stack parameter |
| `DatabaseName` | Optional; default `bingo` | Names the application database/schema | Choose a valid MySQL database name | No | CloudFormation stack parameter |
| `DatabaseBlueprintId` | Optional; default `mysql_8_0` | Selects the managed MySQL engine version | Lightsail database blueprints in the selected region | No | CloudFormation stack parameter |
| `DatabaseBundleId` | Required; no default | Selects managed database capacity and storage | Lightsail database bundles and current regional pricing | No | CloudFormation stack parameter |
| `BackendRuntimeSecretName` | Optional; default `bingo/backend/production` | Names the Secrets Manager secret for the backend `.env` values | Keep the default or choose an unused Secrets Manager name | No | CloudFormation stack parameter |

## GitHub Actions variables

Create the GitHub Environment variables under
**Repository Settings → Environments → `bingo-lightsail-production`**.
All are non-secret configuration. The first four URL/build values are needed
even for dry runs; AWS identifiers and the TLS email are needed for a real
deployment. Database-related values are additionally required when
`component=all` or `component=backend`.

| Exact variable name | Required? | What it is used for | Where to obtain it | Sensitive? | GitHub environment |
|---|---|---|---|---|---|
| `BINGO_AWS_REGION` | Required for real deployment | AWS CLI and OIDC region | Choose the region used by the stack | No | `bingo-lightsail-production` |
| `BINGO_AWS_ROLE_ARN` | Required for real deployment | OIDC role to assume | CloudFormation output `GitHubActionsRoleArn` | No | `bingo-lightsail-production` |
| `BINGO_LIGHTSAIL_INSTANCE_NAME` | Required for real deployment | Selects target VM for SSH access and deployment | Output `LightsailInstanceName` | No | `bingo-lightsail-production` |
| `BINGO_LIGHTSAIL_DATABASE_RESOURCE_NAME` | Required for backend/all deployment | Lightsail API lookup for the managed database endpoint | Output `LightsailDatabaseName` | No | `bingo-lightsail-production` |
| `BINGO_DATABASE_NAME` | Required for backend/all deployment | MySQL schema name used by the backend | Output `DatabaseName` | No | `bingo-lightsail-production` |
| `BINGO_BACKEND_RUNTIME_SECRET_ARN` | Required for backend/all deployment | SecretString containing backend runtime `.env` settings | Output `BackendRuntimeSecretArn` | No; ARN only | `bingo-lightsail-production` |
| `BINGO_DATABASE_ADMIN_SECRET_ARN` | Required for backend/all deployment | Temporary database-user bootstrap credentials; not used by the application | Output `DatabaseMasterUserSecretArn` | No; ARN only | `bingo-lightsail-production` |
| `BINGO_PUBLIC_SITE_URL` | Required, including dry run | Website canonical URL and health check; HTTPS origin, e.g. `https://example.com` | Domain owner | No | `bingo-lightsail-production` |
| `BINGO_PUBLIC_SITE_INDEXABLE` | Required, including dry run | Website robots metadata; exactly `true` or `false` | Product/marketing decision | No | `bingo-lightsail-production` |
| `BINGO_ADMIN_URL` | Required, including dry run | Admin HTTPS origin, health check, CORS/WebSocket allowlist | Domain owner | No | `bingo-lightsail-production` |
| `BINGO_ADMIN_API_BASE_URL` | Required, including dry run | API origin embedded in the Vite admin build; HTTPS origin | Domain owner | No | `bingo-lightsail-production` |
| `BINGO_TLS_EMAIL` | Required for real deployment | Let’s Encrypt registration and expiry notices | Operations contact | No credential; may be personal contact info | `bingo-lightsail-production` |
| `BINGO_PUBLIC_ANDROID_AVAILABILITY` | Optional | Android store availability text | Product owner | No | `bingo-lightsail-production` |
| `BINGO_PUBLIC_ANDROID_URL` | Optional | Android store listing | Approved store listing | No | `bingo-lightsail-production` |
| `BINGO_PUBLIC_IOS_AVAILABILITY` | Optional | iOS store availability text | Product owner | No | `bingo-lightsail-production` |
| `BINGO_PUBLIC_IOS_URL` | Optional | iOS store listing | Approved store listing | No | `bingo-lightsail-production` |
| `BINGO_PUBLIC_SUPPORT_EMAIL` | Optional | Public support contact on website | Support owner | Public contact, not credential | `bingo-lightsail-production` |
| `BINGO_PUBLIC_SUPPORT_TELEGRAM_URL` | Optional | Public Telegram support link | Support owner | No | `bingo-lightsail-production` |
| `BINGO_PUBLIC_SUPPORT_WHATSAPP_URL` | Optional | Public WhatsApp support link | Support owner | No | `bingo-lightsail-production` |

All rows are stored as **GitHub Environment variables** in
`bingo-lightsail-production`. The workflow does not require GitHub Actions
secrets.

### Values the workflow obtains without GitHub variables

- Lightsail IP address, SSH username, temporary private key, and SSH host key
  come from `GetInstanceAccessDetails` for the named instance.
- The runner’s current IPv4 address comes from the GitHub runner at deployment
  time. The workflow opens TCP 22 to that address only, then closes it.
- The database endpoint is looked up using the Lightsail database resource
  name. The workflow appends the endpoint, port, and database name to the
  backend runtime file.
- The database administrator username/password is retrieved from the
  CloudFormation-generated Secrets Manager secret only for creating the
  restricted `MYSQL_USER` account. Those credentials are not used by the
  backend service.

Do **not** create GitHub variables/secrets for `AWS_ACCESS_KEY_ID`,
`AWS_SECRET_ACCESS_KEY`, a permanent SSH private key, or a fixed SSH host
address. OIDC and Lightsail temporary access replace those values.

## Backend runtime secret in AWS Secrets Manager

The CloudFormation stack creates `bingo/backend/production` by default and
outputs its ARN. Populate its SecretString as plain text in this format; do
not commit it or add it to GitHub. Do not include `MYSQL_HOST`, `MYSQL_PORT`,
or `MYSQL_DATABASE`—the workflow supplies those from Lightsail.

Required settings:

```dotenv
MYSQL_USER=<application database user, 1-16 letters/digits/underscores>
MYSQL_PASSWORD=<generated 32-64 character alphanumeric password>
JWT_SECRET=<random signing secret, at least 32 characters>
CORS_ALLOWED_ORIGINS=https://<admin-hostname>
WEBSOCKET_ALLOWED_ORIGINS=https://<admin-hostname>
ADMIN_ACCOUNT_ACTIVATION_URL=https://<admin-hostname>/<activation-route>
SPRING_MAIL_USERNAME=<production SMTP username>
SPRING_MAIL_PASSWORD=<production SMTP password or app password>
```

Optional application settings may be added to the same env file, for example
`BINGO_FLOAT_BANK_ENCRYPTION_KEY` when encrypted bank-account storage is used,
`APP_BUSINESS_TIMEZONE`, `APP_WALLET_CURRENCY`, or product payment
instructions. Production database credentials are validated before restart.
The workflow creates the MySQL account and grants it privileges only on the
configured Bingo database. Keep its username/password stable: rotating the
application DB password requires an intentional coordinated database and
runtime-secret update.

The separate `DatabaseMasterUserSecretArn` output identifies a generated JSON
secret with `username` and `password` fields. Keep that secret in Secrets
Manager. Its password is not a GitHub value and is never passed to the
backend service.

## IAM, permissions, and approvals

The CloudFormation trust policy is limited to:

```text
repo:equar/bingo-platform:environment:bingo-lightsail-production
```

The deployment role has only these action families:

- `lightsail:GetInstance`, `GetInstanceAccessDetails`,
  `OpenInstancePublicPorts`, and `CloseInstancePublicPorts` for the named
  Lightsail instance;
- `lightsail:GetRelationalDatabase` for the named managed database; and
- `secretsmanager:GetSecretValue` for the backend runtime and database admin
  secrets.

The role cannot create or delete AWS infrastructure. The GitHub workflow
requires `contents: read` and `id-token: write`. GitHub branch restrictions,
reviewers, and approver restrictions are configured in repository settings,
not CloudFormation.

## External prerequisites and cost assumptions

- Control of DNS for all three HTTPS hostnames; point each A record to the
  stack output `LightsailStaticIpAddress`. Do not add AAAA records unless
  IPv6 is separately configured and tested.
- TCP 80 and 443 reachable for website traffic and ACME HTTP-01 certificate
  issuance/renewal. TCP 22 is not permanently open.
- A real SMTP account/password and an agreed public support contact.
- A production domain/registrar charge, if the domain is not already owned.
- Monthly AWS charges depend on selected Lightsail instance/database bundles,
  transfer overages, backups/storage, and Secrets Manager. No ALB, NAT
  gateway, ECR, S3, or CloudFront is part of this design. Instance and
  database bundles are required CloudFormation parameters so the operator
  chooses actual region pricing rather than relying on a guessed price. As a
  current US-region reference, a 2-GiB Linux instance ($12/month) plus a
  standard 2-GiB managed database ($30/month) is about **$42/month**, before
  Secrets Manager, domain registration, snapshots, taxes, or transfer
  overages. Confirm actual regional charges on the
  [AWS Lightsail pricing page](https://aws.amazon.com/lightsail/pricing/).
- Before launch, estimate the selected region/bundles in the AWS Pricing
  Calculator and configure an AWS Budget alert.

## Validation and deployment status

**Verified locally:** `cfn-lint` passed; GitHub workflow YAML and embedded
Bash syntax passed; the Lightsail bootstrap and remote deployment scripts
passed `bash -n`; admin lint passed and all 84 admin tests passed; the admin
TypeScript/build checks passed; the website check reported no errors,
warnings, or hints; website link validation and build passed; and the
production canonical/robots metadata check passed with example HTTPS origins.
`git diff --check` found no whitespace errors (it reported only line-ending
normalization warnings for unrelated pre-existing files).

**Not verified locally:** `mvn -B verify` could not complete because this
shell did not have `BINGO_TEST_MYSQL_URL` configured. The deployment workflow
defines a disposable MySQL 8 service and its test credentials, but that
workflow was not run. The Nginx `nginx -t` check is included in the
infrastructure-validation workflow but has not run here; the local Docker
daemon is unavailable.

No AWS resources were provisioned, no GitHub workflow was run, and no
deployment, commit, or push occurred. “Configuration prepared” is not
“successfully deployed.” A first deployment remains blocked on the operator
creating the stack, populating the runtime secret, protecting the GitHub
Environment, configuring DNS, and supplying the required values above.
