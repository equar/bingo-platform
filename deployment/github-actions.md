# Bingo GitHub Actions

`deploy.yml` is the sole production application deployment workflow. It runs
only after an in-repository feature-branch pull request into `main` is merged and
checks out that merge commit. Production runs are serialized and are not
cancelled when another merge arrives. There is no manual dispatch or OIDC
GitHub Environment; repository secrets and variables configure SSH deployment,
matching BillPay's workflow model.

`pr-validation-pipeline.yml` verifies backend tests against disposable MySQL,
admin tests/lint/build, and public website checks on PRs to `main` and the
existing website branch. Require this validation before production merges.
The branch guard accepts feature branches directly into `main` without source
branch naming restrictions. No intermediate branches are required.

`validate-lightsail-infrastructure.yml` checks deployment shell/Python syntax,
the production Nginx template, and the retained reference CloudFormation
file. It does not deploy. Mobile CI/store workflows remain independent.

See [the configuration checklist](aws/SETUP_CHECKLIST.md) for exact secret and
variable names. The old manual backend/admin/all and OIDC deployment workflows
were removed to avoid concurrent deployments with incompatible paths.
