# Module 02B — Agent Float Management

**Status: MODULE 02B: TECHNICALLY VERIFIED — PRODUCTION FUNDING BLOCKED.**

This report covers Module 02B only. It does not mark Module 02 complete. Module 02C and Module 03 were not started.

## Supported lifecycle

1. An approved agent submits a positive ETB float request with amount, selected receiving account, transfer reference, and supporting evidence note. It starts `PENDING`; submission never credits the float.
2. An authorized finance verifier compares the request with an independently recorded, settled bank statement entry. Account, exact reference, amount, and currency must match. The verifier and approver must be different users.
3. A distinct finance approver posts a verified request. Posting creates one balanced accounting journal and one float `TOP_UP` transaction in one guarded transaction. Repeating an approved post is idempotent.
4. Rejection and verification exceptions do not credit float. Daily QA reconciliation is read-only and reports date-level statement/posting totals, journal debit/credit totals, unmatched settled statements, verified-unposted requests, verification exceptions, and a statement-to-posting difference.

Finance verifier/approver roles are assigned by an authorized admin to a different eligible account. Lifecycle and financial actions are audited. Receiving account numbers are encrypted at rest and returned masked.

## QA simulation boundary

The QA workflow supports manual bank transfer and independent verification using an explicitly synthetic bank-statement fixture. In this environment the matching entry is marked settled so the complete lifecycle and accounting controls can be tested. This is **not a bank integration or evidence of real funds**. Production configuration hard-disables synthetic statement creation and simulated posting. Production credits remain blocked until Finance/Product approves real statement ingestion, settlement evidence, accounting mappings, reconciliation handling, limits, and reversal/failure policy.

## Implemented

- Flutter agent request form captures a receiving account, transfer reference, and supporting evidence note; request status and existing request history are displayed.
- Backend APIs support receiving-account administration, synthetic QA statement fixtures, independent verification, finance-role assignment, separate approval, posting, and read-only reconciliation.
- Database migrations V16–V18 retain submission details, add encrypted receiving accounts, bank statement entries, verification history, finance permissions, accounting journals and lines, and the expanded lifecycle status.
- Admin UI provides the transfer queue, request detail/history, receiving accounts, QA statement entry, verification, approval, rejection, and reconciliation. QA simulation is labeled in the interface.
- Posting enforces distinct verifier/approver, eligibility and status checks, exact statement matching, idempotency, request/float locking, balanced entries, rollback, and audit logging.

## Execution results

| Check | Result | Evidence |
| --- | --- | --- |
| Real Chromium manual transfer review | 1/1 Playwright spec passed end to end on a fresh pending synthetic request. The browser opened the queue/details, recorded the synthetic independent statement, verified it, denied the verifier's posting attempt with HTTP 403, approved through a separate account, refreshed to confirm APPROVED, repeated the post idempotently, and ran reconciliation. A preceding attempt exposed a stale queue row after posting; the queue refresh fix is covered by this successful fresh-request run. | `evidence/module-02b-funding-verified.png`, `evidence/module-02b-funding-posted.png`, `evidence/module-02b-funding-browser.json` |
| Unauthorized verifier posting | PASS; verifier's post attempt returned HTTP 403 and created no journal or float movement. | Playwright test and audit evidence in `evidence/module-02b-database-verify.txt` |
| Flutter Android E2E | PASS on emulator-5554, API 37. Real UI submitted request 5, then authenticated and displayed the same reference, updated available balance ETB 51.00, a `Float top-up` transaction, and Approved after separate browser approval. Currency order, whole-number formatting, and duplicate matching labels in the history screen were accounted for in the final assertion. | `evidence/module-02b-agent-float-request-submitted-131900.png`, `evidence/module-02b-agent-float-posted-131900.png` |
| Backend integration suite | **105/105 passed**, zero failures/errors/skips, on `bingo_qa_test`. Includes 16 `AgentServiceIntegrationTest` cases, 15 `AuthSecurityIntegrationTest` cases, migration recovery, and OpenAPI contract. An initial 104/105 run found a stale OpenAPI snapshot after the 403 handler change; the snapshot was regenerated from QA and the full suite rerun passed. | [Execution summary](../evidence/module-02b-test-results.txt) |
| Admin frontend | **75/75** Vitest tests passed; ESLint and production build passed. | [Execution summary](../evidence/module-02b-test-results.txt) |
| Flutter unit/static checks | **10/10** unit tests passed; `flutter analyze` reported no issues. | [Execution summary](../evidence/module-02b-test-results.txt) |
| Persistent database verification | Read-only checks against `bingo_qa`: Flyway V18; requests 4 and 5 are APPROVED; synthetic statements 1 and 2 matched; journals 1 and 2 each have two lines and debit/credit totals ETB 25.50; two float transactions moved Agent 3 from ETB 0.00 to ETB 51.00. Verifier user 15 and approver user 16 are distinct. Original agent profiles 1 and 2 remain APPROVED and PENDING. | `evidence/module-02b-database-verify.txt` |

## Defects fixed

- Spring Security `AccessDeniedException` was falling through to HTTP 500. The exception handler now returns HTTP 403 with `ACCESS_DENIED`, covered by a unit test and reflected in the OpenAPI snapshot.
- Admin queue results did not refresh their displayed lifecycle status after a successful post. The browser test now reloads and checks the approved queue.
- Flutter's currency format places `ETB` before the amount; the posted-state test now checks the request row instead of assuming a display order.
- Full-test fixtures now remove the new funding-dependent rows in foreign-key order so integration cleanup remains isolated to `bingo_qa_test`.

## Scenario coverage limits

- The Chromium spec exercises the end-to-end request review, independent synthetic verification, 403 verifier denial, separate approver posting, persisted approved state, idempotent retry, and reconciliation. The earlier Module 02B browser run exercised rejection without credit. Wrong-account, unsettled, duplicate-reference, amount mismatch, concurrent approval, rollback, finance-role assignment, and suspension-after-verification are covered by backend integration tests rather than separate browser journeys.
- Fresh request 5 was submitted from Android and used for the final full pending-to-posted Chromium run. The browser verified that posting was disabled while pending and enabled only after the matched statement. Detailed browser coverage for filter/pagination/empty states and actionable UI error messages remains outstanding.
- Android real-UI E2E covered request submission (earlier run) and viewing the approved synthetic request (posted-state run). Android validation edge cases, rejection display, duplicate/network retry, suspended-agent top-up navigation, and process restart were not replayed in this Module 02B run. Relevant validation, retry, authorization, and suspension controls have backend or Flutter unit coverage where stated above, but that is not equivalent to all requested Android journeys.

## Remaining gaps

- Real production bank statement ingestion/confirmation and regulated payment settlement evidence are not implemented or approved. Synthetic QA data proves only test behavior.
- Production posting stays disabled until finance/accounting, reconciliation, limits, and reversal rules are formally approved and a non-synthetic funding source is integrated. Current reconciliation is date-level totals only; per-account drilldown and itemized discrepancy reporting remain gaps.
- Some enumerated Chromium and Android presentation journeys remain to be executed; see [Scenario coverage limits](#scenario-coverage-limits). Core request, verification, authorization, posting, idempotency, balance, and accounting invariants pass in the executed browser/mobile/backend checks.
- Module 02 overall remains INCOMPLETE until all other mandatory scenarios are verified.



