# Deployment admin accounts by email

The deployment workflow enables email bootstrap for `apps@bakaroo.com` and
`equarhishe@gmail.com` by default. Repository variables can override this:

| Variable | Default |
|---|---|
| `ADMIN_EMAIL_BOOTSTRAP_ENABLED` | `true` in the deployment workflow; `false` outside it |
| `ADMIN_BOOTSTRAP_EMAILS` | `apps@bakaroo.com,equarhishe@gmail.com` |

Keep the legacy `ADMIN_BOOTSTRAP_ENABLED=false`. The deployment validator rejects
both modes being enabled together. No bootstrap password or phone number is
needed for email bootstrap. Configure either SMTP or Graph using `APP_EMAIL_PROVIDER` as described in
[Email providers](EMAIL_PROVIDERS.md). `APP_EMAIL_FROM` is the sender address;
it defaults to `SPRING_MAIL_USERNAME` in SMTP mode.

After Flyway completes, each missing email gets an active ADMIN account with
its own cryptographically random, readable password (six pronounceable groups
and four digits). The password is emailed only to that account, stored only as
a password hash, never logged, and expires in 24 hours. First login requires a
password change. Existing email identities are skipped without changing roles,
passwords, or status, including existing non-admin accounts.

Each account is created in a separate transaction. Email provider failures fail startup
and roll back that account, allowing the next deployment to retry it while
previously completed accounts are skipped. Provider acceptance is not proof of inbox
delivery; monitor delivery/bounces. Database commit and email delivery cannot be
atomic, so a rare commit failure after sending can result in another email on
retry. Expired passwords use the existing administrator password-reset flow.
Concurrent account creation is constrained by the unique email index; retry a
failed deployment after confirming the other attempt completed.

Set `ADMIN_EMAIL_BOOTSTRAP_ENABLED=false` to stop checks. It can remain enabled
for later deployments because existing accounts are never recreated or emailed.

---

# Legacy one-time production Admin bootstrap

The production-only bootstrap creates one active `ADMIN` user and its required
`user_roles` association after Spring has initialized the database and Flyway
migrations. It does not create agents, players, wallets, sample transactions,
or development fixtures. It is disabled unless `ADMIN_BOOTSTRAP_ENABLED=true`.
The development seeder remains separate and cannot run when the `prod` profile
is active, including when profiles are combined.

## Configure the one-time credentials

The production deployment reads repository variables and secrets through the
existing GitHub Actions deployment workflow. Use an authenticated GitHub CLI
and replace `OWNER` with the repository owner. The bootstrap password is
entered without echo and is never placed in shell history:

```bash
gh variable set ADMIN_BOOTSTRAP_ENABLED --repo OWNER/bingo-platform --body true
gh variable set ADMIN_BOOTSTRAP_PHONE --repo OWNER/bingo-platform --body '+2519XXXXXXXX'
gh variable set ADMIN_BOOTSTRAP_NAME --repo OWNER/bingo-platform --body 'Your Admin Name'
gh variable set ADMIN_BOOTSTRAP_EMAIL --repo OWNER/bingo-platform --body 'you@example.com'
read -rsp 'Initial Admin password: ' ADMIN_BOOTSTRAP_PASSWORD
printf '\n'
printf '%s' "$ADMIN_BOOTSTRAP_PASSWORD" | gh secret set ADMIN_BOOTSTRAP_PASSWORD --repo OWNER/bingo-platform
unset ADMIN_BOOTSTRAP_PASSWORD
```

Use a unique Ethiopian mobile number, a valid email, and a password of at least
16 characters and at most 72 UTF-8 bytes. The email is required for the normal
Admin login flow. Phone, display name, email, and password are required while
enabled. Configure these values only in GitHub repository settings or its
authenticated CLI; do not add real values to repository files.

## Deploy and log in

1. Merge the reviewed release through the repository's normal protected PR
   deployment path. The existing `Build and Deploy Bingo to Lightsail`
   workflow passes the settings to the root-protected runtime environment. The
   bootstrap runs after Flyway migrations.
2. Open `https://admin.bingo.bakaroo.com` and log in with the configured email
   and initial password. This uses the normal `/api/v1/admin/login` flow and
   the existing `ADMIN` role.
3. From the Admin account menu, open **Change password** and replace the
   bootstrap password with a new password. The existing flow verifies the
   current password before saving the new one.
4. Set `ADMIN_BOOTSTRAP_ENABLED` to `false`, then remove the password secret.
   Remove the identity variables too if they are no longer needed:

   ```bash
   gh variable set ADMIN_BOOTSTRAP_ENABLED --repo OWNER/bingo-platform --body false
   gh secret delete ADMIN_BOOTSTRAP_PASSWORD --repo OWNER/bingo-platform
   gh variable delete ADMIN_BOOTSTRAP_PHONE --repo OWNER/bingo-platform
   gh variable delete ADMIN_BOOTSTRAP_NAME --repo OWNER/bingo-platform
   gh variable delete ADMIN_BOOTSTRAP_EMAIL --repo OWNER/bingo-platform
   ```

5. Run the next normal deployment so the protected `/opt/bingo/.env` is
   refreshed without the one-time secret and with bootstrap disabled.

If the requested phone or email already belongs to any account, bootstrap
skips without changing that account's password, status, or roles. Check the
existing account and use the normal account recovery procedure if needed. A
concurrent unique-key collision fails startup with a safe instruction to
verify the account before retrying. Bootstrap never upgrades an existing
player/support account to `ADMIN`.
