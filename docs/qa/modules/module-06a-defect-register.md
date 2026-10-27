# Module 06A — Defect Register

Module 06A-5 confirmed and fixed one player navigation defect by source inspection. Runtime regression tests were added but could not be executed in the Codex sandbox; no runtime PASS is claimed.

| ID | Requirement | Reproducible failing test | Root cause | Fix | Result |
| --- | --- | --- | --- | --- | --- |
| DEF-06A5-01 | OPEN player session could not reach purchase detail | `lobby_screen_test.dart` expected no live-detail route for OPEN, while purchase UI existed only in live detail. Lobby exposed that route only for STARTED/DRAWING; ticket preview's purchase control is deliberately disabled. | OPEN sessions are now routable to live detail. Purchase confirmation is behind `BINGO_PLAYER_PURCHASES_ENABLED`, default false, so this change does not enable purchases. Purchase API errors now retain server codes/messages for insufficient balance, expired/closed session, and network failures. | Regression coverage added; Flutter execution NOT RUN because SDK invocation stalled without output in this sandbox. Backend continues to enforce its isolated Maven profile gate. |

## Product and implementation gaps (not classified as runtime defects)

| Ref | Requirement | Finding | Disposition |
| --- | --- | --- | --- |
| GAP-01 | F04 profile management | Player profile view exists; editable profile flow not implemented (D02). | Product scope/acceptance criteria needed before implementation. |
| GAP-02 | F03 PIN validation/recovery | Player contract uses password authentication; PIN is an agent feature. Player PIN recovery cannot be asserted from this inventory. | No change absent a player PIN requirement. |
| GAP-03 | F12 ticket purchase | Lobby/session list and ticket preview expose session status, availability and price; owned tickets and cards render in live detail; server purchase contract is idempotent. Entry to OPEN live detail was missing and the preview purchase CTA remains disabled. Cards are generated randomly server-side; selectable card inventory/reservation is not supported by the current contract. | Entry and confirmation flow are now present. `BINGO_PLAYER_PURCHASES_ENABLED` defaults false. Real purchase remains blocked until existing financial controls and purchase/cutoff policy permit the isolated runtime. |
| GAP-04 | F17/F18 claims | Claim rules exist, but persistence/replay/concurrency lack real MySQL/E2E evidence; CLOSED eligibility unresolved (D18/D19). | Retain 04D blockers; no fixture writes in this module. |

## Execution blockers

Maven could not create `C:\.m2\repository`; Flutter test/analyzer invocations produced no output before interruption. These limit this module's fresh verification and are not application defects. `git diff --check` passed using a per-invocation safe-directory setting.
