# Documentation Index

Use this page to find the current project guides. The root [README](../README.md) is the quick-start entry point; component READMEs cover per-app setup.

## Development and integration

- [Local E2E testing and database isolation](local-e2e-testing.md) — current backend, Admin Chromium, player Android, and agent Android validation boundaries.
- [Backend README](../bingo-backend/README.md) — local MySQL, configuration, Maven integration-test schemas, and security notes.
- [Admin README](../bingo-admin/README.md) — Vite configuration, checks, API expectations, and Playwright credential handling.
- [Player app README](../bingo-app/README.md) — Flutter setup, API URL requirements, WebSocket behavior, and Android E2E pointers.
- [Agent app README](../bingo-agent/README.md) — Flutter setup, localization, agent operations, and Android test scope.
- [OpenAPI integration](../bingo-backend/docs/openapi-integration.md) and [live-draw WebSocket contract](websocket.md).

## Operations and release

- [Deployment guide](../deployment/README.md), [AWS Lightsail design](../deployment/aws/README.md), and [Lightsail setup checklist](../deployment/aws/SETUP_CHECKLIST.md).
- [GitHub Actions guide](../deployment/github-actions.md).
- [Mobile release readiness](mobile-release-readiness.md) and [mobile store deployment](mobile-store-deployment.md).
- [Website checklist](../bingo-website/CHECKLIST.md).

## QA, security, and financial controls

- [QA documentation index](qa/README.md) — current policy, status, workflow procedures, and historical evidence.
- [Mandatory real E2E policy](qa/mandatory-real-e2e-testing-policy.md) — required UI/API/isolated-MySQL evidence and financial safety gates.
- [QA release checklist](qa/release-checklist.md), [release blockers](qa/release-blockers.md), [security validation](qa/security-validation.md), and [financial reconciliation](qa/financial-reconciliation.md).
- [Local QA environment record](qa/local-environment.md) — a machine-specific setup snapshot; verify it before relying on any recorded process or account.

## Historical design

- [Initial implementation plan](archive/initial-implementation-plan.md) — archived; it describes an empty repository and an obsolete database choice.
- [Initial domain decisions](archive/initial-domain-decisions.md) — archived product/design intent; it does not authorize financial behavior or override current QA and release gates.
- Dated QA reports and evidence remain in `docs/qa/` so previous results and decisions are preserved. Check each report's date and scope; a historical PASS is not a current verification.
