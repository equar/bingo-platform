# Branch promotion and deployment policy

## Promotion and trigger matrix

| Action | Allowed source/context | Result |
| --- | --- | --- |
| Pull request to `develop` | Any non-protected feature/fix branch, including a fork | Allowed by Branch Promotion Policy; PR Validation Pipeline runs |
| Pull request to `release` | `equar/bingo-platform:develop` only | Allowed; PR Validation Pipeline runs |
| Pull request to `main` | `equar/bingo-platform:release` only | Allowed; PR Validation Pipeline runs |
| Pull request to any other target | No promotion policy applies | No release/deployment effect |
| Web/backend deployment dispatch from `develop` | Canonical repository, branch ref | Test environment only; never production |
| Web/backend deployment dispatch from `main` | Canonical repository, branch ref, `confirm_production=true` | Production Environment approval required |
| Web/backend deployment from `release`, feature branch, tag, push, or PR event | Not allowed | No deployment job |
| Android internal testing / iOS TestFlight dispatch | Canonical repository, `main` branch | Non-production store channel; source commit is main |
| Android production / iOS App Store dispatch | Canonical repository, `main`, `confirm_production=true` | Matching production Environment approval required; provenance must be from a successful main-branch upload |

CI validation is separate from deployment. `PR Validation Pipeline` runs against PRs to `develop`, `release`, and `main`; deployment has no `push` or pull-request trigger. The deploy workflow still builds the selected application before deployment. The backend deployment script keeps its Flyway compatibility preflight, failed-migration refusal, health checks, and schema-aware rollback behavior. Frontend deployment keeps URL preflight and backup directories. Keep these scripts as part of the deployment path.

## GitHub settings required outside workflow YAML

Configure repository rulesets/branch protection for `develop`, `release`, and `main`:

- Require pull requests; prohibit direct pushes, force pushes, and branch deletion. Do not grant bypass to admins or automation accounts except a separately reviewed break-glass process.
- Require the `Branch Promotion Policy` check and the `PR Validation Pipeline` backend, Admin, and website checks. Require at least one independent approval, dismiss stale approvals after new commits, and require approval of the most recent push.
- Apply the same protections to administrators and require conversation resolution. Limit who can modify rulesets and workflow files.
- Protect `.github/workflows/**`, deployment scripts, and this policy document with `CODEOWNERS` review from maintainers responsible for release/security policy.

Configure GitHub Environments:

- `test`: allow deployments from `develop` only; require an independent reviewer and prevent self-review. Scope test-only deploy credentials and variables here.
- `production`: allow deployments from `main` only; require an Environment reviewer selected from at least two eligible release approvers, enable prevention of self-review, and disable administrator bypass where available. Store production deployment secrets only in this Environment, not as repository-wide secrets.
- `admin-e2e`: allow `main` only; require an independent reviewer, prevent self-review, and scope the non-production Admin test account and API URL to this Environment.
- `bingo-app-android-internal` and `bingo-app-ios-testflight`: allow `main` only, require an independent release reviewer, prevent self-review, and scope the respective store upload/signing credentials here.
- `bingo-app-android-production` and `bingo-app-ios-production`: allow `main` only, require an independent Environment reviewer selected from eligible store approvers, prevent self-review, disable bypass where available, and scope store credentials to the respective Environment.

The workflows request these Environment names; they do not prove that the Environments exist or that their protection rules and secrets are configured. No authenticated GitHub settings API was available during the repository audit, so reviewer lists, prevent-self-review, branch restrictions, bypass settings, and secret presence remain to be verified in repository Settings. In **Settings → Environments**, open each name above and verify: at least one eligible reviewer (at least two eligible production approvers), prevent self-review enabled, deployment branch restriction as listed, bypass disabled where supported, and only that environment's minimum required secrets/variables. For Graph email, check the deployment Environment's `APP_EMAIL_PROVIDER` and `APP_EMAIL_FROM` variables and, when provider is `graph`, the presence of `MICROSOFT_GRAPH_TENANT_ID`, `MICROSOFT_GRAPH_CLIENT_ID`, and `MICROSOFT_GRAPH_CLIENT_SECRET` secrets. Check names/presence only; never reveal secret values. If provider is `smtp`, verify `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` instead. Do not configure both providers as a fallback.

The browser E2E workflow requires `ADMIN_E2E_ENABLED=true`, `ADMIN_E2E_API_BASE_URL`, `ADMIN_E2E_ADMIN_EMAIL`, and `ADMIN_E2E_ADMIN_PASSWORD` in `admin-e2e`; the workflow rejects the production API host. Internal Android testing needs its Android signing secrets and `GOOGLE_PLAY_JSON`; Android production needs `GOOGLE_PLAY_JSON`. TestFlight and App Store submission need their respective Apple signing and App Store Connect secrets. Keep these credentials environment-scoped and unavailable to pull-request jobs.

Production backend and mobile store promotion workflows are manual (`workflow_dispatch`) and require `confirm_production=true` for production actions. Backend production deployment checks out the requested `main` SHA and verifies it is an ancestor of `origin/main`; mobile store promotion validates the source upload run and its main-branch provenance. GitHub Environment approval is a separate required gate. The phrase “approved main commit” therefore depends on branch protection and Environment reviewer settings as well as the workflow checks.

The backend deployment validator requires database connection policy explicitly: `test` requires `MYSQL_SSL_MODE=DISABLED` and `MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL=true`; `prod` requires `MYSQL_SSL_MODE=VERIFY_IDENTITY` and `MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL=false`. Missing or mismatched values stop deployment instead of being defaulted or silently replaced.


GitHub Environment reviewers, deployment branch policies, repository rulesets, bypass rights, and secret scoping are repository settings; workflow YAML can request an Environment but cannot create or guarantee those settings. Verify the settings after applying this policy. Protect the default branch and ensure changes to these workflow files themselves require review.

## Local validation

Run `python3 scripts/ci/validate-github-workflows.py` to parse every workflow and assert the production/deployment trigger matrix. The branch guard workflow includes source/target policy cases and is exercised by its `BRANCH_POLICY_TESTS_ONLY=1` mode when extracting/running the embedded Node check.
