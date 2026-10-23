# Module 02A — Agent onboarding, approval and suspension

**Date:** 2026-10-23  
**Status:** PASS for the supported agent lifecycle  
**Environment:** Admin `http://localhost:5174`, QA API `http://localhost:8081`, MySQL 8.0.36; integration tests used only `bingo_qa_test`; Android emulator `emulator-5554` (API 37).

## Supported lifecycle

The existing model has three agent statuses: `PENDING`, `APPROVED`, and `SUSPENDED`. The verified transitions are:

```text
application → PENDING → admin approval → APPROVED → admin suspension → SUSPENDED
```

There is no `REJECTED` status or rejection endpoint, and there is no supported reactivation endpoint. Those workflows were not added. A pending application has an `AGENT` role but a `PENDING` account, so it cannot log in or call approved-agent operations. Approval activates the account and creates a zero-balance agent float in the configured currency. Suspension changes both account and agent status to `SUSPENDED`; the authentication filter rechecks the account on every request, so a previously issued JWT is denied.

The existing application flow was absent from both the agent app and backend. The implementation adds one self-service application flow to the agent app and `POST /api/v1/agent/register`; it does not add a parallel admin-created agent mechanism. Required fields are name, Ethiopian mobile phone, password, 4–8 digit PIN, city, territory, identity-document type, and identity-document number. The phone is normalized to `251…`; duplicate phone and duplicate identity-document submissions return conflict. The database also enforces a unique identity-document type/number pair. New applications create no player wallet or agent float.

The admin roster exposes new applications and its detail route displays their identity fields. Approval and suspension are bodyless admin actions with validated state transitions. The generic admin user update rejects AGENT accounts so a caller cannot alter their role or account status through that route. Status changes and failed duplicate decisions are audited.

## Execution results

| Check | Result |
| --- | --- |
| Chromium — admin roster | **1/1 passed.** Both retained agents were found by phone in the portal; `APPROVED` and `SUSPENDED` statuses rendered as recorded. |
| Chromium — approval | **1/1 passed.** Opened both newly submitted applications in the admin browser, checked the first identity detail, approved each through the UI, confirmed status after refresh, and verified successful agent login and dashboard access. Player and agent approval attempts returned 403; duplicate approval and generic user mutation returned 409. |
| Chromium — suspension | **1/1 passed.** Authenticated agent 2 before suspension; suspended it in the admin UI; confirmed the status after refresh and via the admin API. New login, old-token dashboard, old-token float read, and old-token float-transfer mutation returned 401. Anonymous approval returned 401. |
| Flutter Android — application and pending denial | **Passed** on `emulator-5554`. The app submitted both applications through its form. The first pending application could not log in; no token was stored. |
| Flutter Android — approved login | **Passed** for both synthetic applications after browser approval. The authenticated app reached its authorized dashboard. |
| Flutter Android — suspended login | **Passed** after suspension. The agent login was denied and no session token was stored. |
| Flutter analysis | **Passed**, no issues found. |
| Backend integration regression | **24/24 passed** against `bingo_qa_test`: `A00FlywayUpgradeRecoveryIntegrationTest` (1), `AdminReadServiceTest` (2), `AgentServiceIntegrationTest` (7), and `AuthSecurityIntegrationTest` (14). Flyway validated and migrated V1–V15, including the V15 uniqueness constraint. No Docker, Testcontainers, or H2 was used. |
| Admin frontend | **76/76 unit tests passed**; production build and lint passed. |
| Persistent database | Read-only MySQL verification confirms users 11/12 and agent profiles 3/4 in their final states below, zero player wallets, zero ETB float balances, audit rows for both approvals and suspension, original agent profiles 1/2 unchanged, and successful Flyway V15. See [database evidence](../evidence/module-02a-database-verify.txt). |

The Android runner logs have synthetic credentials redacted; sanitized phase results are in [Android evidence](../evidence/module-02a-agent-android-results.json). Screenshots are `module-02a-agent-roster.png`, `module-02a-agent-application-one-submitted.png`, `module-02a-agent-application-two-submitted.png`, `module-02a-agent-pending-login-denied.png`, `module-02a-agent-approved-login.png`, and `module-02a-agent-suspended-login-denied.png`. Browser API/status results are in [approval evidence](../evidence/module-02a-agent-approval-browser.json) and [suspension evidence](../evidence/module-02a-agent-suspension-browser.json).

## Defects fixed

- Added a public registration route while leaving the rest of the agent API protected by the AGENT role.
- Added required application validation, Ethiopian phone normalization, duplicate checks, initial PENDING state, and a database uniqueness constraint for identity documents.
- Replaced the client-controlled approval DTO path with a bodyless approval action that only accepts a pending agent account. Approval creates a zero-balance float.
- Restricted suspension to APPROVED agents and blocked general user edits for agent accounts.
- Fixed disabled-account authentication exceptions returning HTTP 500; pending and suspended login now return HTTP 401.
- Exposed identity-document fields on the admin-only agent detail response and added application and lifecycle controls to the admin portal.
- Added the agent application form to Flutter, with password confirmation, PIN validation, a pending-review acknowledgement, and no automatic sign-in.
- Included the agent profile ID in lifecycle audit target extraction.

## Persistent QA records

| Fixture | IDs | Final state | Financial state |
| --- | --- | --- | --- |
| QA_M02A_AGENT_001 | User 11 / Agent 3 | Account `ACTIVE`, agent `APPROVED` | No player wallet; one zero ETB agent float |
| QA_M02A_AGENT_002 | User 12 / Agent 4 | Account `SUSPENDED`, agent `SUSPENDED` | No player wallet; one zero ETB agent float |

Both records are retained and listed in the [test-data registry](../test-data-registry.md). The original approved and pending agents were not modified. Do not reuse these fixtures, change their terminal scenario state, or store their credentials outside the ignored local test file.

## Remaining gaps and module boundary

- Agent rejection and reactivation are not supported by the current status model/API.
- The app collects identity-document type and number, but does not upload document images; backend front/back references remain optional.
- This completes Module 02A only. Module 02 remains **INCOMPLETE** because its separate float-review queue/balance APIs and remaining mandatory workflows have not been verified. No later module was started.
