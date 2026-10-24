# Module 03B — Player Login, PIN Security, and Session Management

## Current outcome

**COMPLETE.** All 28 mandatory scenarios passed across real MySQL integration tests, Flutter checks, and real Android emulator UI tests. Persistent `bingo_qa` reconciliation used SELECT-only SQL. Module 02 remains INCOMPLETE; Module 03C–03E were not started.

## Authentication design

The player authenticates with an Ethiopian phone number and a six-digit numeric PIN. The Flutter form masks the PIN, checks both fields locally, and sends `phoneNumber` plus the DTO property `password` to `POST /api/v1/auth/login`. The server applies the same phone normalizer used by registration and compares the PIN with the stored password hash. Successful login returns an access JWT, token type, expiry duration, and the user's public profile. It does not return a refresh token, password hash, or other credential.

The player endpoint accepts only accounts with the `PLAYER` role. Agent, administrator, support, and finance identities use their separate authentication paths. Incorrect PIN, unknown phone, inactive status, and a non-player identity receive the same `AUTH_INVALID_CREDENTIALS` / HTTP 401 response. Invalid phone syntax and empty or malformed request fields are rejected by validation before credential lookup.

JWTs use HS256, carry the normalized phone as subject, and expire after the configured access-token TTL (default 3,600 seconds). The API filter validates the signature and expiry, loads the account again by phone, and builds authorities from the current database roles. It checks account status for every protected request, so a suspended or inactive account cannot keep using an otherwise unexpired token. There is no refresh-token flow.

## Session, logout, and PIN handling

The player stores the access token, its local expiry time, and profile data through `flutter_secure_storage`; it does not put the PIN in session storage. A valid local session restores after process termination and app restart. Locally expired sessions are cleared. A 401 from a request that carried a bearer token clears the local session; a 401 from a login attempt without a bearer token leaves the login state alone.

Logout clears the token and profile from secure local storage and returns the app to the login screen. There is no server logout or token revocation endpoint. Therefore logout ends the app's local session; it does not revoke a copied JWT. Such a token remains usable until expiry unless the account becomes inactive or its signing secret changes. No refresh-token system or PIN recovery provider exists. PIN recovery requirements and delivery channel remain product dependencies outside this module.

## Failed-login protection and audit

The database-backed throttle tracks hashed account identifiers and client network addresses. Defaults are five failed attempts per account and fifty per network in a 15-minute window, with a 15-minute temporary lockout. Counters are serialized with MySQL advisory locks to avoid concurrent updates being lost. A successful player login clears that player's failed-attempt bucket; the network-level counter remains independent. Expired buckets are eligible for cleanup after 48 hours. Lockout is temporary and does not change account status.

Login audit records capture success and failure outcomes. The audit sanitizer redacts PIN, phone, and token fields; rate-limit audit events record the scope and path without credentials. Persistent QA login attempts create ordinary audit rows and hashed throttle buckets. They do not mutate user, role, wallet, grant, or wallet-transaction rows.

## Defects addressed

- `/api/v1/auth/login` previously rejected AGENT identities but could authenticate other non-player roles. Player login now rejects any account without `PLAYER`, and the test harness uses role-issued JWTs for administrative setup instead of treating the player endpoint as a generic role login.
- A successful player login previously left the account's failed-PIN counter in place. It now clears that account counter after credentials and status pass; the separate network counter remains intact.
- The Flutter API interceptor previously reported a login 401 as an expired session even when the login request carried no bearer token. It now clears session state only for a 401 response to a request that actually sent a bearer token.
- The login subtitle said “password” while the UI and server require a six-digit PIN. English and Amharic copy now describe the PIN consistently.

## Verification and evidence

See the [test matrix](module-03b-test-matrix.md) for each L01–L28 scenario and the [backend results](../evidence/module-03b-backend-results.txt), [Android results](../evidence/module-03b-android-results.txt), and [read-only database query/output](../evidence/module-03b-database-readonly.sql) / [output](../evidence/module-03b-database-readonly.txt). Android evidence uses emulator-5554 and the QA backend at `10.0.2.2:8081`; Maven integration tests use real MySQL `bingo_qa_test`. The persistent `bingo_qa` database is queried read-only for reconciliation.

The retained Module 03A players 178 and 179 remain ACTIVE with ETB 0.00 available/promotional balances, zero signup grants, and zero wallet transactions. One incorrect PIN was followed by a successful login for player 178; that successful login clears its account failure count. The PENDING player fixture was tested without changing status. Four unknown synthetic phone identifiers were used for lockout; read-only SQL confirms none created a user or wallet. One active hashed throttle bucket is expected from the final temporary lockout and expires according to the existing 15-minute policy.

Final checks: focused auth integration 27/27 PASS; full backend integration 137/137 PASS on real MySQL `bingo_qa_test`; five Android integration tests 5/5 PASS on emulator-5554; Flutter analyzer reports no issues; Flutter unit/widget suite 20/20 PASS. Logs and reconciliation evidence are linked above.
