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
| QA_M02_PLAYER_001 — phone `251736384803`, role `PLAYER` | User 7 | 02 | `PENDING` | Wallet 4, zero ETB | Dedicated status restriction fixture; retain PENDING. PIN is only in ignored `bingo-app/.env.m02.local`. | 2026-10-23 |
| QA_M02_PLAYER_002 — phone `251736819472`, role `PLAYER` | User 8 | 02 | `PENDING` | Wallet 5, zero ETB | Retained player created by the first full browser-suite pass; retain PENDING. PIN is only in ignored `bingo-app/.env.m02.local`. | 2026-10-23 |
| QA_M02_PLAYER_003 — phone `251736949301`, role `PLAYER` | User 9 | 02 | `PENDING` | Wallet 6, zero ETB | Retained player created by the second full browser-suite pass; retain PENDING. PIN is only in ignored `bingo-app/.env.m02.local`. | 2026-10-23 |
| QA_M02_PLAYER_004 — phone `251737056953`, role `PLAYER` | User 10 | 02 | `PENDING` | Wallet 7, zero ETB | Final passing browser-suite restriction fixture; retain PENDING. PIN is only in ignored `bingo-app/.env.m02.local`. | 2026-10-23 |
| QA_M02A_AGENT_001 — phone `251911000601`, role `AGENT` | User 11 / Agent 3 | 02A | User `ACTIVE`; agent `APPROVED` | No player wallet; zero ETB agent float | Dedicated lifecycle fixture; retain. Credentials are only in ignored `bingo-agent/.env.m02a.local`. | 2026-10-23 |
| QA_M02A_AGENT_002 — phone `251911000602`, role `AGENT` | User 12 / Agent 4 | 02A | User `SUSPENDED`; agent `SUSPENDED` | No player wallet; zero ETB agent float | Dedicated suspension fixture; retain suspended with audit history. Credentials are only in ignored `bingo-agent/.env.m02a.local`. | 2026-10-23 |
| QA_M02B_FLOAT_REQUEST_001 — agent User 11 / Agent 3; request references `QA-M02B-AGENT3-20261023-1130`, `...-1131`, `...-1140` | Requests 1–3 | 02B | All `REJECTED`; reviewer User 1; Agent 3 remains approved with 0.00 ETB available/clearing | Synthetic request amounts 12.34, 12.35, and 12.36 ETB. No float ledger transactions or balance credits. Keep all requests and audit records. | 2026-10-23 |
| QA_M02_SUPPORT_001–005 | Tickets 1–5 (`ST-87FA5BE5`, `ST-C16E110A`, `ST-3CB52E67`, `ST-8D9A3A37`, `ST-B6B0DFA3`) | 02 | `WAITING_FOR_CUSTOMER`, assigned to admin user 1 | Synthetic requester user 4; each has one public reply and one internal note | Historical support fixtures; retain all messages, notes and audit records. | 2026-10-23 |
| QA_M02_ROOM_001–004 | Rooms 1–4 | 02 | Active | Each paired with same-numbered DRAFT session | Keep active; do not delete or change session state. | 2026-10-23 |
| QA_M02_SESSION_001–004 | Sessions 1–4 (`M02-1792735981360`, `M02-1792736827510`, `M02-1792736957844`, `M02-1792737064944`) | 02 | `DRAFT` | Room IDs 1–4; no draws, starts, or terminal transitions | Retain DRAFT; do not start or draw. | 2026-10-23 |
| QA_M02_SIGNUP_POLICY_001–007 | Signup reward policy rows 1–7 | 02 | Disabled, expired, zero ETB | Effective 1999-12-31 21:00Z through 2000-01-01 21:00Z | Historical configuration versions; none grants a reward. Keep them for audit/history. | 2026-10-23 |
| QA_M02_TRANSFER_POLICY_001–005 | Transfer policy rows 1–5 | 02 | Disabled, expired, zero ETB limits | Effective 1999-12-31 21:00Z through 2000-01-01 21:00Z | Historical configuration versions; none enables a transfer. Keep them for audit/history. | 2026-10-23 |
| QA_M02_COMMISSION_RULE_001–002 | Commission rule versions 1–2 | 02 | Inactive, 0 bps; effective 1999-12-31 21:00Z | Version 2 is current | Historical inactive rules; preserve version history. | 2026-10-23 |
| QA_M03A_PLAYER_001 — phone `251930300001`, role `PLAYER` | User 178 | 03A | `ACTIVE` | Wallet ETB 0.00 available / 0.00 promotional; no signup grant or wallet transactions; one registration audit event | Created via actual Flutter registration UI on emulator-5554. Retain account. PIN stored only in ignored `.tmp/module-03a-e2e-secrets.json`. | 2026-10-24 |
| QA_M03A_PLAYER_002 — phone `251930300002`, role `PLAYER` | User 179 | 03A | `ACTIVE` | Wallet ETB 0.00 available / 0.00 promotional; no signup grant or wallet transactions; one registration audit event | First registration attempt used a deliberately unreachable backend and created no account; retry succeeded through actual Flutter registration UI on emulator-5554. Retain account. PIN stored only in ignored `.tmp/module-03a-retry-e2e-secrets.json`. | 2026-10-24 |

Naming convention: `QA_ADMIN_###`, `QA_PLAYER_###`, `QA_AGENT_###`, and entity names such as `QA_ROOM_###` / `QA_SESSION_###`. Use synthetic, valid, uniquely allocated phone numbers and fresh idempotency keys/transaction references for each new operation. A completed transaction or terminal session is historical data and must not be forced back into an earlier state.

## Module 03A execution note — 2026-10-24

Two persistent test identities were created through real emulator UI registration, and are recorded above. A read-only snapshot after the run verifies users 1–12 retain their registered role/status states and player wallet balances; it also verifies both Module 03A players are ACTIVE with zero-balance ETB wallets, no signup grants/transactions, and one registration audit event apiece. The full output is [here](evidence/module-03a-database-readonly.txt). No existing QA record was reused or written by this workflow.

## Module 02 admin management

The admin browser suite created five retained support tickets (IDs 1–5), four active room/DRAFT session pairs (IDs 1–4), four additional pending players (IDs 7–10), disabled/expired policy history rows (signup IDs 1–7 and transfer IDs 1–5), and two inactive zero-basis-point commission versions. All synthetic player wallets remain at zero ETB. Read-only verification confirmed `bingo_qa`, ten users, the original two agent profiles unchanged (approved user 2 and pending user 3), all new players PENDING, all four sessions DRAFT, and Flyway V14. Full details are in [module-02-database-verify.txt](evidence/module-02-database-verify.txt); browser/API evidence is in the `module-02-*` files. No secrets or session traces are stored in this registry.

## Module 02A agent lifecycle

The Android onboarding flow created two retained synthetic applications. Admin approval made User 11 / Agent 3 `ACTIVE` / `APPROVED`; Android login and an authorized dashboard request passed. User 12 / Agent 4 was approved, authenticated on Android, and then suspended through the admin portal; it remains `SUSPENDED`. Both have no player wallet and a zero-balance agent float. Read-only verification confirmed the original agent profiles 1 and 2 remain `APPROVED` and `PENDING`, and Flyway is V15. See [the lifecycle report](modules/module-02a-agent-lifecycle.md), [the database verification](evidence/module-02a-database-verify.txt), and the `module-02a-*` evidence files. Do not reuse either identity or reactivate the suspended record.

## Module 02B agent float management

Agent 3 submitted three retained requests (ETB 12.34, 12.35, 12.36) that were safely rejected, plus requests 4 and 5 (each ETB 25.50, references `QA-M02B-TRANSFER-20261023-123001` and `QA-M02B-TRANSFER-20261023-131900`) through the Flutter Android UI. They were matched to synthetic settled statement entries 1 and 2 by finance verifier user 15, then separately approved by finance approver user 16 through the Chromium admin workflow. Journals 1 and 2 are synthetic, each has two balanced ETB 25.50 lines, and each produced one float TOP_UP transaction; Agent 3 now has ETB 51.00 available and ETB 0.00 clearing. Receiving bank account 1 is a synthetic QA fixture with encrypted account data and visible tail 2002. Users 15 and 16 are retained active QA finance accounts. Original agent profiles 1 and 2 remain APPROVED and PENDING. Flyway is V18. Production posting remains blocked; the synthetic statement is not evidence of real funds. See [the Module 02B report](modules/module-02b-agent-float.md), [read-only database verification](evidence/module-02b-database-verify.txt), and `module-02b-*` UI evidence.

## Module 02C admin portal closure

No persistent users, business rows, policies, agent state, balances, journals, or audit history were changed during Module 02C. Read-only verification found 14 users, two approved agent profiles, one pending and one suspended agent profile, two approved and three rejected float requests, four DRAFT sessions, five open support tickets, and no deposit claims, win claims, or prize entitlements. Dashboard aggregates were independently checked against `bingo_qa`. See [the Module 02C database snapshot](evidence/module-02c-database-verify.txt) and browser metrics evidence. Preserve all existing rows and historical statuses.

## Module 02D player, wallet, deposit, and support operations

All Module 02D identities and business records below were created through the supported registration/player UI/API workflows. A few browser iterations ended after performing some persistent actions; those records were kept and are included here. No record was deleted or reset. Player names use the pattern `QA_M02D_PLAYER_00{slot}_{run}`; slots 1, 3, and 4 are `ACTIVE`, slot 2 is `SUSPENDED` except User 30, which remains `ACTIVE` because that iteration stopped before its status scenario. All listed users have role `PLAYER` and a zero-balance wallet except the eight wallet-adjustment users listed below.

| Run suffix | User IDs (slots 1–4) | Retained state |
| --- | --- | --- |
| `56064365` | 17–20 | Slot 2 suspended; other slots active; no wallet transactions |
| `56684959` | 21–24 | Slot 2 suspended; other slots active; no wallet transactions |
| `56741781` | 25–28 | Slot 2 suspended; slot 4 has one ETB 1.25 adjustment |
| `56798154` | 29–32 | All active; no wallet transactions (iteration stopped before lifecycle/adjustment steps) |
| `56849108` | 33–36 | Slot 2 suspended; slot 4 has one ETB 1.25 adjustment |
| `56915327` | 37–40 | Slot 2 suspended; slot 4 has one ETB 1.25 adjustment |
| `56968980` | 41–44 | Slot 2 suspended; slot 4 has one ETB 1.25 adjustment |
| `57059846` | 45–48 | Slot 2 suspended; slot 4 has one ETB 1.25 adjustment |
| `57767279` | 49–52 | Slot 2 suspended; slot 4 has one ETB 1.25 adjustment |
| `57876059` | 53–56 | Slot 2 suspended; slot 4 has one ETB 1.25 adjustment |
| `58145784` | 57–60 | Final passing E2E: slot 2 suspended; slot 4 has one ETB 1.25 adjustment; nonexistent wallet owner returns 404 |

Users 28, 36, 40, 44, 48, 52, 56, and 60 each have exactly one `WALLET_ADJUSTMENT` transaction for ETB 1.25. The E2E replayed each idempotency key without a second credit. No other Module 02D player has a wallet transaction. The original `QA_PLAYER_003` remains PENDING and was not modified.

| Deposit ID | Player ID | Normalized reference | Status | Amount / wallet effect |
| --- | --- | --- | --- |
| 1 | 35 | `QAM02D56849108DEP` | PENDING | ETB 2.50; no credit |
| 2 | 39 | `QAM02D56915327DEP` | PENDING | ETB 2.50; no credit |
| 3 | 43 | `QAM02D56968980DEP` | REJECTED | ETB 2.50; no credit |
| 4 | 47 | `QAM02D57059846DEP` | REJECTED | ETB 2.50; no credit |
| 5 | 51 | `QAM02D57767279DEP` | REJECTED | ETB 2.50; no credit |
| 6 | 55 | `QAM02D57876059DEP` | REJECTED | ETB 2.50; no credit |
| 7 | 59 | `QAM02D58145784DEP` | REJECTED | ETB 2.50; no credit |

Five synthetic support-ticket batches are retained, each in category WALLET with HIGH priority. Tickets 6–11 were created by run `56968980`, tickets 12–17 by `57059846`, tickets 18–23 by `57767279`, tickets 24–29 by `57876059`, and tickets 30–35 by `58145784`. Tickets 6, 12, 24, and 30 are CLOSED and assigned to admin user 1; their corresponding runs verified assignment, public reply, internal note, resolution summary, and reload. All other tickets in these batches remain OPEN and unassigned. The five original support tickets (IDs 1–5) remain `WAITING_FOR_CUSTOMER`, assigned to admin user 1.

Read-only verification is retained in [module-02d-database-verify.txt](evidence/module-02d-database-verify.txt); browser/API results are in the `module-02d-*` evidence files. No phone numbers, credentials, PINs, tokens, or secret values are recorded here. Agent profiles 1–4 and Module 02B float requests, transactions, and balanced journals were read-only checked and remain unchanged; Agent 3 still has ETB 51.00 available and ETB 0.00 clearing.

### Current financial-security verification

QA and `bingo_qa_test` are at Flyway V20; the V19 checksum remains `-727947304`, and V20 was applied additively. Full backend integration: 110/110 passed; the updated wallet integration class: 9/9 passed; focused production-simulation guard: 2/2 passed. Frontend: 78/78, lint and build passed. The latest real Chromium run `69021617` passed 1/1 and exercised D01–D20. Persistent financial verification below used SELECT statements only. No SQL writes, resets, or deletions were performed. Failed/interrupted E2E records remain retained.

The latest run created player users #142–145, finance verifier #146, finance approver #147, and deposit players #148–150. Receiving accounts #5–7 are active and marked player-deposit-eligible for local QA simulation. Their statement entries are explicitly marked `SYNTHETIC_QA_BANK_STATEMENT`; no entry represents real settlement. Earlier partial runs left claims #14–15 pending/uncredited and adjustment requests #7–10 pending/unposted; preserve them.

| Deposit ID | Player ID | Reference suffix / run | Status | Verification / posting / wallet |
| --- | --- | --- | --- | --- |
| 14 | 120 | `68516000-DEP` | PENDING | No verifier, journal, or credit; balance 0.00 ETB |
| 15 | 129 | Earlier interrupted run | PENDING | No verifier, journal, or credit; balance 0.00 ETB |
| 16 | 144 | `69021617-DEP` | APPROVED | Verification 7 / statement 9; journal 4 / wallet transaction 18; verifier 146 / approver 147; ETB 2.50 |
| 17 | 148 | `69021617-MISMATCH` | VERIFICATION_EXCEPTION | Verification 8 / statement 11; submitted ETB 3.00 vs statement ETB 2.99; no journal or credit |
| 18 | 149 | `69021617-REJECT` | REJECTED | No verification, journal, or wallet transaction; balance 0.00 ETB |
| 19 | 150 | `69021617-CONCURRENT` | APPROVED | Verification 9 / statement 12; journal 5 / wallet transaction 19; verifier 146 / approver 147; ETB 4.25 |

Deposit journals #4–5 have balanced ETB lines: debit `QA_BANK_CLEARING_ASSET`, credit `PLAYER_WALLET_LIABILITY`. Claims #1–7 remain unchanged: #1–2 `PENDING`, #3–7 `REJECTED`; all seven have zero deposit journals and deposit-credit transactions. Earlier simulated deposits #9, #10, and #13 remain preserved and are listed in the financial-security report.

| Adjustment request | Player | Maker / checker | Status | Journal / wallet transaction |
| --- | --- | --- | --- | --- |
| 7 | 109 | 110 / — | PENDING | No journal or credit |
| 8 | 115 | 116 / — | PENDING | No journal or credit |
| 9 | 121 | 122 / — | PENDING | No journal or credit |
| 10 | 130 | 131 / — | PENDING | No journal or credit |
| 11 | 139 | 140 / 141 | APPROVED | Journal 5 / transaction 16; ETB 1.25, balance 0.00 → 1.25 |
| 12 | 145 | 146 / 147 | APPROVED | Journal 6 / transaction 17; ETB 1.25, balance 0.00 → 1.25 |

Adjustment journals #5–6 balance between `WALLET_ADJUSTMENT_CLEARING` and `PLAYER_WALLET_LIABILITY`. Audit entries #1821/#1823–1824 record maker #146 and checker #147 for request #12; #1837–1839 record verifier #146 and approver #147 for deposit #16; #1848–1850 record separated roles for concurrent claim #19. Audit #1822 records the denied legacy one-step adjustment. The run created support tickets #42–47; #42 remains `CLOSED`, and #43–47 remain open. Original support tickets #1–5 remain `WAITING_FOR_CUSTOMER`, assigned to admin #1. Agent #3 remains `APPROVED` with ETB 51.00 available / ETB 0.00 clearing, two float transactions, and two balanced ETB 25.50 journals. See [read-only database verification](evidence/module-02d-database-verify.txt), [financial-security report](modules/module-02d-financial-security.md), [browser execution](evidence/module-02d-execution.json), and [test matrix](modules/module-02d-test-matrix.md).

## Module 02E synthetic game administration data

All game records in this section are synthetic admin QA records. They remain in persistent `bingo_qa`; interrupted browser attempts are included. No records are deleted or reset. All listed sessions have zero tickets and zero draw events.

| Room ID | Room name / retained configuration | Session ID and state |
| --- | --- | --- |
| 5 | `QA M02E Config 1792770580871-dda8f6`; ETB 2.50, active, v0 (incomplete early attempt) | — |
| 6 | `QA M02E Session Room 1792770588090-443bf1`; ETB 3.25, active | #5 DRAFT |
| 7 | `QA M02E Inactive Room 1792770594787-c5ba91`; inactive | — |
| 8 | `QA M02E Config 1792770663666-a9b516`; ETB 2.75, active, v1 | — |
| 9 | `QA M02E Session Room 1792770670474-37d0a8`; ETB 3.25, active | #6 DRAFT |
| 10 | `QA M02E Inactive Room 1792770670474-37d0a8`; inactive | — |
| 11 | `QA M02E Config 1792771154070-97a602`; ETB 2.75, active, v1 | — |
| 12 | `QA M02E Session Room 1792771164965-41157d`; ETB 3.25, active | #7 DRAFT |
| 13 | `QA M02E Inactive Room 1792771164965-41157d`; inactive | — |
| 14 | `QA M02E Cancel Room 1792771164965-41157d`; ETB 1.00, active | #8 CANCELLED |
| 15 | `QA M02E Config 1792771210298-2e8340`; ETB 2.75, active, v1 | — |
| 16 | `QA M02E Session Room 1792771210298-2e8340`; ETB 3.25, active | #9 DRAFT |
| 17 | `QA M02E Inactive Room 1792771210298-2e8340`; inactive | — |
| 18 | `QA M02E Cancel Room 1792771210298-2e8340`; ETB 1.00, active | #10 CANCELLED |
| 19 | `QA M02E Config 1792771312545-b42cb6`; ETB 2.75, active, v1 | — |
| 20 | `QA M02E Session Room 1792771312545-b42cb6`; ETB 3.25, active | #11 DRAFT |
| 21 | `QA M02E Inactive Room 1792771312545-b42cb6`; inactive | — |
| 22 | `QA M02E Cancel Room 1792771312545-b42cb6`; ETB 1.00, active | #12 CANCELLED |
| 23 | `QA M02E Config 1792771535974-5a17c5`; ETB 2.75, active, v1 | — |
| 24 | `QA M02E Session Room 1792771535974-5a17c5`; ETB 3.25, active | #13 DRAFT |
| 25 | `QA M02E Inactive Room 1792771535974-5a17c5`; inactive | — |
| 26 | `QA M02E Cancel Room 1792771535974-5a17c5`; ETB 1.00, active | #14 CANCELLED |
| 27 | `QA M02E Session Room 1792771630511-0ab2d5`; ETB 3.25, active | #15 DRAFT |
| 28 | `QA M02E Inactive Room 1792771630511-0ab2d5`; inactive | — |
| 29 | `QA M02E Cancel Room 1792771630511-0ab2d5`; ETB 1.00, active | #16 CANCELLED |
| 30 | `QA M02E Config 1792772678456-14a01a`; ETB 2.75, active, v1 | — |
| 31 | `QA M02E Session Room 1792772678456-14a01a`; ETB 3.25, active | #17 DRAFT |
| 32 | `QA M02E Inactive Room 1792772678456-14a01a`; inactive | — |
| 33 | `QA M02E Cancel Room 1792772678456-14a01a`; ETB 1.00, active | #18 CANCELLED |

Room 5 was retained from a browser iteration that stopped before editing. Other configuration rooms were edited from ETB 2.50 to ETB 2.75 with version increment 0→1. Session rooms use ETB 3.25 and preserve a synthetic LINE pattern/prize snapshot. The final browser run passed 6/6; the backend full suite passed 125/125 against `bingo_qa_test`. No claims, entitlements, tickets, or draws were present in `bingo_qa` at verification time. Duplicate room/start schedule groups: 0. Agent #3 remained APPROVED with ETB 51.00 available and ETB 0.00 clearing, two float transactions and two accounting journals; this verification used SELECT statements only. See [Module 02E report](modules/module-02e-game-admin.md), [case matrix](modules/module-02e-test-matrix.md), and [read-only database evidence](evidence/module-02e-database-verify.txt).

## Module 01 authentication use

On 2026-10-23 the existing QA admin and player identities were used for authentication-only browser checks. The original five users and two agent profiles were preserved with their roles and statuses unchanged. The player Android registration flow created the dedicated `QA_PLAYER_003` fixture and its normal wallet/signup reward; the admin status action left it `PENDING`. No account or business record was deleted. Authentication attempts, status-change audit entries, and hashed rate-limit buckets remain in `bingo_qa`; backend integration-test setup and cleanup ran only against `bingo_qa_test`.

The player and agent Android suites also used the existing accounts for successful and rejected sign-ins. Rate-limit checks used per-run synthetic phone identifiers and created no user records. P17 used the Flutter registration flow to create the dedicated player above, then the admin player-status workflow to preserve it as `PENDING`. Read-only verification confirmed six users, two agent profiles (one `APPROVED`, one `PENDING`), original user IDs 1–5 and their states unchanged, user 6 `PENDING`, and Flyway V14. P17 also confirmed wallet balances were unchanged by the restriction. Hashed rate-limit buckets and authentication audit events remain in `bingo_qa`; no passwords, PINs, JWTs, or generated secrets are recorded here. See [module-01-database-verify.txt](evidence/module-01-database-verify.txt).

## Module 02F admin/support lifecycle records

Module 02F created and retained synthetic ADMIN/SUPPORT identities through `POST /api/v1/admin/users`. IDs 151–167 are all `SUSPENDED`; all have corresponding status-update audit entries. IDs 151–158 are unactivated records retained from interrupted browser attempts. IDs 159–167 were activated for lifecycle scenarios before suspension. No record was deleted or reset. No original player, agent, wallet, deposit, support-ticket, or game record was modified; the shared QA admin was used only for negative current-password and continued-login checks.

| User IDs | Synthetic role(s) | Final state | Notes |
| --- | --- | --- | --- |
| 151, 153, 155–160, 162, 164, 166 | ADMIN | SUSPENDED | Includes interrupted and completed lifecycle iterations; generated credentials are not stored here. |
| 152, 154, 161, 163, 165, 167 | SUPPORT | SUSPENDED | Includes interrupted and completed lifecycle iterations; generated credentials are not stored here. |

The table groups IDs 155–167 by actual role as read from `user_roles`; all 17 identities have an `UPDATE_USER` audit event and remain available for future audit inspection. See [Module 02F Chromium results](evidence/module-02f-chromium-results.txt) and [read-only DB reconciliation](evidence/module-02f-database-reconciliation.txt). Original agent profiles 1–2 remain `APPROVED`/`PENDING`; Agent #3 remains `APPROVED` with ETB 51.00 and balanced journals; original deposit claims 1–7 remain intact. No additional ticket, wallet, deposit, room, session, ticket-purchase, draw, claim, or entitlement fixtures were created in Module 02F.

## Module 02G admin-security and support records

Module 02G used retained synthetic records through the browser workflows. User IDs 168–177 are active synthetic identities. Users 169, 171, and 175 had FINANCE_VERIFIER revoked and retain SUPPORT; users 172 and 176 were transitioned from SUPPORT to ADMIN; users 168, 170, and 174 are SUPPORT operators; users 173 and 177 are AGENT identities. Agent profiles #5 and #6 remain APPROVED and their PINs are stored only as one-way hashes. Support tickets #48 and #51 remain IN_PROGRESS and assigned to SUPPORT operators #170 and #174; #49 and #52 remain CLOSED; #50 and #53 remain OPEN after unauthorized mutation attempts. Ticket #52 includes the synthetic public SUPPORT reply. No existing agent, player, wallet, deposit, float, or original support ticket was used for these cases. See [Module 02G report](modules/module-02g-admin-security.md), [test matrix](modules/module-02g-test-matrix.md), and [read-only reconciliation](evidence/module-02g-database-reconciliation.txt).

## Module 02H wallet transfers

No persistent transfer fixture was created. Read-only checks against `bingo_qa` found zero `wallet_transfers`, zero wallet transactions with `reference_type=WALLET_TRANSFER`, and five transfer policy versions that remain disabled, expired, and zero-limit. The Chromium test compared transfer rows, linked ledger rows, wallet balances, and policy values before and after; all remained unchanged. It used existing QA player and approved-agent credentials only for authentication-denial checks. Isolated synthetic players, balances, transfers, and ledger rows are created only in `bingo_qa_test` by backend integration tests and are cleaned by the test lifecycle. No synthetic transfer credit or bank settlement is recorded in persistent QA. See [Module 02H database reconciliation](evidence/module-02h-database-reconciliation.txt), [test results](evidence/module-02h-test-results.txt), and [report](modules/module-02h-wallet-transfer-admin.md).

### Module 02I transfer fixture/policy review

No persistent transfer, player, or wallet fixture was created. `bingo_qa` was queried read-only; the five existing policies remain unchanged and no transfer or linked transfer-ledger row was added. The focused `bingo_qa_test` regression creates and clears its own isolated policy/user fixtures through the backend test lifecycle. Product/Finance approval is still required for a transfer-specific QA funding route and policy limits before positive persistent E2E. See [Module 02I](modules/module-02i-transfer-fixtures.md) and [read-only reconciliation](evidence/module-02i-database-reconciliation.txt).

## Module 03B player authentication

Module 03B created no persistent player identity. Players #178 and #179 from Module 03A remain `ACTIVE` with ETB 0.00 available/promotional balance, no signup reward grant, and no wallet transactions. User #6, the retained PENDING player, was used only for a rejected login and remains PENDING. Four unknown synthetic phone identifiers (`251930399999`, `251930399998`, `251930399997`, `251930399996`) were used for repeated-failure testing; none corresponds to a user or wallet. The last run leaves one expected active hashed temporary throttle bucket, which expires under the configured 15-minute lockout. Authentication audit entries and hashed attempt buckets are ordinary retained telemetry. No wallet balances, user statuses, or role assignments were changed. See the [Module 03B report](modules/module-03b-player-authentication.md), [Android results](evidence/module-03b-android-results.txt), and [read-only SQL output](evidence/module-03b-database-readonly.txt). No PINs, JWTs, plaintext credentials, or secret values are stored here.

## Module 03C player profile

Module 03C reused the existing active Module 03A player #178 (`QA Module 03A Player 001`) for read-only profile display, refresh, app-session restoration, and offline cached-profile checks. No persistent player or business fixture was created or edited. Player #178 and #179 remain active; their ETB wallet balances, grants, and transactions are unchanged. Profile GETs do not create profile-update audit events. Login/authentication telemetry may be refreshed by the view-login flow. The `bingo_qa` reconciliation is SELECT-only; backend tests run in isolated `bingo_qa_test`. Evidence: [Module 03C report](modules/module-03c-player-profile.md), [Android results](evidence/module-03c-android-results.txt), and [database reconciliation](evidence/module-03c-database-readonly.txt). Credentials and session tokens are not stored in tracked evidence.

## Module 03D signup rewards and wallet initialization

Persistent QA synthetic player #180 (`QA Module 03D Wallet Player 001`, phone `251930400001`) was registered through the Android app on emulator-5554. The player is ACTIVE with one ETB wallet, ETB 0.00 available, ETB 0.00 promotional, no signup reward grant, and no wallet transactions. Registration audit row #2574 records the new identity. No PIN or credential is stored in tracked evidence. The app session for retained player #178 was signed out locally to run registration; no user status, role, balance, wallet, or business record for #178/#179 was changed.

The seven persistent signup reward policy rows remain disabled, zero ETB, and expired (effective 1999-12-31 to 2000-01-01). The admin Chromium check only viewed current settings, history, and grants and reloaded the unchanged settings. It did not submit a positive policy. Admin sign-in added ordinary `LOGIN` audit telemetry (rows #2575 and #2577); signup grants remain zero. The database reconciliation is SELECT-only; backend positive-grant tests use only `bingo_qa_test`. See the [Module 03D report](modules/module-03d-signup-rewards.md), [Android results](evidence/module-03d-flutter-results.txt), [admin results](evidence/module-03d-admin-results.txt), and [read-only database output](evidence/module-03d-database-readonly.txt).

## Module 03E final player-account regression

Persistent synthetic player #181 (`QA Module 03E Regression Player 001`, phone `251930400002`) was registered through the actual Flutter Android UI on emulator-5554. It remains ACTIVE with the PLAYER role and one ETB wallet at 0.00 available / 0.00 promotional, with no signup grant and no wallet transaction. Keep the identity and registration/audit history. Android verification exercised wrong-PIN denial, successful login, profile refresh, local logout, secure token removal, force-stop without clearing app data, session restore, and logout after restore. The retained pending-player fixture was used for restricted-login denial; its status was not changed. No PIN, JWT, or credential is stored in tracked evidence.

The 03E Chromium check viewed reward settings/history/grants and reloaded the unchanged state. No positive policy or financial posting was submitted. The SELECT-only reconciliation confirms players #178–#181, all seven disabled/expired/zero signup policies, zero signup grants, 19 wallet transactions, Agent #3's ETB 51.00 float and two journals, and all 19 deposit claims/five deposit journals remain present. Reconciliation files: [SQL](evidence/module-03e-database-readonly.sql) and [output](evidence/module-03e-database-readonly.txt). Current account/test details: [Module 03E report](modules/module-03e-final-player-account-regression.md), [Android results](evidence/module-03e-android-results.txt), and [master coverage](modules/module-03e-master-coverage.md).

## Module 04A player game lobby and room discovery

No persistent player, room, or session fixture was created or edited. Android read-only browsing reused active player #181 (`QA Module 03E Regression Player 001`), active synthetic room #6 (`QA M02E Session Room 1792770588090-443bf1`), and its existing DRAFT session #5 (`QA M02E DRAFT 1792770588090-443bf1`). The session has ETB 3.25 price, capacity 10, and zero tickets/draws. All records remain present. The SELECT-only before/after reconciliation shows 33 rooms, 18 sessions, zero tickets/draws/claims/entitlements, 19 wallet transactions; players #178–181 wallet balances and Agent #3 float/journals are unchanged. Authentication telemetry may change during login; no game or financial record was written. See [Module 04A report](modules/module-04a-game-lobby.md), [Android results](evidence/module-04a-android-results.txt), and [database after snapshot](evidence/module-04a-database-after.txt). No credentials or tokens are recorded.

## Module 04B player ticket purchase QA

No persistent player, room, session, ticket, reservation, wallet, or ledger fixture was created or changed. Backend financial and lifecycle tests ran only in the guarded local MySQL `bingo_qa_test` database; existing suite fixtures funded test wallets through the isolated synthetic approval workflow and were cleaned by the test lifecycle. No signup reward, agent float, or persistent QA balance was used. A read-only transaction against `bingo_qa` on 2026-10-25 reconciled 33 rooms, 18 sessions, zero tickets, draws, claims, or entitlements, 19 wallet transactions, and Agent #3 at ETB 51.00 available / ETB 0.00 clearing with two float transactions. All 33 room rows, 18 session rows, and four preserved player-wallet rows match the Module 04A after snapshot. The real Android journey logged in with an existing synthetic QA player and read the lobby/session data; ordinary authentication telemetry may be retained, but no game or financial record changed. Evidence: [Module 04B report](modules/module-04b-ticket-selection.md), [test matrix](modules/module-04b-test-matrix.md), [Android screenshot](evidence/module-04b-android-session-details.png), [read-only SQL](evidence/module-04b-database-readonly.sql), and [read-only output](evidence/module-04b-database-readonly.txt). No credentials, PINs, or tokens were captured.
