# AWS Terraform environments

This Terraform stack provisions isolated production and test environments
compatible with the repository deployment scripts:

- Production: Ubuntu 24.04 Lightsail host with static IP, private Lightsail
  MySQL 8 database with backups, and a Secrets Manager runtime placeholder.
- Test: Ubuntu 24.04 EC2 `t3.micro` by default, with a 20 GiB encrypted root
  volume, local MySQL restricted to loopback, and an EC2 security group that
  exposes HTTP plus SSH from the configured CIDRs.
- Both hosts install Java 25 JRE, Node.js 22, Nginx, and required database
  tools at first boot. Production retains Certbot for HTTPS; the test host
  configures HTTP-only website, Admin, and API virtual hosts.

The test backend uses the local `bingotest` schema, binds only to
`127.0.0.1:8091`, and is reached through the HTTP API domain. The test
deployment creates a least-scope MySQL user on first backend deployment.
Production uses the `bingo` schema, its managed database, and port 8081.
Terraform does not store the test database password.

## Test EC2 and free-tier limits

The test host defaults to `t3.micro` with standard CPU credits (no unlimited
credit charges) and 20 GiB of encrypted `gp3` storage. AWS free-tier eligibility
depends on the account, region, account age, and current AWS offer; Terraform
cannot guarantee that EC2, public IPv4, storage, or data transfer will be free.
Review the AWS billing page before applying. Terraform allocates and attaches
an Elastic IP to the test host so its GoDaddy DNS records and deployment
settings remain stable. Elastic IP/public IPv4 charges may apply.
The test instance and its local database are disposable: changing bootstrap
user data replaces the instance, and destroying the test stack deletes its
database. The first test apply also removes the old test Lightsail instance
and managed database from this workspace; review the plan and back up any
existing test data before confirming it.

Configure these values in `env/test.tfvars`:

- `ssh_public_key`: public half of the private key stored in the `test`
  GitHub Environment as `DEPLOY_SSH_KEY_B64`.
- `ssh_allowed_cidrs`: restrict port 22 to the GitHub runner egress ranges or
  your self-hosted runner/network. Do not use `0.0.0.0/0`.
- `test_site_domain`, `test_admin_domain`, and `test_api_domain`: three
  distinct DNS names in the same DNS zone, outside `bingo.bakaroo.com`'s
  `includeSubDomains` HSTS scope. The examples use names under `bakaroo.com`.
  Update any existing `env/test.tfvars` that still uses `*.bingo.bakaroo.com`;
  Terraform rejects those HSTS-covered names. All three resolve to the test
  Elastic IP.
- `test_api_domain` is fixed to `api.test.bakaroo.com` because the mobile
  release HTTP exception is explicitly restricted to that host.
- `route53_zone_id`: optional public Route 53 zone ID. When set, Terraform
  creates A records for the three names. Leave it empty to have Terraform
  create or update the three GoDaddy A records automatically after associating
  the test Elastic IP.

The full input list, defaults, and sources for test and production values are
in the [deployment variable inventory](../../deploymentVariables.txt#terraform-infrastructure-inputs).

For automatic GoDaddy DNS updates, create a GoDaddy personal access token from
the [GoDaddy PAT guide](https://developer.godaddy.com/en/docs/api-users/auth/how-to)
with the `domains.domain:read` and `domains.dns:update` scopes. GoDaddy must
host the authoritative DNS zone for `bakaroo.com`. Record the token's expiry
and rotate it before then. Keep the token in the environment of the machine
running Terraform; it is not a Terraform variable and is not saved in Terraform
state or plan files. The `provision.sh` wrapper requires
`GODADDY_PAT` for test applies that use GoDaddy. Python 3 and outbound HTTPS
access to GoDaddy's API are also required. Terraform plans and destroys do not
call the API.

In macOS `zsh`, enter the token without putting it in shell history, then run
the apply:

```zsh
read -s 'GODADDY_PAT?GoDaddy PAT: '
export GODADDY_PAT
./provision.sh test apply
unset GODADDY_PAT
```

The updater sets a 600-second TTL and rejects conflicting CNAME/AAAA records.
On each test apply, Terraform creates or updates the three GoDaddy A records to
the current Elastic IP. If the IP or a DNS name changes, it syncs the records
to the new configuration without deleting records during replacement or
destroy. If `route53_zone_id` is set instead, Terraform uses Route 53 and does
not call GoDaddy.

The test host serves HTTP directly; no certificate provisioning is required
before deployment. The test names must remain outside the production HSTS
scope or browsers may upgrade HTTP requests to HTTPS. HTTP exposes credentials, session
tokens, and game traffic in plaintext. Use test-only accounts and data, and do
not use the HTTP test environment over an untrusted network. The backend remains
unreachable directly: port 8091 is not opened in the EC2 security group.

## Safety and state

Terraform workspaces isolate `prod` and `test`. Terraform currently has no
configured remote backend; local state is intentionally ignored and must never
be committed. Before team use, approve and provision the encrypted remote state
design and migrate local state using the documented freeze, backup, and
`terraform init -migrate-state` procedure in
[`docs/infrastructure/production-database-and-terraform-state-decision.md`](../../docs/infrastructure/production-database-and-terraform-state-decision.md).
Do not initialize a remote backend until its bucket, KMS policy, and lockfile
permissions have been provisioned and reviewed. Copy `env/prod.tfvars.example`
or `env/test.tfvars.example` to its matching ignored `.tfvars` file. For
production, do not put the database administrator password in a `.tfvars` file;
supply it as `TF_VAR_database_admin_password`. Test application credentials are
configured in the test GitHub Environment and are not Terraform inputs.

The checked-in production Terraform root provisions a Lightsail database,
while the deployment inventory/runtime path references RDS. This discrepancy
is unresolved; do not apply the production root or change the runtime database
until the architecture decision and Terraform state ownership are reconciled.

The stack defaults to `us-east-2` and selects the first available availability
zone in that region. Set `aws_region` or `availability_zone` in an environment
file only when an explicit placement is required.

## Commands

From the repository root, confirm AWS identity and create local tfvars from
the examples. Replace the sample public key, SSH CIDR, and production bundle
values before planning:

```bash
aws sts get-caller-identity
cd deployment/terraform
cp env/test.tfvars.example env/test.tfvars
cp env/prod.tfvars.example env/prod.tfvars
./provision.sh test plan
./provision.sh prod plan
```

Review the full plans before applying. Test apply requires `GODADDY_PAT` only
when GoDaddy is selected. Production plan/apply requires
`TF_VAR_database_admin_password` in the same shell; use the secure prompt
commands in the [variable inventory](../../deploymentVariables.txt#handy-setup-and-deployment-commands).

```bash
./provision.sh test apply
./provision.sh prod apply
```

`apply` and `destroy` require a typed environment-specific confirmation.
Terraform manages test DNS through Route 53 when `route53_zone_id` is set, or
through GoDaddy when it is empty. Backend runtime credentials, GitHub
deployment credentials, and application releases remain explicit follow-up
steps. Terraform never stores application or SSH private keys in source
control.

After apply, retrieve values for GitHub configuration from the matching
workspace:

```bash
terraform workspace select test
terraform output -raw web_static_ip
terraform output -raw test_ssh_user
terraform output -raw test_api_base_url
terraform output -raw test_admin_url
terraform output -raw test_site_url

terraform workspace select prod
terraform output -raw web_static_ip
terraform output -raw database_endpoint
```

## Existing AWS MySQL test schema

[`test-database/`](./test-database/) provisions the `bingotest` schema in an
**existing** MySQL server; it does not create or alter the production schema.
It accepts only `environment = "test"` and requires a typed confirmation for
apply or destroy.

```bash
cd deployment/terraform/test-database
cp test.tfvars.example test.tfvars
export TF_VAR_mysql_admin_password='...'
./provision.sh plan
./provision.sh apply
```

The Terraform runner must be network-authorized to reach the existing MySQL
endpoint over TLS. Use a database administrator limited to test-schema
provisioning; do not use the production application account.

The separate [`test-database/`](./test-database/) root remains available for
provisioning a test schema on an existing MySQL server. The EC2 test stack does
not use that root; it owns a local test-only MySQL instance.
