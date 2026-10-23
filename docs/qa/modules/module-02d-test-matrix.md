# Module 02D — Player, wallet, deposit, and support admin test matrix

## Scope and evidence

This matrix records the current Module 02D financial-security closure evidence. QA was migrated additively to V20 without changing the V19 checksum or resetting data. Browser fixtures were created through the supported application workflows and retained. Backend integration tests use only the restricted `bingo_qa_test` schema.

PASS means the described action and persistence check completed. BLOCKED means a required business policy prevents production enablement. N/A means the capability is absent. Production deposit approval and wallet adjustment remain blocked pending Finance approval.

## Scenario matrix

| Area | Scenario | Result | Evidence / notes |
|---|---|---|---|
| Player roster | Listing, search, status filter, and pagination | PASS | Live Chromium; pagination uses retained data counts; execution JSON |
| Player detail | Profile, status, wallet balance, and transaction history | PASS | Live Chromium; dedicated synthetic players |
| Player lifecycle | ACTIVE → PENDING, denied login/wallet access, PENDING → ACTIVE | PASS | Browser action, HTTP response, MySQL readback, browser refresh |
| Player restriction | ACTIVE → SUSPENDED, old token rejected, new login rejected | PASS | Dedicated synthetic player; SUSPENDED state retained |
| Player validation/security | Invalid status enum, anonymous request, player/agent admin read | PASS | HTTP 400, 401, and 403 responses respectively |
| Wallet listing | Wallet balance/history within player detail | PASS | The product has no standalone admin wallet-list route; existing player detail surface was exercised |
| Wallet administration | Historical single-step admin adjustment | PASS — HISTORICAL | Eight prior ETB 1.25 QA credits remain intact; these predate and do not verify the new maker-checker flow. |
| Wallet adjustment controls | Maker request, separate checker, negative balance guard, unique posting | PASS — QA SIMULATION | Chromium request/approval/replay/history; latest journal #6 and wallet transaction #17 reconciled. Production posting remains blocked. |
| Wallet security | Unauthorized player adjustment; invalid zero amount; nonexistent wallet owner | PASS | HTTP 403, 400, and 404; no extra transaction |
| Wallet audit | `ADJUST_PLAYER_WALLET` event | PASS after audit-prefix fix | Audit API and `audit_log_entries`; regression test in `AuditLogAspectTest` |
| Wallet filters | Filter transaction history by type/date/status | N/A — not implemented | The admin player-activity endpoint and transaction dialog expose pagination/history, but no transaction filter UI or API parameters were found. |
| Deposit submission/review | Synthetic claim visible, rejected from browser, replayed, refresh persists | PASS | Rejected claim; no wallet credit |
| Deposit details | Claim reference, player, cash amount, calculated credit, status and review note | PASS / NO DETAIL ROUTE | The information is shown on the review list row; no separate deposit detail page or endpoint exists. |
| Deposit verification | Eligible receiving account, shared bank-statement evidence, duplicate reference, amount/currency/date/settlement checks | PASS — SYNTHETIC QA | Independent finance verifier matched shared statement evidence; duplicate reference and amount mismatch rejected. |
| Deposit approval | Verified-only approval, verifier/approver separation, balanced simulated posting | PASS — SYNTHETIC QA | Verified deposits posted once; posting response and SQL identify distinct verifier/approver and balanced ETB lines. |
| Deposit security | Unverified approval denial, retries, concurrent approval, rejection, invalid transition | PASS | Unverified/rejected claims remained uncredited; retry and concurrent response returned 200 with one journal per claim. |
| Support queue | Listing, search, pagination, details, assignment, public reply, private note, close, refresh | PASS | Synthetic tickets; existing five open tickets preserved |
| Support validation/security | Player assignee, close without summary, anonymous detail, cross-account detail, player/agent admin reply | PASS | HTTP 400/401/403 as applicable; cross-account response contains no subject |
| Support audit | Assignment, reply, note, status transition | PASS | Admin audit API; retained SQL rows |
| Frontend regression | Current unit tests, lint, production build | PASS | **78/78** tests; `npm run lint` and `npm run build` exited 0. |
| Backend compile | Main and test source compilation | PASS | `mvn -q -DskipTests test-compile` exited 0. |
| Backend audit unit test | Audit success/failure recording and sanitization | PASS | `AuditLogAspectTest`: 4/4. |
| Backend regression | Full integration suite against `bingo_qa_test` | PASS | **110/110**, 0 failures, 0 errors, 0 skipped; suite migrated the isolated schema through V20. |
| Deposit and adjustment integration | `WalletIntegrationTest` | PASS | **9/9**; includes duplicate statement reference, amount mismatch, atomic/idempotent posting, and maker/checker cases. |
| Production simulation guard | Production-profile and disabled-posting checks | PASS | **2/2** focused tests; no database connection opened. |
| Database migration/reconciliation | V19 checksum validation, additive V20, read-only journals/balances/audits | PASS | QA and test schemas are V20. Original claims #1–7 unchanged, no journal/credit; Agent #3's ETB 51.00 float and two balanced journals preserved. |
| Chromium D01–D20 | Deposit and adjustment end-to-end scenarios below | PASS | Real Chromium at QA admin/backend; run `69021617`, **1/1** spec passed, with no page errors, console errors, or failed requests. |
| Android | Flutter agent/player E2E | NOT RUN | Module 02D has no mobile implementation or mobile workflow; scope is the admin portal and backend. |

## Evidence

- [Current Chromium execution details](../evidence/module-02d-execution.json) (real Chromium E2E 1/1; includes D01–D20 identifiers and outcomes)
- Screenshots: `module-02d-player-pending.png`, `module-02d-player-approved.png`, `module-02d-player-suspended.png`, `module-02d-wallet-adjustment.png`, `module-02d-deposit-verified.png`, and `module-02d-support-closed.png`
- [Current read-only reconciliation and financial evidence](module-02d-financial-security.md) and persistent identifiers in [the test-data registry](../test-data-registry.md)
- Focused/full backend Maven reports in `bingo-backend/target/surefire-reports`

## Required Chromium scenarios for the current change

| ID | Scenario | Result |
|---|---|---|
| D01 | Pending deposit visible | PASS |
| D02 | Unverified deposit approval denied | PASS — HTTP 409 |
| D03 | Wallet unchanged after denial | PASS — ETB 0.00 |
| D04 | Finance reviewer independently verifies synthetic statement | PASS — matched, explicitly synthetic |
| D05 | Duplicate bank reference rejected | PASS — HTTP 409 |
| D06 | Amount mismatch rejected and claim moved to exception | PASS — no credit |
| D07 | Unauthorized finance approver and player verification denied | PASS — HTTP 403 |
| D08 | Verified deposit approval | PASS — balanced journal and wallet credit |
| D09 | Idempotent approval retry | PASS — same operation/journal |
| D10 | Concurrent approval protection | PASS — both responses HTTP 200, one journal |
| D11 | Rejection leaves wallet unchanged | PASS |
| D12 | Deposit history survives browser refresh | PASS |
| D13 | Journal matches wallet credit | PASS — read-only SQL |
| D14 | Audit identifies verifier and approver | PASS — audit and posting records reconciled |
| D15 | Invalid state transition rejected | PASS — HTTP 409 |
| D16 | Wallet adjustment maker-checker | PASS — separate identities; one QA-simulated posting and idempotent retry, production remains gated |
| D17 | Unauthorized adjustment/checker denied | PASS — HTTP 403; legacy single-step HTTP 409 |
| D18 | Adjustment history survives refresh | PASS — reason, reference, checker note visible |
| D19 | Search/filter where supported | PASS — deposit, player, and support workflows |
| D20 | Network/API failure handling | PASS — synthetic HTTP 503 shown as an alert |

## Remaining requirement

Finance must still approve evidence sourcing, settlement/reversal handling, accounting treatment, and adjustment policy before production posting can be enabled. Module 02D is **TECHNICALLY VERIFIED — PRODUCTION FINANCIAL POSTING BLOCKED**; Module 02 overall remains INCOMPLETE. No later module was started.
