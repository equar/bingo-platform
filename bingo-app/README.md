# Bingo Player

Flutter player app for the Bingo backend. Requires Flutter 3.41.0+ and Dart
3.11.0+.

## Local development

From this directory:

```bash
flutter pub get
flutter gen-l10n
flutter run -d windows --dart-define=API_BASE_URL=http://localhost:8080
```

Android emulators use `http://10.0.2.2:8080`; replace the URL with the
deployment's HTTPS origin for deployed backends. Always provide
`API_BASE_URL`: local HTTP is debug-only, and profile/release builds require
HTTPS and reject loopback addresses.

Useful checks:

```bash
flutter test
flutter analyze
```

## Game and community behavior

Live draws use STOMP at `/ws` with the API JWT in the `CONNECT` frame and
subscribe to `/topic/session/{sessionId}/draws`. The backend is authoritative
for cards, called numbers, ownership, claims, and persisted session state.
See the [backend API/WebSocket guide](../bingo-backend/README.md) and
[community-feature rules](../docs/community-features.md).

## Functional E2E

Use the [guarded real E2E runbook](../docs/qa/platform-e2e-runbook.md) and
[current scenario matrix](../docs/qa/platform-e2e-scenario-matrix.md). The
legacy `integration_test/local_e2e_test.dart` and
`scripts/run-local-e2e.ps1` do not enforce current database isolation; do not
run them against development, persistent QA, shared, or production databases
or report a test-harness run as device E2E.

Signed package and store release instructions are in the
[mobile release guide](../deployment/mobile/README.md).
