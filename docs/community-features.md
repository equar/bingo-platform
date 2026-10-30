# Player community features

The player app home opens Private Games, Daily Challenges and Leaderboard. The admin Community page shows aggregate activity and recent private rooms with reason-required entry moderation.

## Private games

An active player creates an invite-only game from an active public room template. Ticket price, patterns, prizes and ticket limits come from the existing room rules and are snapshotted by the existing session service. Hosts cannot edit these rules, manually draw numbers or approve wins.

Each host can have five unfinished rooms. Membership is capped at the smaller of the template capacity and 50. Scheduled starts must be within seven days. Invite codes contain 14 cryptographically random characters, expire after 24 hours, and are stored only as SHA-256 hashes. Generating a new invite invalidates the previous code. Invite attempts are throttled per account. Invite codes are returned only by the invite generation endpoint, never by room listings or idempotency response storage.

Private rooms are excluded from public discovery. Membership is enforced on room/session reads, tickets, draw history, ticket issuance and WebSocket subscriptions. Agents use the existing sale flow with the private session ID and the member's player verification; production purchase restrictions remain in place. At least two distinct ticketed members are required for the host to start. Existing server draw scheduling controls play. Hosts can cancel only an open game with no tickets; funded cancellation remains an administrative support operation.

Admin disabling closes new entry and ticket sales without reversing payouts or cancelling a funded game. Existing members retain access to their session. Private room display names may repeat; public room names remain unique.

## Daily challenges

Version 1 has three fixed rules:

| Challenge | Requirement | Points |
| --- | --- | --- |
| Finish a game | Participate in one completed public session | 20 |
| Explore rooms | Participate in completed sessions in two distinct public rooms | 30 |
| Verified Bingo | Receive an approved, validated win in a completed public session | 50 |

Only public sessions started and completed within the current business day qualify. Cancelled and private sessions do not qualify. Extra tickets never multiply progress. Progress is computed on the server from authoritative records. Claims are explicit, idempotent and transactionally unique per player/date/challenge. Points have no monetary value and do not write to wallets. Maximum earnings are 100 points per day. Claims must occur before the business day ends; a previously successful claim can still be replayed safely.

The business timezone defaults to `Africa/Addis_Ababa` through `app.business-timezone`. The API returns the business date, timezone and next reset instant. Rule changes should add a new version rather than reinterpret historical claims.

## Leaderboards

Daily, Monday-based weekly and all-time rankings use claimed challenge points. Period boundaries filter permanent claim records rather than deleting scores. Players opt in using a unique public alias and can opt out without losing points. Responses contain aliases, points and ranks; they do not expose account IDs, phone numbers or names. Disabled accounts are excluded. Equal scores share competition rank; stable alias ordering supports pagination. Page size is capped at 50. No client endpoint accepts arbitrary scores.

## Release and verification

Deploy the backend first, allowing Flyway migration `V23__private_games_and_player_progress.sql`, then the admin and updated player app. Back up the production database through the normal deployment process. No production migration or deployment is performed by local feature tests. Existing agent packages support private ticket sales through their manual session-ID flow.

Backend tests cover private access, invite rotation/expiry/capacity, ticketing safeguards, duplicate/concurrent claims, exclusion of private/cancelled sessions, opt-in ranking privacy and alias conflicts. WebSocket tests cover authenticated membership and rejection of client-sent broker messages. Tests use an isolated disposable MySQL database. Verification on 2026-10-29: backend Maven `verify` passed 189 tests across 37 classes; admin production build, lint and all 85 tests passed; player Flutter analysis passed; six home/community navigation widget tests passed in an isolated copy with native secure storage stubbed out. The signed player Android APK was rebuilt and its signature, package ID and production API URL verified. Full native Flutter widget tests remain blocked by the workstation’s unaccepted Xcode licence; no native-storage or iOS signing validation is claimed.

Design references: [Unity leaderboard features](https://docs.unity.com/en-us/leaderboards/features) and [period reset concepts](https://docs.unity.com/en-us/leaderboards/concepts/resets) inform period scoring; [UKGC RTS 7](https://www.gamblingcommission.gov.uk/standards/remote-gambling-and-software-technical-standards/rts-7-generation-of-random-outcomes) informs fixed rules during play. These references are design guidance, not certification or a statement of local regulatory compliance.
