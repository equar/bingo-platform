# Local E2E Testing

Functional completion requires the actual client UI, Spring Boot API, and an authorized isolated MySQL schema, with read-only reconciliation where applicable. See the [Mandatory Real E2E Testing Policy](qa/mandatory-real-e2e-testing-policy.md). Unit, widget, analyzer, mocked API, and static checks are useful supplemental checks; they are not E2E verification.

## Current guarded workflows

| Client / layer | Current procedure | Isolation and evidence |
| --- | --- | --- |
| Backend integration tests | Follow [Backend integration tests](../bingo-backend/README.md#integration-tests). | Maven tests use only disposable `bingo_qa_maven_test` with the `BINGO_MAVEN_MYSQL_*` variables and the source guard. Do not use the Android/Admin datasource for Maven tests. |
| Admin Chromium | Start the isolated backend with `.\scripts\qa\Start-AndroidTestBackend.ps1`, then run `.\scripts\qa\Invoke-AdminSessionSchedulingE2E.ps1` from normal VS Code PowerShell. | Uses the real Admin UI, Chromium, Spring API, and local `bingo_qa_test`; checks the target and reconciles before/after MySQL state. It creates and transitions a run-owned session, which is retained as evidence. Supply Admin E2E credentials through the protected local process environment, never as command-line values or logs. See [Module 06A-5](qa/modules/module-06a5-functional-gap-closure.md). |
| Player Android | Start the isolated backend with `.\scripts\qa\Start-AndroidTestBackend.ps1`, then run `.\scripts\qa\Invoke-AndroidPlayerFunctionalE2E.ps1` from normal VS Code PowerShell. | Uses the real app on `emulator-5554`, the isolated `android-test` backend and `bingo_qa_test`. The guarded runner preflights identity, prompts for existing authorized accounts, creates a run-owned OPEN session and nonfinancial card-display fixture, and reconciles database state. Fixtures are retained; do not delete historical records. See [Module 06A-9](qa/modules/module-06a9-android-player-functional-e2e.md). |
| Agent Android | Scenarios are under [`bingo-agent/integration_test`](../bingo-agent/integration_test/) and prior device results are in [Module 02A](qa/modules/module-02a-agent-lifecycle.md) and [Module 02B](qa/modules/module-02b-agent-float.md). | Prior reports are historical evidence, not permission to repeat writes against persistent `bingo_qa`. There is no guarded Agent Android launcher in the repository yet. Before a new run, provision/verify an authorized isolated schema and follow the mandatory policy; report only actual device/API/MySQL evidence as E2E. |

Use [QA Module Status](qa/module-status.md) for the latest recorded outcome. A test being runnable, a health endpoint being UP, or a workflow being prepared does not mean its business scenario passed.

## Database boundaries

- `bingo` is the local development database. Do not use it for functional QA or destructive tests.
- `bingo_qa` is persistent QA data. Preserve it; do not use it for new E2E writes.
- `bingo_qa_test` is the local Android/Admin functional E2E schema under the owner-approved local policy.
- `bingo_qa_maven_test` is the separate disposable schema for guarded Maven integration tests.
- Verify the active profile, JDBC catalog, MySQL host/port, authenticated account, grants and financial flags before any write. Never run Flyway `clean` against persistent databases. Full guard details and local setup are in [QA Local Environment](qa/local-environment.md) and the [mandatory policy](qa/mandatory-real-e2e-testing-policy.md).

Never enable financial posting, create wallet/float movements, settle prizes, or use external payment paths to satisfy coverage without the required Finance/accounting approval and isolated execution controls. Keep ledgers immutable, validate idempotency/replay behavior, and reconcile affected and unrelated financial records read-only.

## Legacy orchestration harness

[`scripts/run-local-e2e.ps1`](../scripts/run-local-e2e.ps1) invokes Admin Playwright phases and `bingo-app/integration_test/local_e2e_test.dart`. It defaults to the development backend at `localhost:8080`, has no schema/account isolation preflight, and the Player phases include registration, deposits, purchase, and claim workflows. The script itself does not start or verify isolated services. Retain it for historical development context, but do not use it against `bingo`, persistent QA, shared or production databases, or treat a Flutter test-harness execution as Android-device E2E. Prefer the guarded procedures above.

## Related references

- [Backend configuration and integration-test safeguards](../bingo-backend/README.md)
- [Admin Playwright setup](../bingo-admin/README.md)
- [Player Android test setup](../bingo-app/README.md)
- [Agent Android test scope](../bingo-agent/README.md)
- [QA documentation index](qa/README.md)
