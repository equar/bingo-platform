# Persistent QA Test Data Registry

This registry contains identifiers and non-secret business state only. Never put passwords, PINs, JWTs, activation links, or other secrets here. QA records are retained across modules; do not delete or reset them.

No QA accounts have been provisioned yet. `bingo_qa` exists and has migrations through V14. The available development seeder can overwrite matching accounts and is not safe for this persistent QA schema. Approval was received for a guarded, one-time bootstrap limited to `bingo_qa`; account creation is waiting on reserved Ethiopian QA phone numbers because the repository publishes no reserved range and the backend validates Ethiopian mobile formats. No credentials or business records have been added.

| Test account or record | Database ID | Module | Current business status | Related transactions or sessions | Reuse restrictions | Created |
| --- | --- | --- | --- | --- | --- | --- |

Naming convention: `QA_ADMIN_###`, `QA_PLAYER_###`, `QA_AGENT_###`, and entity names such as `QA_ROOM_###` / `QA_SESSION_###`. Use synthetic, valid, uniquely allocated phone numbers and fresh idempotency keys/transaction references for each new operation. A completed transaction or terminal session is historical data and must not be forced back into an earlier state.
