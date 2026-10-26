# Remaining QA Roadmap

**Audit snapshot:** 2026-10-25. Ordering is a dependency proposal, not authorization to execute database mutations or financial operations.

## Gate 0 — Environment and isolation

1. Resolve local DBA access and perform approved least-privilege provisioning/credential rotation as documented by Module 04C-5B. Verify the dedicated disposable Maven schema/account and the Android/Admin schema/account without exposing secrets.
2. Reduce the Android runtime account from broad schema privileges to the approved minimum before starting it. Recheck denied cross-schema access and ensure persistent `bingo_qa` remains untouched.
3. Confirm current configuration guards, profiles, schema targets, and financial defaults. Keep Flyway clean limited to the disposable Maven schema.

## Gate 1 — Fixture and product decisions

1. Obtain Product/Security/DBA approval for the nonfinancial, synthetic owned-ticket fixture design in [Module 04D-5B](modules/module-04d5b-ticket-fixture-design.md). Keep the mechanism non-public and explicitly opted in.
2. Resolve ticket eligibility/cutoff, CANCELLED and CLOSED claim rules, draw retry/idempotency contract, and fixture retention/cleanup policy.
3. Keep wallet credit, prize calculation, settlement and external payments disabled pending Finance/accounting approval.

## Gate 2 — Claim E2E

After gates 0–1, implement only the approved fixture mechanism and validate its provenance. Run the specific claim matrix scenarios against isolated MySQL, then Android and Admin Chromium. Reconcile claim/audit/ticket/session rows and prove wallet/settlement state unchanged. Do not use full Maven lifecycle until the destructive-suite target and guards are verified; defer clean/recovery tests to the disposable schema.

## Gate 3 — Close existing business-module gaps

Prioritize the existing Module 02 transfer detail/policy and schedule/prize decisions; Module 04B purchase UI/accounting; Module 04C draw retry semantics and history integrity; then remaining player/agent support, reporting, notification, website and release journeys. Preserve every module's stated scope and completion criteria.

## Gate 4 — Production readiness

Only after product, security, finance, operations and release approvals: implement approved settlement/funding behavior, external integrations, operational monitoring and backup/restore evidence. Production authorization is separate from QA completion.

## Stop conditions

Stop a workflow if the active profile/schema/account is uncertain, a guard allows a persistent schema, fixture creation invokes wallet/float/commission/settlement behavior, unexplained existing rows change, or a required policy is unresolved. Record the result as BLOCKED or PARTIAL; do not repair state by deleting historical records.
