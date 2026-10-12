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
