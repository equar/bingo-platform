# Documentation Index

The root [README](../README.md) is the quick start. Component READMEs contain
their setup and test commands; this index points to maintained cross-project
guidance.

## Development and contracts

- [Local E2E overview and database boundaries](local-e2e-testing.md)
- [Backend](../bingo-backend/README.md), [Admin](../bingo-admin/README.md),
  [Player](../bingo-app/README.md), and [Agent](../bingo-agent/README.md) guides
- [OpenAPI integration](../bingo-backend/docs/openapi-integration.md) and
  [live-draw WebSocket contract](websocket.md)
- [Player community features and rules](community-features.md)

## Deployment and release

- [Deployment entry point](../deployment/README.md), [AWS deployment design](../deployment/aws/README.md),
  [server and secret setup](../deployment/aws/SETUP_CHECKLIST.md), and
  [first Admin bootstrap](../deployment/aws/ADMIN_BOOTSTRAP.md)
- [GitHub Actions deployment](../deployment/github-actions.md)
- [Mobile store deployment](mobile-store-deployment.md) and
  [direct-share mobile packages](../deployment/mobile/README.md)
- [Website release checklist](../bingo-website/CHECKLIST.md)

## QA, security, and financial controls

- [QA index](qa/README.md) — policy, current status, procedures, and retained evidence
- [Mandatory real E2E policy](qa/mandatory-real-e2e-testing-policy.md)
- [Release checklist](qa/release-checklist.md), [release blockers](qa/release-blockers.md),
  [security validation](qa/security-validation.md), and
  [financial reconciliation](qa/financial-reconciliation.md)
- [Local QA environment record](qa/local-environment.md) — verify its machine-specific
  state before relying on recorded processes or accounts
