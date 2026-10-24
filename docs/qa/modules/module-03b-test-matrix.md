# Module 03B — Player Authentication Test Matrix

Execution date: 2026-10-24 (Africa/Nairobi). Detailed outcomes are recorded separately from source inspection. A scenario remains NOT RUN until its requested execution evidence is present.

| ID | Requirement | Android screen | Backend endpoint | Expected behavior | Actual result | Status | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| L01 | Login screen opens | Login | — | Phone and PIN fields with sign-in action | Real emulator login screen opened. | PASS | `../evidence/module-03b-login-success.png` |
| L02 | Valid phone and PIN | Login | `POST /api/v1/auth/login` | 200 and authenticated player session | Real Android UI authenticated the retained Module 03A player. | PASS | Android results; login screenshot |
| L03 | Incorrect PIN | Login | `POST /api/v1/auth/login` | Generic 401; no session | Android UI showed `AUTH_INVALID_CREDENTIALS`, then accepted the correct PIN. | PASS | Android results |
| L04 | Unknown phone | Login | `POST /api/v1/auth/login` | Same generic 401 as wrong PIN | Real MySQL integration compares response code/message for unknown phone and wrong PIN. | PASS | Backend results |
| L05 | Empty phone | Login | `POST /api/v1/auth/login` | Client validation; no request | Required phone validation shown in real Android UI. | PASS | Android results |
| L06 | Empty PIN | Login | `POST /api/v1/auth/login` | Client validation; no request | Required PIN validation shown in real Android UI. | PASS | Android results |
| L07 | Invalid Ethiopian phone | Login | `POST /api/v1/auth/login` | Client rejects malformed phone | Real Android UI rejected a phone containing letters. | PASS | Android results |
| L08 | Phone normalization matches registration | Login | `POST /api/v1/auth/login` | Supported formatted/country-code phone maps to same account | Real UI login succeeded with a formatted `+251` phone; backend canonicalization is shared with registration. | PASS | Android results; backend results |
| L09 | PIN obscured | Login | — | PIN characters masked | Integration test inspects the actual Android `TextField`; screenshot displays masked characters. | PASS | Android login screenshot |
| L10 | Permitted response fields only | — | `POST /api/v1/auth/login` | Access token, type, expiry and profile; no PIN/hash/refresh token | Integration test asserts token type/expiry and absence of password/hash/refresh fields. | PASS | Backend results |
| L11 | Valid session created | Home shell | `POST /api/v1/auth/login` | Session stored and authenticated UI opens | Login response persisted and home navigation appeared. | PASS | Android results |
| L12 | Protected player endpoint | Home/profile | `GET /api/v1/me` | Valid player bearer token receives 200 | Real MySQL auth test uses login JWT for `/api/v1/me`; role permissions are tested separately. | PASS | Backend results |
| L13 | Missing token | — | `GET /api/v1/me` | 401 | Real MySQL integration test returns 401. | PASS | Backend results |
| L14 | Malformed token | — | `GET /api/v1/me` | 401 without server error | Real MySQL integration test rejects missing bearer value and malformed JWT. | PASS | Backend results |
| L15 | Expired token | — | `GET /api/v1/me` | 401 | Signed expired JWT is rejected by the integration test. | PASS | Backend results |
| L16 | Modified/tampered token | — | `GET /api/v1/me` | 401 | Wrong-signature and changed-claims tokens are rejected. | PASS | Backend results |
| L17 | Logout | Profile | Local secure storage | Token/profile deleted; login screen returns | Actual Android logout cleared the stored token. This is local logout only. | PASS | Android restart/session screenshot; results |
| L18 | Back navigation after logout | Login | — | Protected shell cannot be reopened | Android integration called back navigation after logout and remained unauthenticated. | PASS | Android results |
| L19 | App restart policy | Auth gate | Local restore; then protected API | Valid unexpired local session restores after force-stop | Real force-stop and relaunch restored the saved session before logout. | PASS | Android results; restored-session screenshot |
| L20 | Suspended player | Login | `POST /api/v1/auth/login` | Generic 401 | Disposable MySQL fixture with SUSPENDED status is denied; PENDING player was denied in actual UI. | PASS | Backend and Android results |
| L21 | Inactive/restricted status | Login | `POST /api/v1/auth/login` | PENDING/DISABLED accounts denied | Existing MySQL status integration verifies PENDING and DISABLED; PENDING UI scenario passed. | PASS | Backend and Android results |
| L22 | Non-player role | Login | `POST /api/v1/auth/login` | ADMIN and AGENT cannot use player login | MySQL integration verifies both roles receive generic 401; player endpoint now requires PLAYER. | PASS | Backend results |
| L23 | Repeated incorrect PIN | Login | `POST /api/v1/auth/login` | Temporary limit/lockout; no permanent account lock | Real Android UI submitted six incorrect attempts and remained unauthenticated. The MySQL integration verifies the temporary HTTP 429 / `AUTH_RATE_LIMITED` response, serialized counters, and temporary recovery. | PASS | Android results; backend results |
| L24 | No phone enumeration | Login | `POST /api/v1/auth/login` | Unknown phone and incorrect PIN have identical status/code/message | Real MySQL integration assertion compares the generic response. | PASS | Backend results |
| L25 | Concurrent login failures | — | `POST /api/v1/auth/login` | No lost attempt updates or throttle bypass | Real MySQL concurrent invalid login requests return only 401/429 and remain throttled. | PASS | Backend results |
| L26 | Authentication audit secrecy | — | Login audit | PIN, phone, and JWT excluded/redacted | Successful and failed login audit metadata checks found no PIN or JWT. | PASS | Backend results |
| L27 | Network failure | Login | `POST /api/v1/auth/login` | Retryable error; remain signed out | Real Android UI against an intentionally unreachable endpoint displayed `HTTP_0`, retained the login action, and opened no authenticated shell. | PASS | Android results |
| L28 | Login retry has no duplicate account/wallet | Login | `POST /api/v1/auth/login` | Failed requests leave users/wallets unchanged | The MySQL integration verifies failed login leaves account/wallet rows unchanged. Read-only QA reconciliation confirms players 178/179 and balances are intact and the four synthetic lockout phone identifiers created no user or wallet. | PASS | Backend results; `../evidence/module-03b-database-readonly.txt` |

## Execution boundary

Maven tests use the guarded `bingo_qa_test` MySQL 8.0.36 catalog. Android tests run `com.example.bingo_app` on emulator-5554 against `10.0.2.2:8081`. Persistent `bingo_qa` is queried only with SELECT statements for reconciliation. No 03C–03E work or Module 02 financial-policy work is part of this module.
