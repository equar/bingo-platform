# Module 04D-4 — Real MySQL, Android and Admin Claim E2E Recovery

Disposition: **PARTIAL — database credentials and schema access were verified read-only, but the Android/Admin runtime was not started because the QA account has schema-destructive privileges. No safe valid-ticket fixture path exists. Real Android, Admin and backend claim E2E remain BLOCKED.**

Scope: Module 04D-4 only. No test backend was started, no gameplay/claim request was sent, no database record or grant was changed, no Flyway clean was run, and no financial operation occurred. The existing dev backend on port 8080 was not used for QA.

## Read-only environment verification

- Port 8080 remains a Java process running `com.bakaroo.bingo.backend.BingoBackendApplication`, active profile `dev`, with startup/Flyway logs identifying schema `bingo`. It is not the QA target and was not used.
- `bingo-backend/.env.test` already provides the local `bingo_qa_test` connection. It was read in-process without printing values. A temporary permission-restricted MySQL option file was removed after each query.
- Read-only authentication succeeded at `127.0.0.1:3306`: `DATABASE()` returned `bingo_qa_test`, `CURRENT_USER()` returned `bingo_qa_test_runner@127.0.0.1`, and MySQL reported version 8.0.36.
- Read-only `SHOW GRANTS FOR CURRENT_USER()` returned global `USAGE` and **`ALL PRIVILEGES ON bingo_qa_test.*`**. Attempts to enumerate tables in `bingo`, `bingo_qa`, and `bingo_qa_maven_test` were denied. There are no grants on those schemas, but the QA account can perform destructive DDL inside its own schema.
- The current Flyway history query reports V22 successful. No migration was applied by this module.
- The `android-test` profile sets `ddl-auto: validate`, `flyway.clean-disabled: true`, `validate-on-migrate: true`, and financial switches false. Its JDBC URL is supplied through `BINGO_TEST_MYSQL_URL`; it does not create a database. Prize settlement remains hard-blocked as `PRIZE_SETTLEMENT_BLOCKED`. The payment provider is configuration-only and no payment network client was found in this claim/payment path.
- Runtime identity reporting is disabled by default and requires build commit/checksum environment values. It checks profile, URL/catalog, Flyway version and financial flags but does not check `CURRENT_USER()` or grants. Since the database account still has DROP/CREATE authority, the required runtime/database safety gate failed; the profile was not launched on port 8082. Runtime build identity, startup Flyway validation and process-level guard output therefore remain unverified.

## Claim and fixture review

The player submission API is `POST /api/v1/tickets/{id}/win-claims`, requires PLAYER authorization and an idempotency key, and derives ticket, owner, session, card and draw history server-side. Ownership is checked through ticket ID and owner ID. The service locks the authoritative session row, rejects CANCELLED, evaluates the existing pattern rules, persists the claim, and relies on the mutation audit aspect. CLOSED behavior remains unchanged pending Product approval. Admin claim routes are ADMIN-protected; the claims UI shows claimant/ticket/session references and validation/status and restricts review buttons to server-passed pending claims. These points were source-reviewed only in this module.

No approved nonfinancial route can produce the required new eligible ticket. Player purchase invokes the isolated financial-posting guard, debits the wallet, and records a transaction. The agent ticket route debits agent float and creates commission accrual. No test-only ticket fixture API was found. No wallet balance, ticket eligibility, ownership, or claim behavior was bypassed. Therefore positive claim E2E is BLOCKED independently of the grant issue. No historical fixture IDs were reused.

## Executed checks

- Focused database-free Maven tests: **26/26 passed**, including claim/session security 6/6, financial guard and Maven target policy, pattern evaluator, game rules and session state. No Spring integration context or DB test class was selected.
- Flutter claim controller retry test: **1/1 passed**.
- Flutter analyzer: **No issues found**.
- Admin claims component test: **6/6 passed** with mocked API calls; this is not Chromium E2E.
- MySQL checks were SELECT/SHOW only. No rows or grants were changed. No Android emulator test, backend API claim integration, Admin browser interaction, screenshot, or before/after business-data reconciliation was performed.

## Scenario disposition

All requested real scenarios A01–A14 are **BLOCKED / NOT RUN**. No test backend or fixture was created, so there are no fixture IDs or correlation identifiers. Android login/ticket/history/claim/retry, MySQL claim/audit persistence, duplicate behavior, CANCELLED API rejection, Admin claim visibility/authorization, and no-financial-write E2E remain unverified. The CANCELLED rule has focused database-free service coverage from Module 04D-2; it was not sent through a live API here.

## Required next steps

1. An authorized DBA must review the current grants and narrow `bingo_qa_test_runner` to only the runtime DML/read privileges required on `bingo_qa_test`, removing CREATE/ALTER/DROP and other unneeded privileges. Reconfirm the three cross-schema denials afterward. No grant change was made here.
2. Provide an approved, nonfinancial mechanism for creating a dedicated synthetic player, session, server-generated ticket and authorized draw history. Do not substitute arbitrary SQL ticket insertion or wallet/float funding.
3. Then start the `android-test` runtime with explicit `.env.test` loading, enable the fail-fast runtime identity checks for actual account/grants, verify build identity and V22 schema state, and run Android/Admin E2E with read-only before/after reconciliation.

Module 04D-4 is **PARTIAL**. Module 04D remains PARTIAL. Module 04C and 04B remain PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE. No Module 04E work, commit or push occurred.
