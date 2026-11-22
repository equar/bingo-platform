# Production database and Terraform state decision

**Review date:** 2026-11-22
**Scope:** repository configuration only. No AWS account, production host, database, or Terraform state was queried.

## What the checkout establishes

- `deploymentVariables.txt` identifies the production `MYSQL_HOST` setting as an RDS endpoint. The deployment workflow reads `MYSQL_HOST` from the protected GitHub Environment variable; that value is write-only/unavailable from this checkout, so the actual configured endpoint and running connection were not verified.
- `deployment/terraform/main.tf` independently declares a production Lightsail MySQL 8 database (`bingo-prod-mysql`), with bundle `medium_2_0` in the example, `publicly_accessible = false`, and Lightsail automatic backup retention enabled. Its `database_endpoint` output points at that Lightsail database.
- The production backend deployment requires `MYSQL_HOST` and uses `VERIFY_IDENTITY`. `setup-rds-truststore.sh` downloads and installs the AWS RDS CA bundle. This supports the repository's RDS runtime intent, but is not evidence that the live endpoint is RDS or that a successful TLS connection currently occurs.
- The production Lightsail DB declaration is not connected to the deployment workflow's runtime endpoint. Applying the current production Terraform configuration could therefore create or manage a second database. Do not apply it until the resource ownership and selected architecture are reconciled.
- The two paths both name the application schema `bingo`; a matching schema name does not mean data, users, grants, migration history, or backups are shared.

## Decision matrix

| Area | Referenced RDS MySQL | Terraform Lightsail MySQL | Review and decision impact |
|---|---|---|---|
| Security | RDS supports VPC/subnet and security-group isolation, TLS identity verification, and optional KMS encryption at rest. The deployed instance's encryption, public-access, SG, and TLS settings are not visible here. | The Terraform declaration sets `publicly_accessible = false`; Lightsail states that it encrypts managed DB data at rest and in transit. The database is paired simply with Lightsail resources in the account. | RDS offers stronger network and key-policy controls. Require proof of encryption, private access, and TLS on either target before production approval. |
| Backups | RDS supports configurable automated-backup retention from 1 to 35 days and point-in-time recovery. The actual instance retention and snapshot policy are unverified. | Terraform enables Lightsail backup retention. Lightsail automatic database backups retain the last 7 days; longer retention requires manual snapshots and an operational retention process. | RDS offers a broader configurable recovery window. Set and verify a business-approved RPO/RTO, retention, and restore rehearsal for the selected target. |
| Recovery | RDS PITR restores to a new instance; snapshots and retained automated backups can support recovery after deletion when configured. | Lightsail PITR restores to a new database; automatic restore history is limited to 7 days, and manual snapshots are separately retained. | RDS is the better fit for financial records if the required retention exceeds seven days or more recovery controls are needed. Test restores; backups without rehearsals are not proof of recoverability. |
| Networking | RDS normally resides in a VPC and supports security-group-scoped access. The Lightsail application host's actual path to the referenced RDS endpoint is not visible; validate VPC peering/routing and whether the endpoint is public. | The database is non-public and is designed to be reachable by Lightsail resources in the same account. This aligns directly with the Terraform Lightsail web host. | Lightsail has simpler connectivity for an all-Lightsail stack. RDS provides clearer VPC boundaries, but the current Lightsail-host-to-RDS path must be verified and kept private. |
| Operations | RDS provides configurable instance classes, Multi-AZ options, monitoring, maintenance, and backup controls with more operational settings. The current RDS class/HA/monitoring configuration is unknown. | Lightsail offers fixed monthly plans and a smaller set of controls. The checked-in resource selects one AZ and does not declare an HA plan. | RDS is preferable for production database operations and availability controls, subject to cost and a confirmed private network design. Lightsail is simpler but offers less room for recovery and scaling policy. |
| Cost | No RDS instance class, storage size, IOPS, retention, or Multi-AZ setting is in the Terraform root; a defensible monthly RDS total cannot be calculated from this checkout. Cost varies by region and selected options. | The example uses the Lightsail `medium_2_0` bundle. AWS currently lists its Medium managed database plan at $60/month; verify the exact bundle/region price in the account calculator before approval. | Lightsail has the more predictable bundle price. RDS may cost more, especially Multi-AZ and retained backups, but the current estimate is unknown. Compare equivalent capacity, HA, backup retention, transfer, and taxes in AWS Pricing Calculator. |

## Recommendation

**Recommend RDS MySQL as the single production database architecture.** The repository's deployment inventory identifies RDS as the production runtime target, the deployment path is built around RDS CA trust, and RDS provides configurable recovery retention and network/availability controls that fit a financial production database. This is a repository-level recommendation, not a claim about live configuration.

Before implementing that choice, an authorized operator must verify the protected GitHub Environment's database host without copying credentials, the running backend's database identity, the RDS instance's account/region, encryption and backup policy, network path, MySQL version, schema migration level, and least-privilege application grants. Also inspect Terraform state ownership for the Lightsail declaration. Do not create the Lightsail resource, point the app at it, import either resource, migrate data, or remove state until that reconciliation and explicit architecture approval are complete.

If Lightsail is selected instead, first establish a maintenance window and a lossless database migration plan (consistent snapshot/dump, schema and Flyway checks, users/grants, app cutover, rollback, DNS/endpoint behavior, and restore evidence). Do not run both as independent writable production databases.

## Terraform remote state design (proposal; not provisioned)

Use an S3 backend in a dedicated infrastructure/state account or otherwise isolated administrative boundary, with separate state keys for production and test. Use a bucket with versioning, Block Public Access, Bucket Owner Enforced ownership, TLS-only bucket policy, and default SSE-KMS encryption with a customer-managed key whose deletion is protected. Do not put backend credentials, bucket keys, KMS keys, or secrets in Terraform source, `-backend-config` files, plans, or logs. Use short-lived GitHub OIDC and human role sessions rather than long-lived AWS keys.

Use Terraform S3 native lockfiles (`use_lockfile = true`; Terraform 1.10 or later) rather than introducing a new DynamoDB locking table. Grant each environment role only `s3:ListBucket` on the required prefix; `s3:GetObject`/`s3:PutObject` on its state object; and `s3:GetObject`/`s3:PutObject`/`s3:DeleteObject` on that object's `.tflock`. Grant only the required KMS encrypt/decrypt/data-key operations on the one state key. Keep state read access restricted because Terraform state can contain passwords and other sensitive resource attributes even when outputs are marked sensitive. Separate production and test state access policies and require protected production Environment approval for any production plan or write.

### Safe migration from current local state

1. Freeze Terraform operations and identify the authoritative local state/workspace for each environment. Confirm no other operator or CI job is changing the same resources.
2. Take a protected, encrypted, access-controlled backup of each local state and its matching Terraform configuration/lock file. Record state lineage, serial, workspace, and a cryptographic checksum without publishing state contents.
3. Provision and independently review the bucket, KMS key, policies, versioning, public-access block, and lock permissions in the isolated state account. Test access with a least-privilege role. Do not place state infrastructure in the stack whose state it protects.
4. Add the approved partial S3 backend configuration and initialize the matching workspace with `terraform init -migrate-state`. Review Terraform's migration prompt and stop if it reports unexpected state/workspace differences. Never use `-force-copy` to bypass a mismatch.
5. Verify the remote object's encryption, versioning, lock acquisition/release, state lineage/serial, and `terraform state list`; then run a read-only plan and compare it with the pre-migration plan. Do not apply as part of migration.
6. Retain the encrypted local backup under the approved retention policy until a successful restore rehearsal and owner sign-off. After cutover, prevent stale local state from being reused; do not delete the only recovery copy.

Rollback is a controlled state-backend migration, not a resource rollback. Freeze all writers, preserve the remote object and versions, and use a reviewed `terraform init -migrate-state` back to local only if state lineage/serial are consistent. Do not copy or edit raw state by hand. Restoring an older state can forget later infrastructure changes and cause destructive plans; require a fresh plan review before any future apply.

## CI and local check policy

Repository CI and local hooks should run formatting, backend-free initialization, validation, and mocked-provider `terraform test` plans for both Terraform roots. Mocked plans prove configuration can produce a plan without AWS credentials; they do not refresh real infrastructure, validate live state, or authorize an apply. Any real state-aware plan must run only in a protected environment with least-privilege read access, reviewed output, and no apply step. Plan files and logs must be treated as sensitive artifacts and not exposed to untrusted pull requests.

## Sources

- [AWS Lightsail managed database FAQ](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-faq-databases.html)
- [AWS Lightsail database plans and pricing](https://docs.aws.amazon.com/lightsail/latest/userguide/amazon-lightsail-choosing-a-database.html)
- [AWS RDS for MySQL pricing](https://aws.amazon.com/rds/mysql/pricing/)
- [AWS RDS encryption](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.Encryption.html)
- [AWS RDS backup retention](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.BackupRetention.html)
- [Terraform S3 backend and locking](https://developer.hashicorp.com/terraform/language/backend/s3)
