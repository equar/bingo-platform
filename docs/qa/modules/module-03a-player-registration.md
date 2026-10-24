# Module 03A — Player Registration and Account Creation

## Current outcome

**COMPLETE.** Module 03A was executed on 2026-10-24 (Africa/Nairobi) through the real player app, backend, isolated MySQL integration database, and read-only persistent QA queries. The QA backend was started with the documented `qa` profile. Android UI coverage ran on emulator-5554.

## Existing registration design

The player application uses one `AuthScreen` for login and registration. Registration mode adds **display name** to the normal **phone number** and **six digit PIN** fields. There is no email field or OTP flow. The PIN is submitted in the DTO property named `password` and encoded with the configured password encoder before persistence; it is not stored as plaintext. The UI trims the display name and phone number. The backend trims the display name and canonicalizes phone numbers.

The Ethiopian phone normalizer accepts 9 digit local mobile numbers beginning 7 or 9, 10 digit local forms beginning with 0, and 12 digit country code forms beginning 251. It removes common spaces, hyphens, parentheses, and periods, and persists the canonical `251` plus 9 digit form. Other prefixes and lengths are rejected.

`POST /api/v1/auth/register` is public, validates `RegisterRequest`, returns HTTP 201, and issues a bearer token. `AuthService.register` assigns only `PLAYER` and sets status to `ACTIVE`; registration has no verification/activation step. Client-supplied `roles` and `status` fields are ignored by the request DTO, as covered by the existing registration integration test. Protected routes require authentication and authorization; player calls to admin user routes are forbidden.

## Account initialization and reward boundary

User creation, wallet initialization, signup reward evaluation, and token generation run inside the registration service transaction. Wallet uniqueness is enforced by a database unique constraint on owner. Wallet defaults to the configured currency (ETB in the QA configuration) and zero available and promotional balances.

Registration calls `SignupRewardService.grantForNewPlayer`. It grants only when the newest policy is enabled, currently effective, positive, the account is a player, and no prior grant exists. The persistent QA registry records the existing signup policy rows as disabled, expired, and zero amount. Both new QA accounts received zero wallet balances, no signup grants, and no wallet transactions. No signup reward policy or existing financial balance was changed.

The success response includes a bearer token and account profile. MySQL integration tests verify successful registration, canonical phone, forced PLAYER role/status, login, denial from the admin users endpoint, registration audit, wallet initialization, duplicate concurrency, input validation, and transaction rollback.

## Verification performed

- Read the requested Module 00/01/02I QA documentation and persistent test-data registry.
- Inspected player `AuthScreen`, API/repository, registration DTO/controller/service, phone normalization, user/wallet entities and uniqueness, reward policy service, auth throttling, and the existing registration security test.
- Full Maven suite on guarded MySQL `bingo_qa_test`: **131/131 passed**, 0 failures/errors/skips. Focused auth/normalizer regressions and rollback test passed; exact execution details are in [backend evidence](../evidence/module-03a-backend-results.txt).
- Flutter analysis: no issues. Player unit/widget tests: **20/20 passed**.
- Actual Android integration tests: **8/8 passed** across two retained synthetic QA identities; see [Android evidence](../evidence/module-03a-android-results.txt) and screenshots under `docs/qa/evidence/`.
- Read-only `bingo_qa` verification confirms users 1–12 retain their recorded roles/statuses and first five retain their prior states. The two new players each have one ACTIVE PLAYER user, one ETB wallet at 0.00/0.00, one registration audit event, no grant, and no wallet transactions. All seven signup policies remain disabled, expired, and zero amount. See the [read-only SQL](../evidence/module-03a-database-readonly.sql) and [query output](../evidence/module-03a-database-readonly.txt). No existing QA record was changed.

## Defects fixed

- Concurrent duplicate registration previously returned HTTP 500 for the request losing the MySQL unique-key race. It now returns `PHONE_ALREADY_REGISTERED` / HTTP 409. See [reproduction and fix evidence](../evidence/module-03a-concurrent-registration-defect.md).
- The phone normalizer previously stripped arbitrary non-digits, allowing malformed letters to collapse into an accepted phone. Backend and Flutter now accept only the existing phone formats and permitted separators.

Registration-specific rate limiting is not implemented; the existing authentication throttle applies to login only. No new signup throttle policy was invented. No Module 02, 03B, 03C, 03D, or 03E work was started.
