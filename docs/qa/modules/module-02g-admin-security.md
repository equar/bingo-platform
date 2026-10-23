# Module 02G — Admin identity, role security and support closure

## Decision

Module 02G is **VERIFIED — SCOPE CLOSED**. It closes the Module 02F identity, finance-revocation, PIN-management, and SUPPORT-mutation gaps with dedicated MySQL integration cases and real Chromium actions. The browser suite is in [module-02g-test-matrix.md](module-02g-test-matrix.md). The original Module 02F results remain historical in [the 44-row master matrix](module-02f-master-coverage.md); this report adds follow-up evidence without changing its original counts.

Module 02 remains **INCOMPLETE**. Wallet-transfer detail remains NOT RUN in 02C scope. Scheduling semantics and prize settlement remain BLOCKED pending Product and Finance decisions. Production financial posting remains disabled pending approved Finance, accounting, bank-evidence, and compliance controls.

## Scope and environment

QA admin: `http://localhost:5174`; QA backend: `http://localhost:8081`; persistent MySQL schema: `bingo_qa`; integration-test schema: `bingo_qa_test`; MySQL 8.0.36. No Docker, H2, or Testcontainers were used. Integration tests were guarded to `bingo_qa_test`; final `bingo_qa` reconciliation used SELECT-only SQL. No original support tickets, agent profiles, deposits, wallets, balances, or game rows were changed.

## Module 02F gap mapping and outcome

The source matrix contains seven NOT RUN row entries representing six unique operational scenarios because the two PIN rows duplicate the same action. It contains two BLOCKED scenarios. Four unique Module 02G scenarios now have browser and database evidence: admin/support role transition, finance-role revocation, agent PIN management, and SUPPORT ticket mutation. The duplicate PIN row shares the PIN evidence. Wallet-transfer detail belongs to 02C and remains NOT RUN. Scheduling semantics and prize settlement remain BLOCKED.

| Module 02F scenario | Route / endpoint | Role and expected behavior | Risk / fixture | Module 02G outcome |
| --- | --- | --- | --- | --- |
| Edit admin/support role | `/admin-users`; `PUT /api/v1/admin/users/{userId}` | ADMIN changes a synthetic SUPPORT account to ADMIN; persisted roles and audit identify actor and target | Privilege escalation; dedicated synthetic user | PASS — Chromium, response, read-only MySQL, refresh, screenshot, and stale-token backend coverage |
| Revoke finance roles | `/admin-users`; `DELETE /api/v1/admin/users/{userId}/finance-roles?role=…` | ADMIN removes FINANCE_VERIFIER; current user roles are reloaded server-side on every request | Stale financial privilege; dedicated synthetic SUPPORT + finance role | PASS — Chromium revocation and persistence; backend proves an existing JWT loses finance access and audit is recorded |
| Set agent PIN | `/agents/{agentId}`; `POST /api/v1/admin/users/{userId}/agent-pin` | ADMIN sets a 4–8 digit PIN for an approved synthetic agent; response and audit do not disclose the secret | Agent impersonation; synthetic Agent 5 | PASS — masked Chromium input, MySQL one-way hash, audit redaction, refresh, backend authorization/invalidation coverage |
| Support ticket mutations as SUPPORT | `/support/tickets/{ticketId}`; assign, messages, and status APIs | SUPPORT assigns to active ADMIN/SUPPORT staff, posts a public reply, and closes a ticket with summary; unauthorized PLAYER mutation is denied | Cross-user exposure and invalid transitions; dedicated synthetic tickets | PASS — Chromium assignment/status/reply, 403 denial, database state and audit checks, refresh, screenshots |
| Agent PIN administration (duplicate inventory row) | Same endpoint and behavior as “Set agent PIN” | Same role and behavior | Duplicate row, no separate fixture | PASS — covered by the same PIN test; not counted as an additional unique scenario |
| Wallet transfer history/detail | `/wallet/transfers`; `GET /api/v1/admin/wallet/transfers/{id}` | ADMIN reads an existing transfer detail | Cross-account data exposure; no approved synthetic transfer fixture | NOT RUN — remains owned by 02C and was not part of this 02G run |
| Schedule semantics | `/sessions`; session scheduling API | ADMIN schedules according to approved recurrence, timezone, DST, and past-time rules | Incorrect session times; no approved policy | BLOCKED — Product must define scheduling semantics |
| Prize settlement | `/prize-entitlements`; `POST /api/v1/admin/prize-entitlements/{id}/settle` | ADMIN posts an approved payout under an approved settlement policy | Irreversible payout; no approved payout/accounting policy | BLOCKED — fail-closed behavior remains; Finance/Product approval required |

## Supported authorization decisions

- Current roles are `ADMIN`, `SUPPORT`, `PLAYER`, `AGENT`, `FINANCE_VERIFIER`, and `FINANCE_APPROVER`. There is no `SUPER_ADMIN` role or separate last-superadmin invariant in this model; no role was invented for testing.
- Only ADMIN can change admin/support roles, grant or revoke finance roles, and set agent PINs. The update API accepts only ADMIN/SUPPORT roles; clients cannot set AGENT or finance roles through the generic user update contract.
- Authentication filters reload the current user and roles from MySQL for each request. Existing JWTs therefore immediately lose removed permissions and are denied when the account is suspended. The browser also refreshes current profile roles before rendering protected pages.
- Agent app registration and login use phone number plus PIN. The Flutter login form contains exactly those two fields and serializes `{phoneNumber, pin}`. No app password is collected or sent; password login through the shared player endpoint is denied for AGENT accounts. The user table still has a required password-hash column, so application registration stores an internal random opaque credential hash that is never provided to or used by the agent app.
- Agent PINs are one-way hashed with the dedicated agent PIN encoder. Responses omit the PIN; audit metadata redacts it. Login throttling remains on the phone/PIN agent login path. No PIN delivery or recovery rule was introduced.
- SUPPORT may view the staff queue and perform supported assignment, public reply, internal-note, and status operations. Assignment targets must be ACTIVE ADMIN or SUPPORT accounts. Player ticket reads remain restricted to the owning requester. CLOSED tickets reject status changes; the 409 terminal-state response and audit outcome are covered by backend integration tests.
- A failed mutation is audited as a failure; successful-mutation assertions filter for `metadata_json.outcome = SUCCESS` so rejection records do not look like duplicate successful actions.

## Defects and fixes

1. Added a governed admin-portal control to revoke finance roles and a dedicated masked PIN-management card on approved agent details.
2. Admin/support role updates could leave cached browser roles stale after reload. Protected routes now refresh the current profile from the server before granting access.
3. SUPPORT users could access the queue but had no verified eligible-assignee list. Added the active ADMIN/SUPPORT assignee endpoint and wired the support UI to it.
4. Support assignment previously accepted ineligible accounts, and CLOSED tickets could be reopened through an unvalidated transition. Assignment now rejects inactive/non-staff users; CLOSED is terminal and rejected transitions are audited.
5. Updated the checked-in OpenAPI contract to match current API routes and schemas. The first full backend run found this documentation mismatch; the contract test passes after synchronization.
6. Corrected integration-test assumptions for audit action names/outcomes, cross-user ownership fixtures, public-message ordering, and PIN form validation. These were test-fixture/assertion defects, not production behavior changes.
7. Added user-row locking to concurrent admin-role/finance-role changes. Updated the service unit fixtures to use the locking lookup and corrected integration cleanup to delete wallet adjustment journal children before their parent requests and users.
8. Stabilized the Android login scenario by verifying emulator API reachability and resetting the account throttle before the successful UI sign-in assertion. The agent app still submits only phone number and PIN.

## Final verification

Exact results are recorded in [the test matrix](module-02g-test-matrix.md) and the evidence files in `../evidence/`.

| Suite | Result |
| --- | --- |
| Chromium 02G | 6/6 passed in real Chromium; screenshots retained |
| Backend focused security/support/OpenAPI and admin-user role service | 28/28 passed against `bingo_qa_test` (22 integration/contract + 6 service tests) |
| Full backend | 130/130 passed, 0 failures, 0 errors, 0 skipped against `bingo_qa_test` |
| Frontend unit tests | 81/81 passed |
| Frontend lint | Passed |
| Frontend production build | Passed |
| Android agent authentication | 1/1 Flutter integration test passed on `emulator-5554`; agent login uses phone number and PIN only |
| Persistent QA database | SELECT-only; original agents 1–3, tickets 1–5, claims 1–7, and Agent 3 ETB 51.00 available / ETB 0.00 clearing verified unchanged; synthetic identities and tickets retained (see database evidence) |

## Remaining work and restrictions

- 02C wallet-transfer detail remains NOT RUN because no safe transfer fixture was approved.
- Product owns the scheduling interpretation required to close the schedule BLOCKED row.
- Finance/Product own payout policy and approvals required to close prize settlement; the API remains fail-closed.
- Production deposit, float, and wallet-adjustment posting remains disabled pending approved bank evidence, accounting/reversal policy, and Finance/compliance approval.
- Module 02 overall remains INCOMPLETE. This work stops at Module 02G; no Module 03 or gameplay work was started.
