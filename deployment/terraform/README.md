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
  distinct DNS names in the same DNS zone. The examples are placeholders for
  names you control; all three should resolve to the test Elastic IP.
- `route53_zone_id`: optional public Route 53 zone ID. When set, Terraform
  creates A records for the three names; otherwise point all three A records
  to Terraform's `web_static_ip` output yourself (for example, in GoDaddy).

The test host serves HTTP directly; no DNS propagation or certificate
provisioning is required before deployment. HTTP exposes credentials, session
tokens, and game traffic in plaintext. Use test-only accounts and data, and do
not use the HTTP test environment over an untrusted network. The backend remains
unreachable directly: port 8091 is not opened in the EC2 security group.

## Safety and state

Terraform workspaces isolate `prod` and `test`. Configure a remote encrypted
backend before team use; local state is intentionally ignored and must never be
committed. Copy `env/prod.tfvars.example` or `env/test.tfvars.example` to its
matching ignored `.tfvars` file. For production, do not put the database
administrator password in a `.tfvars` file; supply it as
`TF_VAR_database_admin_password`. Test application credentials are configured
in the test GitHub Environment and are not Terraform inputs.

The stack defaults to `us-east-2` and selects the first available availability
zone in that region. Set `aws_region` or `availability_zone` in an environment
file only when an explicit placement is required.

## Commands

```bash
cd deployment/terraform
./provision.sh test plan
./provision.sh test apply
./provision.sh prod plan
./provision.sh prod apply
```

`apply` and `destroy` require a typed environment-specific confirmation. When
`route53_zone_id` is set, Terraform creates the test DNS records; otherwise
configure the three records in your DNS provider after apply. Backend runtime
credentials, GitHub deployment credentials, and application releases remain
explicit follow-up steps. Terraform never stores application or SSH private
keys in source control.

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
