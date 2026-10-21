# bingo-app

Flutter player app for the bingo backend.

Requires Flutter 3.41.0 or newer and Dart 3.11.0 or newer. CI also checks
dependency resolution, analysis, and tests with Flutter 3.41.0 and 3.47.7.

Run it with:

```bash
flutter pub get
flutter gen-l10n
flutter run --dart-define=API_BASE_URL=https://your-backend-host
```

`API_BASE_URL` is required; there is no localhost fallback. For Android
emulators, explicitly pass `--dart-define=API_BASE_URL=http://10.0.2.2:8080`.
For desktop or iOS simulators, use `http://localhost:8080`. Local HTTP is
accepted only in debug builds. Profile and release builds require HTTPS and
reject localhost and other loopback addresses.

Live-game connections use the same host and port as the REST API. The client connects to `/ws` with STOMP, sends the HTTP JWT as a bearer token in the `CONNECT` frame, and subscribes to `/topic/session/{sessionId}/draws` for live draw events. If the backend is behind TLS, use an `https://` API base URL so the app derives `wss://` automatically.

Useful commands:

```bash
flutter test test/features/game
flutter analyze
```

## Local E2E credentials

The integration E2E tests require `BINGO_E2E_PRIMARY_PHONE` and `BINGO_E2E_PRIMARY_PASSWORD`; there are no committed account or PIN fallbacks. The setup test generates a disposable secondary PIN at runtime and does not save it. Passwords are removed from any legacy E2E manifest when it is loaded, and new manifests never contain passwords or PINs.

For local runs, copy `e2e-secrets.example.json` to the ignored `e2e-secrets.json`, set `API_BASE_URL` explicitly (for example, `http://10.0.2.2:8080` for an Android emulator), and replace the account placeholders with a disposable test account. Invoke Flutter with `--dart-define-from-file=e2e-secrets.json`. For CI, create that file from secret-store values without echoing it or enabling shell tracing, then delete it after the test.
