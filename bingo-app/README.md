# bingo-app

Flutter player app for the bingo backend.

Run it with:

```bash
flutter pub get
flutter gen-l10n
flutter run --dart-define=API_BASE_URL=https://your-backend-host
```

The app reads the backend base URL only from `--dart-define=API_BASE_URL=...` and will stop at startup if the value is missing.

Live-game connections use the same host and port as the REST API. The client connects to `/ws` with STOMP, sends the HTTP JWT as a bearer token in the `CONNECT` frame, and subscribes to `/topic/session/{sessionId}/draws` for live draw events. If the backend is behind TLS, use an `https://` API base URL so the app derives `wss://` automatically.

Useful commands:

```bash
flutter test test/features/game
flutter analyze
```

## Local E2E credentials

The integration E2E tests require `BINGO_E2E_PRIMARY_PHONE` and `BINGO_E2E_PRIMARY_PASSWORD`; there are no committed account or PIN fallbacks. The setup test generates a disposable secondary PIN at runtime and does not save it. Passwords are removed from any legacy E2E manifest when it is loaded, and new manifests never contain passwords or PINs.

For local runs, copy `e2e-secrets.example.json` to the ignored `e2e-secrets.json`, replace its placeholders with a disposable test account, and invoke Flutter with `--dart-define-from-file=e2e-secrets.json`. For CI, create that file from secret-store values without echoing it or enabling shell tracing, then delete it after the test.
