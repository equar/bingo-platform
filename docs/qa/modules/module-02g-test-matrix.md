# Module 02G — Test matrix

All browser cases use real Chromium against `http://localhost:5174` and the QA backend at `http://localhost:8081`. Each mutation is initiated through the browser UI, its HTTP response is asserted, persistent state is queried with read-only MySQL, the page is refreshed, and a screenshot is retained under [evidence](../evidence/). Fixtures are dedicated synthetic records; original rows were not reused.

| ID | Scenario | Browser interaction and response | Persistent assertion / refresh | Evidence | Status |
| --- | --- | --- | --- | --- | --- |
| G01 | Finance-role revocation | Admin opens `/admin-users`, revokes FINANCE_VERIFIER; DELETE returns 200 | Role row disappears, `REVOKE` audit exists, reload retains role removal | `module-02g-finance-revoke.png` | PASS |
| G02 | Admin/support role transition | Admin edits synthetic SUPPORT user to ADMIN; PUT returns 200 | `user_roles` and `UPDATE_USER` audit checked; reload displays ADMIN | `module-02g-role-transition.png` | PASS |
| G03 | Agent PIN management | Admin opens synthetic approved Agent 5 detail, enters PIN into password-masked field; POST returns 200 | PIN hash exists and is not plaintext; `SET_AGENT_PIN` audit contains no PIN; reload does not reveal it | `module-02g-agent-pin.png` | PASS |
| G04 | SUPPORT ticket assignment | SUPPORT opens dedicated ticket, assigns to eligible active SUPPORT staff; POST returns 200 | `assigned_to_user_id`, IN_PROGRESS state, and successful assignment audit checked; reload retains assignee | `module-02g-support-assignment.png` | PASS |
| G05 | SUPPORT reply and status change | SUPPORT posts public reply and closes a separate ticket with resolution summary; POST/PUT return 200 | Public message, CLOSED state, and successful reply/status audits checked; reload retains closed state | `module-02g-support-status.png` | PASS |
| G06 | Unauthorized support mutation | Player token attempts a ticket status change in the Chromium browser context; PUT returns 403 | Ticket remains OPEN in MySQL | `module-02g-support-unauthorized-mutation.png` | PASS |

## Backend security coverage

`AuthSecurityIntegrationTest` and `SupportIntegrationTest` run against the guarded MySQL `bingo_qa_test` database. Coverage includes stale JWT after finance-role revocation, role transitions applied to existing tokens, suspended account denial, unauthorized role elevation, PIN reset authorization, PIN format validation, PIN invalidation, PIN redaction and hashing, support authorization, ineligible assignment denial, public reply visibility, cross-user ticket ownership, closed-ticket invalid transition, successful and failed mutation audits, and rate-limited authentication. The existing integration harness cleans only `bingo_qa_test`.

The original Module 02F 44-row counts remain historical. The duplicate PIN rows share one execution scenario; they are not counted twice in the unique scenario results.

## Results

| Verification | Actual result |
| --- | --- |
| Chromium G01–G06 | 6/6 passed in real Chromium (Playwright); all six screenshots retained |
| Focused backend auth/support/OpenAPI and admin-user role service | 28/28 passed against `bingo_qa_test` (includes the 22 security/support/OpenAPI cases and six `AdminUserServiceTest` cases) |
| Full backend | 130/130 passed, 0 failures, 0 errors, 0 skipped against `bingo_qa_test` |
| Admin frontend unit tests | 81 passed, 0 failed |
| Lint and production build | Both passed |
| Android agent auth integration | 1/1 Flutter integration test passed on `emulator-5554`; phone/PIN login, pending denial, session persistence, and logout checks passed |
| Read-only `bingo_qa` reconciliation | Original profiles 1–3, tickets 1–5, claims 1–7 and Agent 3 float remain unchanged; synthetic users 168–177, agent profiles 5–6 and tickets 48–53 are retained. See [database evidence](../evidence/module-02g-database-reconciliation.txt). |

Android screenshots: [login](../evidence/module-02g-agent-login.png), [authenticated](../evidence/module-02g-agent-authenticated.png), and [pending-agent rejection](../evidence/module-02g-agent-pending-rejected.png). These use phone and PIN only; there is no agent-app password field or password request property.

See the [captured test totals](../evidence/module-02g-test-results.txt) and [read-only database reconciliation](../evidence/module-02g-database-reconciliation.txt).
