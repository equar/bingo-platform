# Production SSH trust and Admin provisioning

## GitHub Environment setup

Create `test` and `production` GitHub Environments and scope deployment variables and secrets to the matching environment. Configure `production` with required reviewers, prevent self-review where available, and restrict deployment branches to `main`. Configure `test` for `develop`. GitHub Environment protection is an account setting and cannot be enforced by workflow YAML alone. The deployment job pauses for the Environment review before its secrets and variables are available.

For both environments, configure `DEPLOY_HOST`, `DEPLOY_USER`, and `DEPLOY_SSH_HOST_KEY` as environment variables, and `DEPLOY_SSH_KEY_B64` as an environment secret. `DEPLOY_SSH_HOST_KEY` must be one OpenSSH public host key line, such as `ssh-ed25519 AAAA...`. Retrieve the host public key or fingerprint from an independently authenticated cloud/host control plane or an administrator's verified inventory. Compare its SHA256 fingerprint out of band before saving it; never copy a key learned from the GitHub runner's connection. The workflow and deployment scripts install exactly this key into a temporary `known_hosts` file and use strict host checking. Missing or malformed pins stop deployment.

The deployment configuration importer requires `DEPLOY_SSH_HOST_KEY` in its environment-specific input file. Keep the input file outside version control and use `GH_ENVIRONMENT=test` or `GH_ENVIRONMENT=production` so values are stored at the correct Environment scope.

## Admin bootstrap policy

Production email bootstrap and legacy one-account bootstrap are both disabled by default. No production Admin email addresses are embedded in the workflow. The email bootstrap recipient list is sourced only from `ADMIN_BOOTSTRAP_EMAILS` in the protected `production` Environment; legacy account fields are likewise environment-scoped. Existing account identities are never upgraded, disabled, deleted, or have their password changed by either bootstrap service. Existing Admins therefore remain able to log in when bootstrap is disabled or skipped.

If initial provisioning is needed, use the `Build and Deploy Bingo` workflow on `main`, select production confirmation, and set `approve_initial_admin_bootstrap=true`. Supply `admin_bootstrap_approval_expires_at` as a UTC timestamp ending in `Z`, no more than 60 minutes in the future. The workflow run records the actor, inputs, and Environment reviewer approval. Bootstrap is enabled only when the corresponding Environment variable (`ADMIN_EMAIL_BOOTSTRAP_ENABLED=true` or `ADMIN_BOOTSTRAP_ENABLED=true`) is also set. Enable only one mode at a time; the deployment validator checks the complete configuration before SSH starts. Email mode also requires a deliberate `ADMIN_BOOTSTRAP_EMAILS` list. Legacy mode requires a one-time phone, name, email, and `ADMIN_BOOTSTRAP_PASSWORD` secret.

At application startup, the bootstrap runners require the recorded GitHub Actions run ID and recheck the approval expiry. They log the run ID and expiry when bootstrap is authorized, linking provisioning logs to the reviewed workflow run. If deployment is delayed past expiry, provisioning is skipped and the application starts normally. After provisioning, remove/disable the relevant bootstrap Environment variables and legacy password secret. The next deployment runs with both bootstrap modes disabled. Do not delete or recreate existing Admin accounts as a bootstrap workaround; use the normal Admin account recovery process.

## Required secret rotation

- Rotate `DEPLOY_SSH_KEY_B64` by installing the replacement public key on the host first, updating the protected Environment secret, verifying a deployment, then removing the retired host authorization. Update the pinned `DEPLOY_SSH_HOST_KEY` only after host-key replacement is independently verified; keep a controlled overlap/maintenance plan because host key rotation changes trust for every deployment client.
- Rotate database and email provider credentials at their provider, update the matching Environment secrets, deploy, then revoke the old credential. Do not put secret values in workflow variables or the repository.
- `JWT_SECRET` rotation invalidates existing signed access tokens; schedule it as a deliberate session revocation event and update the Environment secret before deployment.
- `BINGO_FLOAT_BANK_ENCRYPTION_KEY` protects stored receiving-account ciphertext. Do not replace it as an ordinary credential rotation: existing ciphertext must be decrypted with the old key and re-encrypted with the new key in a separately reviewed rotation procedure. Preserve a protected backup of the old key until migration and verification are complete. Production configuration and deployment validation fail closed if the key is missing or does not decode to exactly 32 bytes.
- Remove one-time bootstrap password material immediately after initial provisioning and password change. Approval inputs are time-limited and are not a substitute for Environment reviewers.

No production host, account, or credential was changed as part of this repository change.
