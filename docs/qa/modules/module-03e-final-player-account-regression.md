# Module 03E — Final Player Account Regression and Coverage Audit

## Decision

**Module 03 can close for its defined approved QA scope.** The 03A–03D scenario inventory is reconciled in the [master coverage matrix](module-03e-master-coverage.md). No mandatory approved-scope scenario remains failed or unrun. Technical account creation, authentication, session handling, read-only profile access, ETB zero-balance wallet initialization, and disabled/zero reward behavior are verified.

This is a scoped QA closure, not production readiness. Two reward scenarios remain `BLOCKED` pending Product/Finance authorization and accounting design: B05 persistent positive reward posting and B14 a balanced signup reward journal. Editable profiles remain an explicit `OPEN PRODUCT GAP` pending a product scope decision. Positive posting remains disabled in persistent QA. Module 02 remains `INCOMPLETE`.

## Execution performed in this audit

Historical 03A–03D counts are preserved in their reports and are not presented here as newly run. This audit independently reviewed all four reports/matrices, status and data registry, relevant app/backend/admin source and automated tests, then ran the following current checks on 2026-10-24:

- Backend full suite against guarded MySQL `bingo_qa_test`: **145/145 passed**, no failures/errors/skips after the authorization repair below.
- Focused registration/authentication/profile/reward classes against `bingo_qa_test`: **49/49 passed**, no failures/errors/skips after the authorization repair. Classes: `AuthSecurityIntegrationTest`, `AuthenticationAttemptThrottleIntegrationTest`, `EthiopianPhoneNormalizerTest`, `SignupRewardPostingGuardIntegrationTest`, and `WalletIntegrationTest`.
- Flutter player app: analyzer clean; unit/widget tests **21/21 passed**.
- Admin frontend: **82/82** tests across 26 files; lint and production build passed.
- Real Chromium at `localhost:5174` against the QA backend: **1/1 passed**. The check read current policy, history and grants, then reloaded unchanged settings. Anonymous settings read returned 401. No positive policy was submitted.
- Real Android UI on `emulator-5554`: new-account journey **1/1**, session preparation **1/1**, force-stop/session restore **1/1**, and restricted-player login denial **1/1** passed. Android tests used `10.0.2.2:8081` and did not uninstall or clear app data.
- Read-only `bingo_qa` reconciliation: restricted-player fixture #6 remains PENDING; players #178/#179/#180 remain ACTIVE; new player #181 is ACTIVE PLAYER with one ETB wallet at 0.00 available / 0.00 promotional, no grant and no wallet transaction. All seven reward policies are still disabled, expired, zero ETB. User count is 179 and wallet count 121; grants remain 0 and wallet transactions remain 19. Agent #3 retains ETB 51.00 available / ETB 0.00 clearing, two float transactions and two journals. All 19 historical deposit claims and five deposit journals remain present. No profile-update audit row exists for #181. Reconciliation SQL is SELECT-only.

Detailed logs are [backend](../evidence/module-03e-backend-results.txt), [Flutter](../evidence/module-03e-flutter-results.txt), [Android](../evidence/module-03e-android-results.txt), [admin/Chromium](../evidence/module-03e-admin-results.txt), and [database SQL/output](../evidence/module-03e-database-readonly.sql) / [output](../evidence/module-03e-database-readonly.txt).

## Android journey results

| Journey ID | Check | Result | Evidence |
| --- | --- | --- | --- |
| J01 | Register a new synthetic player in the Android app | PASS — user #181 created and retained | Android results; database reconciliation |
| J02 | Confirm new account status | PASS — ACTIVE PLAYER | Android results; database reconciliation |
| J03 | Verify ETB 0.00 wallet initialization | PASS — available and promotional balances both 0.00 | Android results; database reconciliation |
| J04 | Verify no signup grant or wallet transaction | PASS — no grant or transaction for #181 | Database reconciliation |
| J05 | Log out locally | PASS — login screen returned | Android results |
| J06 | Log in using registered phone and PIN | PASS | Android results |
| J07 | Open player profile | PASS | Android results; profile screenshot |
| J08 | Verify profile identity | PASS — display name, phone and PLAYER role match #181 | Android results; profile screenshot |
| J09 | Refresh profile data | PASS — server refresh completed | Android results |
| J10 | Restart application | PASS — Android process force-stopped without clearing data or uninstalling | Android results |
| J11 | Verify documented session restore | PASS — same authenticated profile restored from secure local storage | Android results; restore screenshot |
| J12 | Log out after restore | PASS — secure token removed and login screen returned | Android results |
| J13 | Confirm protected screens cannot be reopened locally | PASS — navigation/profile unavailable after logout | Android results |
| J14 | Confirm incorrect PIN is rejected | PASS — generic credential failure and no session | Android results |
| J15 | Deny restricted-player login | PASS — retained synthetic pending player fixture denied; status unchanged | Android results; Module 03B evidence |
| J16 | Confirm no unexpected financial posting | PASS — balances unchanged; no grant, wallet transaction, or policy write | Database reconciliation |

## Security and access review

The initial cross-role review found and reproduced a security defect: an AGENT token could GET the player app's `/api/v1/me` profile endpoint (HTTP 200). The endpoint and its `/api/v1/auth/me` alias matched a broad authenticated/public auth rule rather than a non-agent allowlist. `SecurityConfig` now allows the defined non-agent account roles (PLAYER, ADMIN, SUPPORT, FINANCE_VERIFIER, FINANCE_APPROVER) and denies AGENT for both profile routes. This preserves the existing finance/support self-profile contract. The focused regression verifies AGENT receives 403 and missing/malformed tokens receive 401. The full and focused suites passed after the repair.

The current auth integration tests also confirm player JWTs cannot access admin endpoints (403), agent JWTs cannot access other restricted admin/player routes outside their permitted routes, and player login denies non-player or suspended identities. Registration assigns PLAYER/ACTIVE regardless of client-supplied role/status fields. Principal-bound `GET /api/v1/me` ignores a supplied `userId`, excludes PIN/hash/token fields, and `PUT /api/v1/me` returns 405 without profile or wallet mutation. Missing, malformed, expired, wrong-signature and tampered JWTs are rejected. Login throttling, failed-attempt reset after successful login, generic unknown-phone/wrong-PIN errors, and audit redaction are covered by the focused tests. Persistent reconciliation found no profile-update audit for #181.

The player app has **local-only logout**: it removes the token/profile from secure storage and hides protected screens. The backend has no logout/revocation endpoint, so a copied JWT remains valid until its one-hour expiry unless the account is suspended; protected requests reload roles/status and deny suspended accounts. The configured session lifetime and status check are adequate for the approved QA scope, including local logout and session restoration. They do not provide immediate revocation of a copied token; production token-revocation requirements remain a separate security decision. Login throttling is implemented; registration-specific throttling has not been specified or added.

## Scope classification and closure conditions

| Area | Classification | Closure treatment |
| --- | --- | --- |
| Registration, PIN hashing, phone validation/normalization, duplicate/concurrent handling, rollback and wallet initialization | Existing approved scope | PASS with current full/focused backend regression and retained Android evidence |
| Login, throttling, role/status enforcement, JWT validation and local session behavior | Existing approved scope | PASS; accurately qualified as local-only logout |
| Profile identity, refresh, cache/retry and protected-field rejection | Existing approved view-only scope | PASS |
| Editable profile fields and write/audit/conflict behavior | Unapproved product feature / open product gap | Remains OPEN PRODUCT GAP; prior N/A entries preserved as previous status in the master matrix, never marked PASS |
| Persistent positive signup reward posting | Unapproved production financial dependency | B05 remains BLOCKED; no positive policy or posting was attempted in `bingo_qa` |
| Signup-reward double-entry journal | Unapproved accounting dependency | B14 remains BLOCKED; no journal was invented |
| Future gameplay or Module 04 requirements | Future module | Not started; Module 02 remains INCOMPLETE |

Both confirmed technical defects found in this scope are repaired: the historical 03A concurrent duplicate behavior (one success and one HTTP 500) and the 03E agent-to-player-profile access defect (HTTP 200 instead of 403). Their failing reproductions remain documented, and their repaired regressions passed. The initial strict PLAYER-only repair also exposed and was corrected for an existing Finance self-profile permission. No unresolved technical defect remains in the approved player-account scope. Original failed/interrupted QA attempts and records remain preserved.
