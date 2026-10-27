# Mandatory Real E2E Testing Policy

**Effective:** 2026-10-26  
**Applies to:** All current and future Bingo Platform functional QA modules, including the remaining scope of Module 06A and all subsequent functional modules.

## Completion standard

Database-free unit, service, widget, analyzer, static, and mocked API checks remain useful supplemental evidence. They cannot alone establish functional module completion. Every applicable workflow must be validated end to end using the actual Spring Boot API and an authorized, isolated MySQL QA schema, with real Flutter Android player/agent UI or real Admin Chromium interaction where that client applies.

For each applicable scenario, collect evidence for:

1. Actual UI interaction and user-visible result.
2. Requests handled by the real Spring Boot backend.
3. Real persistence in the authorized isolated MySQL QA schema.
4. Read-only reconciliation of expected persisted records and relevant audit records.
5. Negative validation, retry and duplicate behavior, authorization, and state transitions where applicable.
6. Unchanged unrelated financial and historical records, including proof that no unintended financial mutation occurred.

Use emulator `emulator-5554` for Android when applicable and actual Chromium interaction for Admin workflows. Retain command logs, test counts, screenshots or browser evidence, sanitized API results, and read-only reconciliation output with the module report. Never expose secrets in retained evidence.

## Status labels

- **COMPLETE** — All required real E2E scenarios for the defined scope passed, including UI/API/MySQL verification and reconciliation, negative and security cases, and no unintended financial mutation.
- **PARTIAL** — Some required real E2E scenarios passed, but required coverage remains.
- **BLOCKED** — Required real E2E could not execute safely, including an isolation, privilege, fixture, approval, environment, or unresolved-policy gate.
- **NOT STARTED** — No relevant execution evidence exists.

Supplemental unit or mocked checks may be reported alongside these statuses, but do not raise a module to COMPLETE. Preserve historical evidence and previously justified status labels; do not retroactively rewrite prior outcomes. Apply this completion bar to remaining work and when a module is next assessed for closure.

## Database and financial safety gates

- Use only a verified, authorized isolated QA schema. Record the target and preflight evidence without printing credentials.
- Do not use development schema `bingo`, modify persistent `bingo_qa`, or run destructive Maven suites against `bingo_qa_test`.
- **Local QA privilege exception (owner authorized 2026-10-26):** `ALL PRIVILEGES` scoped only to local `bingo_qa_test` is accepted for functional QA. Do not modify local MySQL grants or block local E2E solely because of this grant. Confirm the effective account and schema before a run, and ensure the account has no global privileges beyond `USAGE` and no grants on `bingo`, `bingo_qa`, or other schemas. This exception does not apply to production or shared environments, which must use least-privilege identities.
- **Local root exception (owner authorized 2026-10-26 for this machine's Android E2E setup):** the local MySQL `root` account may be used for the `android-test` backend after secure local password entry. This exception permits root's global privileges only in this local environment; do not change grants, persist credentials in project files, use the account for production/shared environments, or point the backend at any schema except `bingo_qa_test`. Verify the selected catalog, local MySQL server port, and runtime identity before use. For non-root accounts, retain the schema-scope grant checks above.
- Stop and classify the affected scenario BLOCKED if schema identity or isolation is uncertain, regardless of the local privilege exception.
- Never run Flyway clean against persistent databases. Limit any explicitly authorized destructive recovery operation to its verified disposable target.
- Do not create unauthorized synthetic tickets or fixtures. Follow the approved Module 04D-5B fixture design only after its Product, Security, and DBA prerequisites are met.
- Keep financial posting disabled unless the required Finance/accounting approvals and isolated execution controls are in place. Do not create wallet/float movements, prize postings, settlement, or external payments merely to satisfy coverage.
- Capture read-only before/after reconciliation for relevant business state and unrelated financial/historical records. Investigate and stop on unexpected changes; do not delete or reverse historical data to restore a baseline.

## Required response to a blocked scenario

Stop that scenario, preserve the schema and records, report the exact gate and evidence, and classify it BLOCKED or PARTIAL according to remaining scope. Do not substitute mocks or database-free tests for required real persistence and report success. Database-free checks should still be used where safe and useful, clearly labeled as supplemental.

## Application to current QA

Module 06A remains INCOMPLETE until applicable real E2E scenarios meet this policy or are explicitly scoped and their blockers recorded; its existing database-free evidence remains supplemental. The local schema-scoped `bingo_qa_test` grant and this machine's owner-authorized local-root testing exception are not E2E blockers when exact runtime identity and target checks pass. Historical module results remain unchanged. Remaining work must resolve only scenario-specific fixture, financial, policy, and execution-environment blockers before each safe scenario. Module 04E remains NOT STARTED and is not authorized by this policy.
