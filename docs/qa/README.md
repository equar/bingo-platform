# QA Documentation Index

This directory contains current QA policy and status, operational procedures, dated module reports, and retained evidence. The reports document scoped results; they are not a live view of running services.

## Current authorities

- [Mandatory Real E2E Testing Policy](mandatory-real-e2e-testing-policy.md) — functional completion requires applicable real UI/API/isolated-MySQL execution and reconciliation. Unit, widget, mocked, static, and analyzer checks are supplemental.
- [Module Status](module-status.md) — latest recorded module dispositions. Read the linked report for its date, scope, and evidence; do not infer a fresh PASS from a prior run.
- [Local E2E procedures](../local-e2e-testing.md) — current backend, Admin Chromium, player Android, and agent Android workflow boundaries.
- [Local environment record](local-environment.md) — machine-specific setup snapshot; verify service health, schema, account, grants, and flags before execution.
- [Test-data registry](test-data-registry.md) — persistent QA identities/fixtures and their history. It is also read by the QA runner; keep it available and never record credentials.

## Validation and release

- [Master E2E coverage matrix](master-e2e-coverage-matrix.md) and [business-domain inventory](master-business-domain-inventory.md) — dated evidence inventories, not a claim that every workflow is complete.
- [Remaining business workflows](remaining-business-workflows.md) and [remaining QA roadmap](remaining-qa-roadmap.md) — outstanding scope and proposed sequencing; a roadmap is not execution authorization.
- [Release checklist](release-checklist.md), [release blockers](release-blockers.md), [production readiness](production-readiness-report.md), and [execution plan/results](qa-execution-plan-and-results.md) — dated go/no-go records.
- [Financial reconciliation](financial-reconciliation.md), [security validation](security-validation.md), and [load-test results](load-test-results.md) — preserve scoped findings and explicit unverified work.
- [Defect remediation matrix](defect-remediation-matrix.md) — historical findings, fixes, and their actual verification scope.

## Client and business-module evidence

- **Backend:** integration-test schema and command requirements are in the [backend README](../../bingo-backend/README.md); destructive Maven tests are limited to the verified disposable `bingo_qa_maven_test` target.
- **Admin Chromium:** the guarded Module 06A-5 procedure and result are linked from [Local E2E procedures](../local-e2e-testing.md) and [Module 06A-5](modules/module-06a5-functional-gap-closure.md).
- **Player Android:** the guarded Module 06A-9 runner and latest outcome are in [Module 06A-9](modules/module-06a9-android-player-functional-e2e.md). A prepared runner or blocked device launch is not an E2E PASS.
- **Agent Android:** use the guarded launcher and latest outcome recorded in [Module 01](modules/module-01-authentication.md); prior real-device reports remain [Module 02A](modules/module-02a-agent-lifecycle.md) and [Module 02B](modules/module-02b-agent-float.md). Historical reports do not authorize new writes to persistent `bingo_qa`.
- **Claims, draws, wallet, and session behavior:** use the corresponding module report and test matrix under [`modules/`](modules/). Their status and evidence are scoped to the date and scenario stated in each report.

## Evidence retention

Keep dated reports, screenshots, sanitized logs, read-only database evidence, incident notes, and migration/recovery decisions when they explain what was actually verified or blocked. Do not delete historical evidence merely because it is old or has no inbound link. Do not promote mocked/unit results to E2E status, rewrite earlier outcomes retroactively, or retain secrets, tokens, passwords, PINs, or live financial data.
