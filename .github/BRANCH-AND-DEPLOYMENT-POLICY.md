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

- `test`: allow deployments from `develop` only. Store test-only deploy variables and credentials here.
- `production`: allow deployments from `main` only; require an Environment reviewer selected from at least two eligible release approvers, enable prevention of self-review, and disable administrator bypass where available. Store production deployment secrets only in this Environment, not as repository-wide secrets.
- `bingo-app-android-production` and `bingo-app-ios-production`: allow `main` only, require an independent Environment reviewer selected from eligible store approvers, prevent self-review, disable bypass where available, and scope store credentials to the respective Environment.
- If internal store upload credentials are Environment-scoped, allow only `main` for those environments too. Never place signing or store credentials in a PR-accessible context.

GitHub Environment reviewers, deployment branch policies, repository rulesets, bypass rights, and secret scoping are repository settings; workflow YAML can request an Environment but cannot create or guarantee those settings. Verify the settings after applying this policy. Protect the default branch and ensure changes to these workflow files themselves require review.

## Local validation

Run `python3 scripts/ci/validate-github-workflows.py` to parse every workflow and assert the production/deployment trigger matrix. The branch guard workflow includes source/target policy cases and is exercised by its `BRANCH_POLICY_TESTS_ONLY=1` mode when extracting/running the embedded Node check.
