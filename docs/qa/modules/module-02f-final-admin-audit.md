# Module 02F — Final admin coverage audit

## Decision

**PARTIAL — MANDATORY COVERAGE REMAINS. Module 02 stays INCOMPLETE.** The full operation inventory contains 44 action rows: 32 `PASS`, 6 `NOT RUN`, 2 `BLOCKED`, 3 `DEFERRED`, and 1 `NOT APPLICABLE`. Not-run privileged admin actions include admin/support role transitions, finance-role revocation, agent PIN management, and support-ticket mutation as a SUPPORT user. Several of these have no corresponding UI. Finance-role granting passed through the API, but Product must confirm whether API-only administration is acceptable. Open items remain until implemented and tested or explicitly de-scoped by the product owner. The full route/action details and ownership are in the [master matrix](module-02f-master-coverage.md).

Production financial readiness is tracked separately: simulated deposit, agent-float, and wallet-adjustment controls passed their QA cases, but production posting remains blocked on bank evidence integration, accounting/reversal policy, and Finance approval. Prize settlement remains fail-closed.

## Scope and environment

This audit covers Module 02 admin functionality only. Real Chromium used the QA admin at `http://localhost:5174` and backend at `http://localhost:8081`. Backend tests were guarded to `bingo_qa_test`. Persistent data checks were SELECT-only against `bingo_qa` on MySQL 8.0.36. No Docker, H2, Testcontainers, admin port 5173, or database reset was used. No production financial posting was enabled and no Module 03 work was started.

## Defects found and fixed

1. **Suspended-admin authentication bypass (high severity).** `AdminAuthService.login` did not check account status before issuing a JWT. A suspended synthetic admin received HTTP 200. The service now rejects any non-ACTIVE admin/support account. A backend regression test covers suspended-admin login, and the final Chromium flow confirmed a suspended account gets denied.
2. **Admin account status edit returned HTTP 400.** The user API returns Ethiopian phone numbers normalized as `251…`, while the update DTO accepts a 10-digit local value. The edit form now converts the normalized value to local form before resubmission. A frontend regression verifies the edit field value; real browser status changes, response, refresh, and database state passed.
3. **Incorrect current-password response cleared a valid session.** The shared Axios interceptor treated every 401 as an expired session, including a wrong-current-password response. It now preserves the session for the password-verification endpoint; a unit regression and Chromium test confirm the page/session remain available and the shared QA admin still authenticates.
4. **SUPPORT accounts were blocked by admin-only route/navigation guards.** The UI now allows SUPPORT into support, profile, and password pages, redirects support-only logins to the support inbox, and hides admin navigation. Chromium verified support access and denial of admin-user/dashboard access.
5. **QA activation email origin.** The QA activation link now targets port 5174. Both ADMIN and SUPPORT synthetic activation flows were verified through captured QA email; tests assert port 5173 is absent.

An initial test locator/cleanup mismatch was corrected in the Playwright spec. Interrupted test identities were retained and subsequently suspended through the browser; no user row was deleted.

## Final execution results

- **Chromium:** 5/5 passed, 0 failed, 0 skipped in the final Module 02F spec. Scenario-level outcomes are in [the sanitized result record](../evidence/module-02f-chromium-results.txt). The admin activation screenshot is `../evidence/module-02f-admin-activation-complete.png`.
- **Backend focused regression:** `AdminAuthServiceTest` 12/12 passed, including the new suspended-account regression.
- **Backend full suite:** 124 tests passed, 0 failures, 0 errors, 0 skipped, against `bingo_qa_test`; Maven BUILD SUCCESS. The schema guard selected MySQL 8.0.36 and `bingo_qa_test`. See [backend results](../evidence/module-02f-backend-results.txt).
- **Admin frontend:** 79/79 unit tests passed; ESLint and production build both exited 0.
- **Android/Flutter:** not run. Module 02F made no mobile code or workflow changes; the mobile agent lifecycle evidence remains in Module 02A.
- **Database:** read-only reconciliation passed; see [Module 02F database evidence](../evidence/module-02f-database-reconciliation.txt).

## Financial integrity and retained data

Flyway remains V22. Original agent profiles 1 and 2 remain APPROVED and PENDING. Agent #3 remains APPROVED with ETB 51.00 available and ETB 0.00 clearing; its two float journals total ETB 51.00 debit and credit. Original deposit claims 1–7 remain PENDING/REJECTED as recorded in prior evidence. Deposit, float, and adjustment journal imbalance counts are zero; orphan deposit/float journals and wallet transactions are zero; all wallet balances match the latest ledger entry (or zero for an empty ledger); duplicate deposit/float/adjustment postings and duplicate room/start schedules are zero. There are no prize settlement records. Rooms/sessions remain 33/18; persistent QA has zero tickets, draws, claims, and prize entitlements. Full synthetic admin/support user IDs and lifecycle state are registered in [test-data-registry](../test-data-registry.md). No original player, agent, financial, support-ticket, or game record was modified; the existing QA admin was used only for negative current-password and post-test login checks.

## Remaining gaps and owners

- **Module 02 mandatory coverage:** Add and test an ADMIN/SUPPORT role transition; test finance-role revocation and obtain Product confirmation whether the existing grant API is sufficient without UI; decide whether admin agent-PIN setting is a required UI function and test if required; test support-ticket mutations under SUPPORT role using a dedicated retained ticket fixture. Until resolved, these are `NOT RUN` and Module 02 stays incomplete.
- **Product scheduling approval:** Recurrence, timezone/DST, and past-time scheduling interpretation is undocumented; no conclusion is inferred from future-only synthetic schedules.
- **Module 03:** Live session progression, draws, ticket purchase and claim generation/review/entitlement creation require real gameplay integration and approved operating controls. No draws or claims were fabricated.
- **Product/Finance:** Ticket-bearing cancellation and refunds have no approved refund/void rules. Prize settlement remains disabled pending approved payout/accounting policy. Production deposit, float, and wallet adjustment settlement remain disabled pending real evidence sources and production accounting/reversal controls.
- **Product requirement traceability:** No reporting route or controller exists in the inspected application. Reporting is marked `NOT APPLICABLE` as implemented; a product owner must identify a requirement and owner if reporting is required.

The audit does not mark untested actions PASS, does not mark Module 02 COMPLETE, and stops at Module 02F.
