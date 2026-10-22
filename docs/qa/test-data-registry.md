# Persistent QA Test Data Registry

This registry contains identifiers and non-secret business state only. Never put passwords, PINs, JWTs, activation links, or other secrets here. QA records are retained across modules; do not delete or reset them.

QA accounts were provisioned in `bingo_qa` through the guarded QA-only bootstrap after the user authorized choosing test numbers. The bootstrap verified its active schema was exactly `bingo_qa`, required the schema to have zero users and agents, refused reruns on a non-empty account schema, and used the application's BCrypt encoders and agent approval service. Credentials are stored only in ignored `bingo-backend/.env.qa`. The five numbers below are distinct syntactically valid QA identifiers; the app has no carrier-reserved test range. Player suspension is not supported by a player account service, so no suspended player was fabricated.

| Test account or record | Database ID | Module | Current business status | Related transactions or sessions | Reuse restrictions | Created |
| --- | --- | --- | --- | --- | --- | --- |
| QA_ADMIN_001 — `qa-admin@example.test`, phone `251900000001`, role `ADMIN` | User 1 | 00 | `ACTIVE` | None | Persistent login identity; keep credentials in ignored `.env.qa` | 2026-10-22 |
| QA_AGENT_001 — approved agent, phone `251900000002`, role `AGENT` | User 2 / Agent 1 | 00 | User `ACTIVE`; agent `APPROVED` | Float created with zero ETB | Persistent login identity; keep password/PIN in ignored `.env.qa` | 2026-10-22 |
| QA_AGENT_002 — pending agent, phone `251900000003`, role `AGENT` | User 3 / Agent 2 | 00 | User `PENDING`; agent `PENDING` | None | Pending identity; do not approve unless a later scenario explicitly requires it | 2026-10-22 |
| QA_PLAYER_001 — phone `251900000004`, role `PLAYER` | User 4 | 00 | `ACTIVE` | Wallet and normal signup-reward processing | Persistent login identity; keep PIN in ignored `.env.qa` | 2026-10-22 |
| QA_PLAYER_002 — phone `251900000005`, role `PLAYER` | User 5 | 00 | `ACTIVE` | Wallet and normal signup-reward processing | Persistent login identity; keep PIN in ignored `.env.qa` | 2026-10-22 |

Naming convention: `QA_ADMIN_###`, `QA_PLAYER_###`, `QA_AGENT_###`, and entity names such as `QA_ROOM_###` / `QA_SESSION_###`. Use synthetic, valid, uniquely allocated phone numbers and fresh idempotency keys/transaction references for each new operation. A completed transaction or terminal session is historical data and must not be forced back into an earlier state.
