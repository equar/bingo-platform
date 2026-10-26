# Remaining QA Roadmap

**Audit snapshot:** 2026-10-25. Ordering is a dependency proposal, not authorization to execute database mutations or financial operations.

**Completion policy:** All remaining and future functional modules follow the [Mandatory Real E2E Testing Policy](mandatory-real-e2e-testing-policy.md). Unit, widget, analyzer, mocked API, and static checks are supplemental; module completion requires applicable real UI/API/isolated-MySQL E2E with read-only reconciliation, negative/security coverage, and confirmation that unrelated financial and historical records remained unchanged. Existing historical outcomes are preserved.

## Gate 0 — Environment and isolation

1. For local functional E2E, verify the Android/Admin schema and account identity without exposing secrets. The project owner accepts ALL PRIVILEGES scoped to local `bingo_qa_test`; no DBA grant reduction or grant change is required. Keep the account denied access to `bingo`, persistent `bingo_qa`, and other schemas. Production still requires least privilege.
2. Verify Maven's disposable schema/account separately only if local Maven integration tests are needed. Do not use the functional E2E account for Maven suites.
3. Confirm current configuration guards, profiles, schema targets, and financial defaults. Never run destructive Maven tests against `bingo` or `bingo_qa_test`; keep any authorized destructive recovery isolated to its verified disposable schema. No Flyway clean on persistent databases.

## Gate 1 — Fixture and product decisions

1. Obtain Product/Security/DBA approval for the nonfinancial, synthetic owned-ticket fixture design in [Module 04D-5B](modules/module-04d5b-ticket-fixture-design.md). Keep the mechanism non-public and explicitly opted in.
2. Resolve ticket eligibility/cutoff, CANCELLED and CLOSED claim rules, draw retry/idempotency contract, and fixture retention/cleanup policy.
3. Keep wallet credit, prize calculation, settlement and external payments disabled pending Finance/accounting approval.

## Gate 2 — Claim E2E

After gates 0–1 and required Product/Security/DBA approvals, implement only the approved fixture mechanism and validate its provenance. Run specific claim scenarios through the real API and isolated MySQL, then actual Android and Admin Chromium where applicable. Reconcile claim/audit/ticket/session rows and prove wallet/settlement and unrelated historical state unchanged. Include negative, authorization, retry, and duplicate scenarios. Do not use full Maven lifecycle until the destructive-suite target and guards are verified; defer clean/recovery tests to the disposable schema.

## Gate 3 — Close existing business-module gaps

Prioritize the existing Module 02 transfer detail/policy and schedule/prize decisions; Module 04B purchase UI/accounting; Module 04C draw retry semantics and history integrity; then remaining player/agent support, reporting, notification, website and release journeys. Preserve every module's stated scope and completion criteria.

## Gate 4 — Production readiness

Only after product, security, finance, operations and release approvals: implement approved settlement/funding behavior, external integrations, operational monitoring and backup/restore evidence. Production authorization is separate from QA completion.

## Stop conditions

Stop a workflow if the active profile/schema/account is uncertain, a guard allows a persistent schema, fixture creation invokes wallet/float/commission/settlement behavior, unexplained existing rows change, or a required policy is unresolved. The local `bingo_qa_test` grant is not a stop condition under the owner-approved exception. Record the result as BLOCKED or PARTIAL; do not replace real E2E with mocks and claim completion, and do not repair state by deleting historical records.
