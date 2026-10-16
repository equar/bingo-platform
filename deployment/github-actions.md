# Bingo GitHub Actions

This repository uses three workflow groups for Bingo:

- `branch-flow-guard.yml` enforces the allowed PR flow between `public-landing-website` and `main`.
- `pr-validation-pipeline.yml` runs backend and admin validation on pull requests.
- `deploy-backend.yml`, `deploy-admin.yml`, `deploy-all.yml`, and `deploy-website.yml` provide manual production deployments.

## Workflow summary

- `branch-flow-guard.yml` runs on pull requests and rejects invalid merge directions.
- `pr-validation-pipeline.yml` runs on pull requests targeting `main` or `public-landing-website`, and it can also be started manually.
- `deploy-backend.yml` is a manual backend-only release.
- `deploy-admin.yml` is a manual admin-only release.
- `deploy-all.yml` is a manual combined release that builds both artifacts once and deploys those exact artifacts.
- `deploy-website.yml` is a manual static-site release to AWS S3, with optional CloudFront invalidation.

## Validation flow

The PR pipeline does two independent checks:

- Backend validation runs Maven `verify` against an isolated MySQL service container.
- Admin validation installs from `bingo-admin/package-lock.json`, runs Vitest, performs a TypeScript check, and builds the production bundle.

Artifacts from validation are uploaded for review:

- Backend test reports from `bingo-backend/target/surefire-reports`.
- Backend package output from `bingo-backend/target`.
- Admin Vitest results from `bingo-admin/test-results`.
- Admin build output from `bingo-admin/dist`.

## Deployment flow

All deployment workflows are `workflow_dispatch` only.
That keeps production releases manual and prevents deployment from running on pull requests or untrusted fork code.

Each deploy workflow follows the same pattern:

1. Decode the SSH key and backend env file from GitHub secrets.
2. Build the release artifact locally in the workflow runner.
3. Deploy the exact artifact with the existing shell scripts in `deployment/`.
4. Run the built-in health check from the deploy script.

The deploy scripts still own the remote release switch and rollback support.
They keep the current release in `current` and the previous release in `previous` so rollback remains a remote symlink switch instead of a rebuild.

The website workflow publishes the Astro `dist/` output directly to S3 instead of using the backend/admin shell deploy scripts.
That keeps the public site separate from the same-origin application host and matches the site’s static deployment model.

## Required repository settings

Create the following GitHub environments:

- `bingo-backend-production`
- `bingo-admin-production`
- `bingo-production`

Recommended protections:

- Require reviewers before a deployment job starts.
- Restrict who can approve each environment.
- Keep the deploy workflows manual until the release process is stable.

## Required variables and secrets

The workflows expect these repository or environment values:

- `BINGO_SERVER` - SSH target for the production host.
- `BINGO_SSH_KEY_B64` - base64-encoded SSH private key.
- `BINGO_BACKEND_ENV_FILE_B64` - base64-encoded backend env file used by the remote service.
- `BINGO_BACKEND_SERVICE_NAME` - systemd service name for the backend, default `bingo-backend`.
- `BINGO_REMOTE_BACKEND_ROOT` - backend release root, default `/opt/bingo/backend`.
- `BINGO_REMOTE_ADMIN_ROOT` - admin release root, default `/var/www/bingo-admin`.
- `BINGO_SERVICE_USER` - remote runtime user, default `bingo`.
- `BINGO_ADMIN_PUBLIC_URL` - public origin used by the admin build and health check.
- `BINGO_AWS_REGION` - AWS region for the website deployment.
- `BINGO_WEBSITE_BUCKET` - S3 bucket that serves the public website.
- `BINGO_WEBSITE_CLOUDFRONT_DISTRIBUTION_ID` - optional CloudFront distribution ID for cache invalidation.
- `BINGO_AWS_ROLE_ARN` - IAM role ARN used by GitHub Actions OIDC to access AWS.

The backend env file must contain the runtime database and security configuration required by the backend shell script, including MySQL connection values, JWT secret, and allowed origins.

## Rollback and health checks

Rollback is handled by the existing deployment scripts, not by the workflow.

- Backend deployment validates `http://127.0.0.1:8081/actuator/health` on the remote host.
- Admin deployment validates the public origin with `curl` after the release switch.

## How to enable automatic deployment later

If you later want automatic deploys, add a branch trigger to the relevant workflow after the manual flow is stable.
The safest pattern is to keep `workflow_dispatch` as the primary entry point and add a protected `push` trigger only for the final production branch.

Do not enable automatic deploys until the environment protections, secrets, and rollback process are all confirmed in a dry run.

## Notes

- No Flutter deployment workflow is included.
- The BillPay workflows were used only as a structural reference; no BillPay hostnames, secrets, or service names were copied.