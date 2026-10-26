# Module 06A — Functional Test Matrix

Statuses describe retained evidence and this module's execution attempt. `PASS (prior)` is only the scoped historical evidence named in the test reference; it is not a new Module 06A execution. Actual behavior is source review unless otherwise stated. Remaining E2E dependencies apply even where an isolated test passed.

| Req | Domain | Expected behavior (inventory scope) | Actual behavior observed | Test reference | Status | Defect ID / resolution | Remaining E2E dependency |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | D01 Registration | Validate and register supported player inputs; reject invalid/duplicate identity | Registration API/UI exists; pending/restricted state applies | 03A registration tests/report | PASS (prior) | — | Fresh approved isolated account journey |
| F02 | D01 Authentication | Login, authenticated access and local logout | Login/session restore implemented; logout is client-side and does not revoke copied JWT | 03B/03E reports | PASS (prior), logout limitation | — | Server token revocation policy/verification |
| F03 | D01 Auth / D08 distinction | Apply defined PIN validation/recovery | Player auth is password based; player PIN recovery is not present; agent PIN is separate | Source review; auth routes | NOT RUN | — | Product/security requirement if player PIN is desired |
| F04 | D02 Profile | View and manage supported profile fields | Self profile view/refresh exists; edits absent | 03C report | PARTIAL | — | Product-defined editable profile flow |
| F05 | D03 Wallet | Display authenticated player's balance | Balance API/UI exists; persistent posting is guarded | 03D/03E evidence; WalletController | PASS (prior) | — | Read-only reconciled MySQL E2E for fresh evidence |
| F06 | D04 Signup rewards | Show eligibility/result under approved reward policy | Disabled/zero path and eligibility guard exist; positive persistent reward blocked | 03D report; SignupRewardPostingGuard | PASS (prior, scoped) | — | Product/Finance policy and isolated posting approval |
| F07 | D12 Lobby | Show discoverable rooms and navigate to sessions | Lobby UI and room APIs exist | 04A report/tests | PASS (prior) | — | Fresh backend/UI reconciliation |
| F08 | D12/D13 Sessions | Show available sessions and lifecycle | Room session listing exists; schedule semantics unresolved | 04A, 04B2 reports | PASS (prior, discovery only) | — | Product timezone/DST/overlap policy |
| F09 | D12 Lobby | Filter/select supported room/session | Selection/navigation exists; no additional filter policy inferred | 04A source/report | PASS (prior, scoped) | — | Device/API E2E for all filters (if defined) |
| F10 | D17 Card generation | Generate valid 75-ball card and render returned grid | Server generation and player display exist | `BingoGameRulesTest`; 04D1 | BLOCKED (fresh run) | — | Maven execution plus eligible owned-ticket E2E |
| F11 | D15 Ownership | Return only player's owned tickets; validate ownership server-side | `/my-tickets` and authenticated ownership paths exist | 04B/04D1 sources and reports | PARTIAL | — | Real MySQL principal/ownership E2E with approved fixture |
| F12 | D15 Purchase | Validate purchase and reject invalid/unauthorized/insufficient balance requests | Backend purchase exists; player purchase UI absent; purchase is financially stateful | 04B/04B1 reports | BLOCKED / UI MISSING | — | Product/Finance approval and isolated funded fixture |
| F13 | D14 Lifecycle | Enforce allowed state transitions and terminal behavior | Lifecycle states implemented; selected terminal/restart evidence retained | 04C3B report; `GameSessionStateTest` | PASS (prior), fresh test blocked | — | Fresh transition concurrency/reconciliation |
| F14 | D16 Draw | Select unique numbers server-side and emit events | Server-driven draws and broadcast exist; retry idempotency unresolved | 04C2 report; `BingoLiveDrawIntegrationTest` (DB suite) | PASS (prior, scoped); retry BLOCKED | — | Approved retry contract and isolated MySQL concurrency |
| F15 | D16/D37 Timeline | Display ordered draw history and recover missed events | Flutter timeline/reconnect behavior implemented and previously exercised | 04C2/04C3B reports and Flutter game tests | PASS (prior) | — | Fresh device/API verification |
| F16 | D17 Patterns | Evaluate configured supported patterns against called numbers | LINE/TWO_LINES/FULL_HOUSE evaluator exists | `BingoPatternEvaluatorTest`; 04D1 | BLOCKED (fresh run) | — | Maven execution and valid-ticket E2E |
| F17 | D18 Claims | Validate owner, session state, pattern and called sequence | Server validation exists; CLOSED policy unresolved | `WinClaimSessionSecurityTest`; 04D1/04D4 | BLOCKED (fresh run) | — | Approved ticket fixture and claim persistence E2E |
| F18 | D19 Duplicate claims | Prevent duplicate/replayed claim safely | Idempotency/unique constraints exist in source; claim-specific concurrency not proven | 04D1/04D5B reports | BLOCKED | — | Product retry contract and isolated MySQL concurrency |
| F19 | D14/D18 Cancelled | Reject/handle cancelled sessions consistently | Terminal behavior has prior UI evidence; ticket-bearing cancellation policy unresolved | 04C3B; 04D1 | PASS (prior, terminal display); BLOCKED (refund/claims) | — | Product cancellation and refund policy |
| F20 | D14/D18 Closed | Handle closed session consistently | Closed terminal display exists; claim eligibility not settled | 04C3B; 04D1 | PASS (prior, display); BLOCKED (claim policy) | — | Product claim eligibility decision |
| F21 | D32 Recovery | Restore auth/session/game state after restart | Existing player session/game recovery suites and retained emulator evidence | 03B/03C/03D/04B1/04C3B | PASS (prior) | — | Claim/purchase state restoration E2E |
| F22 | D32 Network | Surface/recover from offline and network errors | Auth/lobby/live reconnect tests and prior network recovery evidence exist | 03A/03B/04A reports; Flutter tests | PASS (prior, scoped) | — | Fresh offline/error widget execution blocked |
| F23 | D32/D38 Feedback | Show actionable validation/API errors | Shared error handling/localized messages exist; fresh widget verification unavailable | localization and error widget source/tests | NOT RUN | — | Run Flutter widget suite and API error contract tests |
| F24 | D32 Navigation | Keep navigation and screen state consistent | Auth gate, home shell and lobby/game navigation exist | 04A/03E reports; widget tests | PASS (prior, scoped) | — | Fresh widget suite and device journey |

## Execution commands

- Maven safe unit selection: blocked before test discovery because Maven local repository creation failed at `C:\.m2\repository`.
- `flutter test` and `flutter analyze`: interrupted after producing no output in this execution environment; results are NOT RUN, not pass.
- Static route search: passed for presence of expected player API client paths only.
- `git -c safe.directory=D:/GitHub/bingo-platform diff --check`: passed.
- MySQL-dependent tests, Flutter integration/device tests, and financial scenarios were not run.
