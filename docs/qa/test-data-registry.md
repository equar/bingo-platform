# Persistent QA Test Data Registry

This registry contains identifiers and non-secret business state only. Never put passwords, PINs, JWTs, activation links, or other secrets here. QA records are retained across modules; do not delete or reset them.

The original five QA accounts were provisioned in `bingo_qa` through the guarded QA-only bootstrap after the user authorized choosing test numbers. The bootstrap verified its active schema was exactly `bingo_qa`, required the schema to have zero users and agents, refused reruns on a non-empty account schema, and used the application's BCrypt encoders and agent approval service. Their credentials are stored only in ignored `bingo-backend/.env.qa`. Player registration accepts distinct syntactically valid QA identifiers; the app has no carrier-reserved test range.

| Test account or record | Database ID | Module | Current business status | Related transactions or sessions | Reuse restrictions | Created |
| --- | --- | --- | --- | --- | --- | --- |
| QA_ADMIN_001 — `qa-admin@example.test`, phone `251900000001`, role `ADMIN` | User 1 | 00 | `ACTIVE` | None | Persistent login identity; keep credentials in ignored `.env.qa` | 2026-10-22 |
| QA_AGENT_001 — approved agent, phone `251900000002`, role `AGENT` | User 2 / Agent 1 | 00 | User `ACTIVE`; agent `APPROVED` | Float created with zero ETB | Persistent login identity; keep password/PIN in ignored `.env.qa` | 2026-10-22 |
| QA_AGENT_002 — pending agent, phone `251900000003`, role `AGENT` | User 3 / Agent 2 | 00 | User `PENDING`; agent `PENDING` | None | Pending identity; do not approve unless a later scenario explicitly requires it | 2026-10-22 |
| QA_PLAYER_001 — phone `251900000004`, role `PLAYER` | User 4 | 00 | `ACTIVE` | Wallet and normal signup-reward processing | Persistent login identity; keep PIN in ignored `.env.qa` | 2026-10-22 |
| QA_PLAYER_002 — phone `251900000005`, role `PLAYER` | User 5 | 00 | `ACTIVE` | Wallet and normal signup-reward processing | Persistent login identity; keep PIN in ignored `.env.qa` | 2026-10-22 |
| QA_PLAYER_003 — QA Module 01 Restricted Player, phone `251900269346`, role `PLAYER` | User 6 | 01 | `PENDING` | Wallet and normal signup-reward processing | Persistent P17 restriction fixture; do not reactivate except when rerunning the authorized P17 flow. PIN is in ignored `bingo-app/.env.p17.local`. | 2026-10-23 |

Naming convention: `QA_ADMIN_###`, `QA_PLAYER_###`, `QA_AGENT_###`, and entity names such as `QA_ROOM_###` / `QA_SESSION_###`. Use synthetic, valid, uniquely allocated phone numbers and fresh idempotency keys/transaction references for each new operation. A completed transaction or terminal session is historical data and must not be forced back into an earlier state.

## Module 01 authentication use

On 2026-10-23 the existing QA admin and player identities were used for authentication-only browser checks. The original five users and two agent profiles were preserved with their roles and statuses unchanged. The player Android registration flow created the dedicated `QA_PLAYER_003` fixture and its normal wallet/signup reward; the admin status action left it `PENDING`. No account or business record was deleted. Authentication attempts, status-change audit entries, and hashed rate-limit buckets remain in `bingo_qa`; backend integration-test setup and cleanup ran only against `bingo_qa_test`.

The player and agent Android suites also used the existing accounts for successful and rejected sign-ins. Rate-limit checks used per-run synthetic phone identifiers and created no user records. P17 used the Flutter registration flow to create the dedicated player above, then the admin player-status workflow to preserve it as `PENDING`. Read-only verification confirmed six users, two agent profiles (one `APPROVED`, one `PENDING`), original user IDs 1–5 and their states unchanged, user 6 `PENDING`, and Flyway V14. P17 also confirmed wallet balances were unchanged by the restriction. Hashed rate-limit buckets and authentication audit events remain in `bingo_qa`; no passwords, PINs, JWTs, or generated secrets are recorded here. See [module-01-database-verify.txt](evidence/module-01-database-verify.txt).
