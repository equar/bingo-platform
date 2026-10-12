# Unit and Integration Test Report

## Scope

This report captures the current test coverage work for the Bingo platform across backend, admin, player, and agent codebases.

## Coverage Matrix

| Area                      | Focus                                                                                        | Status      | Notes                                                                                                                        |
| ------------------------- | -------------------------------------------------------------------------------------------- | ----------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Backend unit tests        | Phone normalization, money values, game session transitions, bingo rules, support flow rules | Implemented | Added focused coverage for critical business logic and validation paths.                                                     |
| Backend integration tests | Support ticket workflow over Spring Boot + MySQL                                             | Implemented | Added a support-flow integration test covering ticket creation, internal-note visibility, and resolution-summary validation. |
| Admin UI tests            | Session helpers, protected routes, idempotency hook, status state rendering                  | Implemented | Vitest/jsdom harness added and validated.                                                                                    |
| Player app tests          | Live game timeline, claim retry idempotency, reconnect/replay flow                           | Implemented | Flutter tests added for the draw timeline and controller behavior.                                                           |
| Agent app tests           | Idempotency key helper                                                                       | Implemented | Flutter test added and validated.                                                                                            |

## Executed Test Runs

| Command                                                                                                                                                                                                            | Result |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------ |
| `mvn -Dtest=SupportIntegrationTest test` in `bingo-backend`                                                                                                                                                        | Passed |
| `npm test -- --run src/api/session.test.ts` in `bingo-admin`                                                                                                                                                       | Passed |
| `C:\src\flutter\bin\flutter.bat test test/features/game/live_game_timeline_test.dart test/features/game/live_game_controller_test.dart test/features/game/live_game_controller_reconnect_test.dart` in `bingo-app` | Passed |
| `C:\src\flutter\bin\flutter.bat test test/core/network/idempotency_test.dart` in `bingo-agent`                                                                                                                     | Passed |

## Notes

- The backend integration coverage now exercises the support workflow against the real MySQL-backed Spring test profile.
- The admin test harness requires `jsdom` for Vitest and is now configured and passing.
- The Flutter controller tests needed a future-dated auth session seed so the controller would not short-circuit on expiry during tests.
- Additional backend integration coverage is still a good next step for auth, wallet, claims, and websocket concurrency flows.
