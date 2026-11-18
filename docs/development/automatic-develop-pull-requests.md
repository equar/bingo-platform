# Automatic working-branch pull requests

Pushes to repository branches other than `develop`, `release`, and `main`
automatically open one ready-for-review pull request into `develop`. Existing
open PRs are reused and subsequent pushes update them normally. The workflow
does not approve or merge PRs. Existing branch protections, reviews, promotion
policy, and PR validation remain authoritative.

## One-time GitHub setup

Create or reuse a GitHub App installed on `equar/bingo-platform`. Give it only
`Contents: read` and `Pull requests: write` repository permissions, then add:

- Repository variable `BINGO_PR_AUTOMATION_APP_ID` with the App ID.
- Repository secret `BINGO_PR_AUTOMATION_APP_PRIVATE_KEY` with its private key.

The workflow mints an installation token scoped only to this repository. The
App token is required so opening the PR triggers normal `pull_request`
workflows. The run fails with a setup message if either credential is absent.

## Validation

Run `node --test scripts/ci/open-develop-pull-request.test.mjs` to exercise
eligible pushes, existing PR reuse, excluded/deleted branches, no-commit
branches, and concurrent PR creation. A successful job logs the opened or
existing PR URL. API/authentication failures fail the job with a clear message.
