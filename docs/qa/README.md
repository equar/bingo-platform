# QA Documentation

Use the maintained guides for current policy and the dated evidence for the
specific run or release decision it supports. Historical test results are not
current certification.

## Current guidance

- [Guarded real E2E runbook](platform-e2e-runbook.md) — prerequisites, runner
  commands, isolation, safety gates, and completion criteria.
- [Current scenario matrix](platform-e2e-scenario-matrix.md) — stable cross-client
  scenario IDs and the latest recorded run status.
- [Business-domain inventory and historical coverage](master-business-domain-inventory.md)
  — implementation map and the consolidated D01–D39 evidence levels.
- [Current blockers and approvals](qa-blockers-and-approvals.md) — unresolved
  product, finance, security, database, operations, and release gates.
- [Financial controls and reconciliation](financial-reconciliation.md) —
  financial boundaries, verification, posting, and release evidence.
- [V23 local QA backup/restore runbook](local-bingo-qa-test-v23-runbook.md) —
  gated recovery procedure; prepared for review and not authorization to execute.
- [Persistent QA test-data registry](test-data-registry.md) — runner-readable
  record of retained test identities and state. Preserve its path and format;
  never add credentials or remove existing records through documentation work.

## Retained release evidence

The following six dated QA records remain linked because release decisions and
reviews depend on them:

- [Release checklist](release-checklist.md) and [release blockers](release-blockers.md)
- [Production-readiness report](production-readiness-report.md) and
  [QA execution results](qa-execution-plan-and-results.md)
- [Defect remediation matrix](defect-remediation-matrix.md) and
  [final independent review](final-independent-qa-review.md)

Additional retained run-specific evidence is under `docs/qa/evidence/`,
including the latest platform run report. Do not treat any historical PASS as
current certification, discard evidence supporting an open blocker or release
decision, or expose secrets in evidence.
